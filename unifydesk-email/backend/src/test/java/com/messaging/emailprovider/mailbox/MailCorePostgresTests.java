package com.messaging.emailprovider.mailbox;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.emailprovider.common.exception.*;
import com.messaging.emailprovider.mailbox.compose.MailComposeService;
import com.messaging.emailprovider.mailbox.inbound.*;
import com.messaging.emailprovider.product.console.ConsoleAuthService.Principal;
import com.messaging.emailprovider.product.console.ConsoleRole;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(value = OS.LINUX, disabledReason = "Exercises real durable Linux spool and PostgreSQL")
class MailCorePostgresTests {
  @TempDir Path objects;
  private static final Principal OWNER =
      new Principal(11L, 1L, ConsoleRole.OWNER, "owner@example.test", 101L);
  private static final Principal MEMBER =
      new Principal(12L, 1L, ConsoleRole.VIEWER, "member@example.test", 102L);
  private static final Principal ADMIN =
      new Principal(13L, 1L, ConsoleRole.ADMIN, "admin@example.test", 103L);
  private static final Principal OTHER =
      new Principal(21L, 2L, ConsoleRole.OWNER, "other@example.test", 201L);
  private static final byte[] MIME =
      MailAttachmentReaderTests.MIME.getBytes(StandardCharsets.UTF_8);

  @Test
  void concurrentReplaysReserveStorageExactlyOnceAndMismatchedReplayFails() throws Exception {
    try (var db = new MailPostgresFixture()) {
      db.seed();
      var intake =
          new InboundIntakeService(
              db.jdbc, db.tx, new MailSpoolStore(objects.toString(), 10485760), true);
      try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
        List<Callable<InboundIntakeService.Intake>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++)
          calls.add(
              () ->
                  intake.accept("owner@example.test", "same-key", new ByteArrayInputStream(MIME)));
        var results = pool.invokeAll(calls);
        var id = results.getFirst().get().id();
        for (var result : results) assertEquals(id, result.get().id());
      }
      assertEquals(
          1L, db.jdbc.queryForObject("SELECT count(*) FROM email_mail_inbound_jobs", Long.class));
      assertEquals(
          (long) MIME.length,
          db.jdbc.queryForObject(
              "SELECT storage_used_bytes FROM email_mailboxes WHERE id=41", Long.class));
      assertThrows(
          ConflictException.class,
          () ->
              intake.accept(
                  "owner@example.test",
                  "same-key",
                  new ByteArrayInputStream("different".getBytes(StandardCharsets.UTF_8))));
      try (var files = Files.list(objects)) {
        assertEquals(1, files.count());
      }
      db.jdbc.update(
          "UPDATE email_mailboxes SET storage_quota_bytes=storage_used_bytes WHERE id=41");
      assertThrows(
          ConflictException.class,
          () -> intake.accept("owner@example.test", "over-quota", new ByteArrayInputStream(MIME)));
      try (var files = Files.list(objects)) {
        assertEquals(1, files.count());
      }
    }
  }

  @Test
  void promotionRequiresScansAndCurrentAdmissionAndContentRequiresExplicitLiveGrant()
      throws Exception {
    try (var db = new MailPostgresFixture()) {
      db.seed();
      var spool = new MailSpoolStore(objects.toString(), 10485760);
      var intake = new InboundIntakeService(db.jdbc, db.tx, spool, true);
      var malware = mock(ClamdMalwareScanner.class);
      var spam = mock(RspamdSpamScanner.class);
      when(malware.scan(any(), anyLong(), anyString()))
          .thenReturn(ClamdMalwareScanner.Verdict.CLEAN);
      when(spam.scan(any(), anyLong(), anyString())).thenReturn(RspamdSpamScanner.Decision.INBOX);
      var worker = new InboundScanWorker(db.jdbc, db.tx, spool, malware, spam);
      var read =
          new MailboxReadService(
              db.jdbc,
              spool,
              new MailPlainTextExtractor(),
              new MailAttachmentReader(),
              new MailAuditService(db.jdbc),
              true,
              true);
      intake.accept("owner@example.test", "clean", new ByteArrayInputStream(MIME));
      assertTrue(read.list(OWNER, 41, "INBOX", null, 30).isEmpty());
      worker.poll();
      var messages = read.list(OWNER, 41, "INBOX", null, 30);
      assertEquals(1, messages.size());
      long message = Long.parseLong(messages.getFirst().id());
      assertTrue(read.preview(OWNER, 41, message).plainText().contains("Message body"));
      assertEquals(2, read.attachments(OWNER, 41, message).size());
      assertThrows(NotFoundException.class, () -> read.preview(ADMIN, 41, message));
      assertThrows(NotFoundException.class, () -> read.preview(OTHER, 41, message));
      var raw = read.raw(OWNER, 41, message);
      try (var stream = raw.body()) {
        assertTrue(stream.read(new byte[8]) > 0);
        db.jdbc.update("UPDATE email_console_sessions SET revoked_at=now() WHERE id=101");
        assertThrows(NotFoundException.class, () -> stream.read(new byte[8]));
      }
      db.jdbc.update("UPDATE email_console_sessions SET revoked_at=NULL WHERE id=101");
      String key =
          db.jdbc.queryForObject(
              "SELECT mime_object_key FROM email_mail_inbound_jobs WHERE delivery_key='clean'",
              String.class);
      Files.writeString(objects.resolve(key), "tampered");
      assertThrows(ServiceUnavailableException.class, () -> read.preview(OWNER, 41, message));
      intake.accept("owner@example.test", "disabled-after-intake", new ByteArrayInputStream(MIME));
      db.jdbc.update("UPDATE email_mail_domains SET inbound_enabled=FALSE WHERE id=31");
      worker.poll();
      assertEquals(
          "QUARANTINED",
          db.jdbc.queryForObject(
              "SELECT status FROM email_mail_inbound_jobs WHERE delivery_key='disabled-after-intake'",
              String.class));
      assertThrows(
          NotFoundException.class,
          () -> intake.accept("owner@example.test", "disabled", new ByteArrayInputStream(MIME)));
    }
  }

  @Test
  void delegatePermissionsAreIndependentAuditedAndCannotBeBypassedByAdminActivation()
      throws Exception {
    try (var db = new MailPostgresFixture()) {
      db.seed();
      var audit = new MailAuditService(db.jdbc);
      var access = new MailboxAccessService(db.jdbc, audit);
      var read =
          new MailboxReadService(
              db.jdbc,
              new MailSpoolStore(objects.toString(), 10485760),
              new MailPlainTextExtractor(),
              new MailAttachmentReader(),
              audit,
              true,
              true);
      assertThrows(NotFoundException.class, () -> read.activate(ADMIN, 41, 13));
      assertThrows(NotFoundException.class, () -> access.grant(ADMIN, 41, 13, true, true, true));
      assertThrows(NotFoundException.class, () -> access.grant(OWNER, 41, 21, true, true, false));
      db.tx.executeWithoutResult(ignored -> access.grant(OWNER, 41, 12, true, false, false));
      read.requireGrant(MEMBER, 41);
      var compose = new MailComposeService(db.jdbc, false);
      assertThrows(NotFoundException.class, () -> compose.list(MEMBER, 41));
      assertThrows(NotFoundException.class, () -> access.grant(MEMBER, 41, 13, true, true, false));
      db.tx.executeWithoutResult(ignored -> access.revoke(OWNER, 41, 12));
      assertThrows(NotFoundException.class, () -> read.requireGrant(MEMBER, 41));
      assertEquals(
          2L,
          db.jdbc.queryForObject(
              "SELECT count(*) FROM email_mail_audit WHERE action IN ('GRANT','REVOKE')",
              Long.class));
      assertThrows(
          org.springframework.dao.DataIntegrityViolationException.class,
          () ->
              db.jdbc.update(
                  "INSERT INTO email_mailbox_access(organization_id,mailbox_id,user_id) VALUES(1,41,21)"));
    }
  }

  @Test
  void scannerOutageAndMalwareNeverBecomeReadable() throws Exception {
    try (var db = new MailPostgresFixture()) {
      db.seed();
      var spool = new MailSpoolStore(objects.toString(), 10485760);
      var intake = new InboundIntakeService(db.jdbc, db.tx, spool, true);
      var malware = mock(ClamdMalwareScanner.class);
      var spam = mock(RspamdSpamScanner.class);
      var worker = new InboundScanWorker(db.jdbc, db.tx, spool, malware, spam);
      when(malware.scan(any(), anyLong(), anyString()))
          .thenThrow(new ServiceUnavailableException("offline"));
      intake.accept("owner@example.test", "outage", new ByteArrayInputStream(MIME));
      worker.poll();
      assertEquals(
          "PENDING_SCAN",
          db.jdbc.queryForObject("SELECT status FROM email_mail_inbound_jobs", String.class));
      assertEquals(
          0L, db.jdbc.queryForObject("SELECT count(*) FROM email_mailbox_messages", Long.class));
      db.jdbc.update("UPDATE email_mail_inbound_jobs SET scan_attempts=4,scan_retry_after=NULL");
      worker.poll();
      assertEquals(
          "QUARANTINED",
          db.jdbc.queryForObject("SELECT status FROM email_mail_inbound_jobs", String.class));
      reset(malware);
      when(malware.scan(any(), anyLong(), anyString()))
          .thenReturn(ClamdMalwareScanner.Verdict.INFECTED);
      intake.accept("owner@example.test", "infected", new ByteArrayInputStream(MIME));
      worker.poll();
      assertEquals(
          2L,
          db.jdbc.queryForObject(
              "SELECT count(*) FROM email_mail_inbound_jobs WHERE status='QUARANTINED'",
              Long.class));
      verifyNoInteractions(spam);
    }
  }

  @Test
  void retentionHonorsBackupsAndLegalHoldsAndReleasesQuotaOnceAfterCrash() throws Exception {
    try (var db = new MailPostgresFixture()) {
      db.seed();
      var spool = new MailSpoolStore(objects.toString(), 10485760);
      var intake = new InboundIntakeService(db.jdbc, db.tx, spool, true);
      var malware = mock(ClamdMalwareScanner.class);
      var spam = mock(RspamdSpamScanner.class);
      when(malware.scan(any(), anyLong(), anyString()))
          .thenReturn(ClamdMalwareScanner.Verdict.CLEAN);
      when(spam.scan(any(), anyLong(), anyString())).thenReturn(RspamdSpamScanner.Decision.INBOX);
      intake.accept("owner@example.test", "retention", new ByteArrayInputStream(MIME));
      new InboundScanWorker(db.jdbc, db.tx, spool, malware, spam).poll();
      long message = db.jdbc.queryForObject("SELECT id FROM email_mailbox_messages", Long.class);
      var audit = new MailAuditService(db.jdbc);
      var read =
          new MailboxReadService(
              db.jdbc,
              spool,
              new MailPlainTextExtractor(),
              new MailAttachmentReader(),
              audit,
              true,
              true);
      var lifecycle = new MailboxLifecycleService(db.jdbc, read, audit);
      var retention = new MailRetentionWorker(db.jdbc, db.tx, spool);
      db.tx.executeWithoutResult(ignored -> lifecycle.move(OWNER, 41, message, "TRASH"));
      assertEquals(1, read.list(OWNER, 41, "TRASH", null, 30).size());
      assertThrows(NotFoundException.class, () -> read.preview(OWNER, 41, message));
      db.tx.executeWithoutResult(ignored -> lifecycle.restore(OWNER, 41, message));
      assertEquals(1, read.list(OWNER, 41, "INBOX", null, 30).size());
      db.tx.executeWithoutResult(ignored -> lifecycle.move(OWNER, 41, message, "TRASH"));
      db.jdbc.update("UPDATE email_mailbox_messages SET trashed_at=now()-interval '8 days'");
      assertFalse(retention.markOne());
      db.jdbc.update("UPDATE email_mailboxes SET trash_retention_days=7 WHERE id=41");
      assertFalse(retention.markOne());
      db.jdbc.update(
          "UPDATE email_mailboxes SET backup_verified_at=now(),backup_reference='synthetic-restore-checkpoint',legal_hold=TRUE WHERE id=41");
      assertFalse(retention.markOne());
      db.jdbc.update("UPDATE email_mailboxes SET legal_hold=FALSE WHERE id=41");
      assertTrue(retention.markOne());
      assertThrows(
          NotFoundException.class,
          () -> db.tx.executeWithoutResult(ignored -> lifecycle.restore(OWNER, 41, message)));
      db.jdbc.update("UPDATE email_mailboxes SET legal_hold=TRUE WHERE id=41");
      assertFalse(retention.purgeOne());
      db.jdbc.update("UPDATE email_mailboxes SET legal_hold=FALSE WHERE id=41");
      String key =
          db.jdbc.queryForObject(
              "SELECT mime_object_key FROM email_mailbox_messages", String.class);
      // Simulate death after unlink but before phase-two DB commit.
      spool.purge(new MailSpoolStore.Stored(key, MIME.length, ""));
      assertTrue(retention.purgeOne());
      assertFalse(retention.purgeOne());
      assertEquals(
          0L,
          db.jdbc.queryForObject(
              "SELECT storage_used_bytes FROM email_mailboxes WHERE id=41", Long.class));
      assertEquals(
          2L,
          db.jdbc.queryForObject("SELECT count(*) FROM email_mail_retention_audit", Long.class));
      assertTrue(read.list(OWNER, 41, "TRASH", null, 30).isEmpty());
    }
  }

  @Test
  void concurrentSentArchivePreparationReservesQuotaExactlyOnce() throws Exception {
    try (var db = new MailPostgresFixture()) {
      db.seed();
      db.jdbc.update(
          "INSERT INTO email_mail_drafts(id,organization_id,mailbox_id,author_user_id) VALUES(51,1,41,11),(52,1,41,11)");
      db.jdbc.update(
          "INSERT INTO email_mail_outbound_intents(id,organization_id,mailbox_id,draft_id) VALUES(61,1,41,51),(62,1,41,52)");
      var archive =
          new com.messaging.emailprovider.mailbox.outbound.MailSentArchiveStore(
              db.jdbc, new MailSpoolStore(objects.toString(), 10485760), db.tx);
      try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
        List<Callable<byte[]>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) calls.add(() -> archive.prepare(1, 41, 61, MIME));
        for (var result : pool.invokeAll(calls)) assertArrayEquals(MIME, result.get());
      }
      assertEquals(
          (long) MIME.length,
          db.jdbc.queryForObject(
              "SELECT storage_used_bytes FROM email_mailboxes WHERE id=41", Long.class));
      assertEquals(
          1L,
          db.jdbc.queryForObject("SELECT count(*) FROM email_mail_outbound_archives", Long.class));
      db.jdbc.update(
          "UPDATE email_mailboxes SET storage_quota_bytes=storage_used_bytes WHERE id=41");
      assertThrows(ConflictException.class, () -> archive.prepare(1, 41, 62, MIME));
      try (var files = Files.list(objects)) {
        assertEquals(1, files.count());
      }
    }
  }
}
