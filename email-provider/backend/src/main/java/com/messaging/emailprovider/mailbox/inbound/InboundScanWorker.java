package com.messaging.emailprovider.mailbox.inbound;

import com.messaging.emailprovider.common.id.IdGenerator;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Single-record claims with lease tokens. Scan outside transactions; never
 * publish raw MIME without both malware and spam checks succeeding.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail.scanner", name = "enabled", havingValue = "true")
public class InboundScanWorker {
  private static final int MAX_ATTEMPTS = 5;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final MailSpoolStore spool;
  private final ClamdMalwareScanner malware;
  private final RspamdSpamScanner spam;

  record Job(long id, long organizationId, long mailboxId, String objectKey,
             long bytes, String digest, Instant receivedAt, UUID lease, int attempts) {}

  public InboundScanWorker(JdbcTemplate jdbc, TransactionTemplate transactions,
      MailSpoolStore spool, ClamdMalwareScanner malware, RspamdSpamScanner spam) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.spool = spool;
    this.malware = malware;
    this.spam = spam;
  }

  @Scheduled(fixedDelayString = "${app.mail.scanner.interval-ms:5000}")
  public void poll() {
    // A worker may die on its last lease; quarantine rather than retry forever.
    jdbc.update("""
        UPDATE email_mail_inbound_jobs
        SET status='QUARANTINED',claim_token=NULL,claimed_at=NULL,
            scan_error='SCAN_LEASE_EXHAUSTED',updated_at=now()
        WHERE status='SCANNING' AND scan_attempts >= 6
          AND claimed_at < now() - INTERVAL '120 seconds'
        """);
    // Bounded batch limits CPU and contention on a single low-cost mail node.
    for (int i = 0; i < 4; i++) {
      Job claimed = claim();
      if (claimed == null) return;
      process(claimed);
    }
  }

  private Job claim() {
    UUID lease = UUID.randomUUID();
    List<Job> jobs = jdbc.query("""
        WITH picked AS (
          SELECT id FROM email_mail_inbound_jobs
          WHERE scan_attempts < 6 AND
            ((status='PENDING_SCAN'
              AND (scan_retry_after IS NULL OR scan_retry_after<=now()))
             OR (status='SCANNING'
              AND claimed_at < now() - INTERVAL '120 seconds'))
          ORDER BY received_at,id
          FOR UPDATE SKIP LOCKED LIMIT 1
        )
        UPDATE email_mail_inbound_jobs j
        SET status='SCANNING',claim_token=?,claimed_at=now(),
            scan_attempts=scan_attempts+1,updated_at=now()
        WHERE j.id IN (SELECT id FROM picked)
        RETURNING j.id,j.organization_id,j.mailbox_id,j.mime_object_key,
          j.mime_size_bytes,j.sha256,j.received_at,j.claim_token,j.scan_attempts
        """, (rs, row) -> new Job(rs.getLong(1),rs.getLong(2),rs.getLong(3),
        rs.getString(4),rs.getLong(5),rs.getString(6),
        rs.getTimestamp(7).toInstant(),(UUID) rs.getObject(8),rs.getInt(9)), lease);
    return jobs.isEmpty() ? null : jobs.get(0);
  }

  private void process(Job job) {
    try {
      ClamdMalwareScanner.Verdict verdict;
      try (InputStream data = spool.open(job.objectKey())) {
        verdict = malware.scan(data, job.bytes(), job.digest());
      }
      if (verdict == ClamdMalwareScanner.Verdict.INFECTED) {
        finish(job, "QUARANTINED", null, null);
        return;
      }
      RspamdSpamScanner.Decision disposition;
      try (InputStream data = spool.open(job.objectKey())) {
        disposition = spam.scan(data, job.bytes(), job.digest());
      }
      if (disposition == RspamdSpamScanner.Decision.QUARANTINE) {
        finish(job, "QUARANTINED", null, null);
      } else {
        MailHeaderSummary.Summary summary;
        try (InputStream data = spool.open(job.objectKey())) {
          summary = MailHeaderSummary.read(data);
        }
        finish(job, "READY",
            disposition == RspamdSpamScanner.Decision.SPAM ? "SPAM" : "INBOX", summary);
      }
    } catch (Exception unavailable) {
      fail(job);
    }
  }

  private void finish(Job job, String status, String folder, MailHeaderSummary.Summary summary) {
    transactions.executeWithoutResult(ignored -> {
      // Recheck tenant/domain/mailbox state under the same transaction.
      Boolean active = jdbc.queryForObject("""
          SELECT EXISTS (
            SELECT 1 FROM email_mailboxes m
            JOIN email_mail_domains d ON d.id=m.domain_id
              AND d.organization_id=m.organization_id
            JOIN email_product_organizations o ON o.id=m.organization_id
            WHERE m.id=? AND m.organization_id=? AND m.status='ACTIVE'
              AND d.status='VERIFIED' AND d.inbound_enabled=TRUE
              AND o.status='ACTIVE')
          """, Boolean.class, job.mailboxId(), job.organizationId());
      String destination = Boolean.TRUE.equals(active) ? status : "QUARANTINED";
      int updated = jdbc.update("""
          UPDATE email_mail_inbound_jobs
          SET status=?,claim_token=NULL,claimed_at=NULL,updated_at=now(),
              scan_error=NULL,scan_retry_after=NULL
          WHERE id=? AND claim_token=? AND status='SCANNING'
          """, destination,job.id(),job.lease());
      if (updated != 1 || !"READY".equals(destination)) return;
      // Unique inbound_job_id makes recovery/replays exactly-once in the mailbox.
      jdbc.update("""
          INSERT INTO email_mailbox_messages (
            id,organization_id,mailbox_id,inbound_job_id,direction,folder,
            mime_object_key,mime_size_bytes,received_at,
            from_address,subject,message_id_header)
          VALUES (?,?,?,?,'INBOUND',?,?,?,?,?,?,?)
          ON CONFLICT (inbound_job_id) DO NOTHING
          """, IdGenerator.nextId(),job.organizationId(),job.mailboxId(),
          job.id(),folder,job.objectKey(),job.bytes(),job.receivedAt(),
          summary.from(),summary.subject(),summary.messageId());
    });
  }

  private void fail(Job job) {
    int attempts = job.attempts();
    int seconds = Math.min(3600, 30 * (1 << Math.min(attempts, 6)));
    jdbc.update("""
        UPDATE email_mail_inbound_jobs
        SET status=?,claim_token=NULL,claimed_at=NULL,updated_at=now(),
            scan_retry_after=CASE WHEN ? THEN NULL ELSE now() + (? * INTERVAL '1 second') END,
            scan_error='SECURITY_SCANNER_UNAVAILABLE'
        WHERE id=? AND claim_token=? AND status='SCANNING'
        """, attempts >= MAX_ATTEMPTS ? "QUARANTINED" : "PENDING_SCAN",
        attempts >= MAX_ATTEMPTS, seconds, job.id(), job.lease());
  }
}
