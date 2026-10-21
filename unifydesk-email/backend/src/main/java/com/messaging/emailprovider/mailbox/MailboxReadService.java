package com.messaging.emailprovider.mailbox;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.ForbiddenException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import com.messaging.emailprovider.mailbox.inbound.MailSpoolStore;
import com.messaging.emailprovider.product.console.ConsoleAuthService;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit per-user mailbox grants; even organization admins cannot implicitly read mail. */
@Service
@ConditionalOnProperty(prefix = "app.mail.inbound", name = "enabled", havingValue = "true")
public class MailboxReadService {
  private final JdbcTemplate jdbc;
  private final MailSpoolStore spool;
  private final MailPlainTextExtractor textExtractor;
  private final boolean activationEnabled;
  private final boolean scanningEnabled;

  public MailboxReadService(
      JdbcTemplate jdbc,
      MailSpoolStore spool,
      MailPlainTextExtractor textExtractor,
      @Value("${app.mail.activation.enabled:false}") boolean activationEnabled,
      @Value("${app.mail.scanner.enabled:false}") boolean scanningEnabled) {
    this.jdbc = jdbc;
    this.spool = spool;
    this.textExtractor = textExtractor;
    this.activationEnabled = activationEnabled;
    this.scanningEnabled = scanningEnabled;
  }

  public record MailboxView(String id, String status) {}

  public record Message(
      String id, String folder, String from, String subject, long sizeBytes, String receivedAt) {}

  public record RawMail(InputStream body, long sizeBytes) {}

  private record ObjectRecord(String key, long size, String digest) {}

  private record PreviewObject(String key, long size, String sha256) {}

  public record MessagePreview(
      String messageId, String plainText, boolean truncated, boolean textAvailable) {}

  /** Controlled activation binds one active console user to one verified mailbox. */
  @Transactional
  public MailboxView activate(
      ConsoleAuthService.Principal principal, long mailboxId, long ownerUserId) {
    if (!principal.role().canManage()) {
      throw new ForbiddenException("Admin permission required");
    }
    if (!activationEnabled || !scanningEnabled) {
      throw new ServiceUnavailableException(
          "Mailbox activation requires approved scanner controls");
    }
    List<Long> mailbox =
        jdbc.query(
            """
        SELECT m.id FROM email_mailboxes m
        JOIN email_mail_domains d ON d.id=m.domain_id AND d.organization_id=m.organization_id
        WHERE m.id=? AND m.organization_id=? AND d.status='VERIFIED'
          AND m.status IN ('DISABLED','ACTIVE')
        FOR UPDATE OF m
        """,
            (rs, row) -> rs.getLong(1),
            mailboxId,
            principal.organizationId());
    if (mailbox.isEmpty()) throw new NotFoundException("Verified mailbox not found");
    Boolean validOwner =
        jdbc.queryForObject(
            """
        SELECT EXISTS (SELECT 1 FROM email_console_users u
          WHERE u.id=? AND u.organization_id=? AND u.status='ACTIVE')
        """,
            Boolean.class,
            ownerUserId,
            principal.organizationId());
    if (!Boolean.TRUE.equals(validOwner))
      throw new NotFoundException("Active mailbox user not found");
    jdbc.update(
        """
        INSERT INTO email_mailbox_access(organization_id,mailbox_id,user_id)
        VALUES (?,?,?) ON CONFLICT(mailbox_id,user_id) DO NOTHING
        """,
        principal.organizationId(),
        mailboxId,
        ownerUserId);
    jdbc.update(
        """
        UPDATE email_mailboxes SET status='ACTIVE',updated_at=now()
        WHERE id=? AND organization_id=? AND status IN ('DISABLED','ACTIVE')
        """,
        mailboxId,
        principal.organizationId());
    return new MailboxView(Long.toString(mailboxId), "ACTIVE");
  }

  @Transactional(readOnly = true)
  public List<Message> list(
      ConsoleAuthService.Principal user, long mailboxId, String folder, Long beforeId, int limit) {
    requireGrant(user, mailboxId);
    String normalized = folder == null ? "INBOX" : folder;
    if (!("INBOX".equals(normalized) || "SPAM".equals(normalized) || "SENT".equals(normalized))
        || limit < 1
        || limit > 100
        || (beforeId != null && beforeId <= 0)) {
      throw new BadRequestException("Invalid folder, page size or cursor");
    }
    return jdbc.query(
        """
        SELECT id,folder,from_address,subject,mime_size_bytes,
          COALESCE(received_at,sent_at)
        FROM email_mailbox_messages
        WHERE organization_id=? AND mailbox_id=? AND folder=? AND deleted_at IS NULL
          AND id < ?
        ORDER BY id DESC LIMIT ?
        """,
        (rs, row) ->
            new Message(
                Long.toString(rs.getLong(1)),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getLong(5),
                rs.getTimestamp(6).toInstant().toString()),
        user.organizationId(),
        mailboxId,
        normalized,
        beforeId == null ? Long.MAX_VALUE : beforeId,
        limit);
  }

  /** Download-only untrusted MIME. NEVER inline it as HTML or execute attachments. */
  @Transactional(readOnly = true)
  public RawMail raw(ConsoleAuthService.Principal user, long mailboxId, long messageId) {
    requireGrant(user, mailboxId);
    List<ObjectRecord> messages =
        jdbc.query(
            """
        SELECT COALESCE(j.mime_object_key,a.mime_object_key),
               COALESCE(j.mime_size_bytes,a.mime_size_bytes),
               COALESCE(j.sha256,a.sha256)
        FROM email_mailbox_messages msg
        LEFT JOIN email_mail_inbound_jobs j ON j.id=msg.inbound_job_id
          AND j.organization_id=msg.organization_id AND j.mailbox_id=msg.mailbox_id
          AND j.status='READY'
        LEFT JOIN email_mail_outbound_archives a ON a.intent_id=msg.outbound_intent_id
          AND a.organization_id=msg.organization_id AND a.mailbox_id=msg.mailbox_id
        WHERE msg.id=? AND msg.mailbox_id=? AND msg.organization_id=?
          AND msg.deleted_at IS NULL
          AND ((msg.direction='INBOUND' AND msg.folder IN ('INBOX','SPAM')
                AND j.id IS NOT NULL)
             OR (msg.direction='OUTBOUND' AND msg.folder='SENT'
                AND a.intent_id IS NOT NULL))
        """,
            (rs, row) -> new ObjectRecord(rs.getString(1), rs.getLong(2), rs.getString(3)),
            messageId,
            mailboxId,
            user.organizationId());
    if (messages.isEmpty()) throw new NotFoundException("Mail message not found");
    ObjectRecord message = messages.get(0);
    verifyObjectIntegrity(new PreviewObject(message.key(), message.size(), message.digest()));
    return new RawMail(spool.open(message.key()), message.size());
  }

  /**
   * Never hold a database transaction while decoding MIME; the access query checks the exact
   * user+org+mailbox+message association first.
   */
  public MessagePreview preview(ConsoleAuthService.Principal user, long mailboxId, long messageId) {
    List<PreviewObject> matches =
        jdbc.query(
            """
        SELECT j.mime_object_key,j.mime_size_bytes,j.sha256
        FROM email_mailbox_messages msg
        JOIN email_mail_inbound_jobs j ON j.id=msg.inbound_job_id
          AND j.mailbox_id=msg.mailbox_id AND j.organization_id=msg.organization_id
        JOIN email_mailboxes m ON m.id=msg.mailbox_id
          AND m.organization_id=msg.organization_id
        JOIN email_mail_domains d ON d.id=m.domain_id
          AND d.organization_id=m.organization_id
        JOIN email_product_organizations o ON o.id=m.organization_id
        JOIN email_mailbox_access a ON a.mailbox_id=m.id
          AND a.organization_id=m.organization_id
        WHERE msg.id=? AND msg.mailbox_id=? AND msg.organization_id=?
          AND a.user_id=? AND m.status='ACTIVE'
          AND d.status='VERIFIED' AND o.status='ACTIVE'
          AND j.status='READY' AND msg.direction='INBOUND'
          AND msg.deleted_at IS NULL AND msg.folder IN ('INBOX','SPAM')
        """,
            (rs, row) -> new PreviewObject(rs.getString(1), rs.getLong(2), rs.getString(3)),
            messageId,
            mailboxId,
            user.organizationId(),
            user.userId());
    if (matches.isEmpty()) throw new NotFoundException("Mail message not found");
    PreviewObject object = matches.get(0);
    verifyObjectIntegrity(object);
    try (InputStream mime = spool.open(object.key())) {
      MailPlainTextExtractor.PlainText text = textExtractor.extract(mime);
      return new MessagePreview(
          Long.toString(messageId), text.text(), text.truncated(), text.available());
    } catch (IOException failure) {
      throw new ServiceUnavailableException("Cannot read verified MIME object", failure);
    }
  }

  private void verifyObjectIntegrity(PreviewObject object) {
    if (object.size() < 1
        || object.size() > 10485760
        || object.sha256() == null
        || !object.sha256().matches("[0-9a-f]{64}")) {
      throw new ServiceUnavailableException("Invalid stored MIME integrity metadata");
    }
    try (InputStream in = spool.open(object.key())) {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      long bytes = 0;
      int read;
      while ((read = in.read(buffer)) != -1) {
        bytes += read;
        if (bytes > object.size()) {
          throw new ServiceUnavailableException("Mail MIME object has changed");
        }
        sha.update(buffer, 0, read);
      }
      if (bytes != object.size()
          || !HexFormat.of().formatHex(sha.digest()).equals(object.sha256())) {
        throw new ServiceUnavailableException("Mail MIME object failed integrity verification");
      }
    } catch (IOException | NoSuchAlgorithmException failure) {
      throw new ServiceUnavailableException("Stored MIME integrity check unavailable", failure);
    }
  }

  @Transactional(readOnly = true)
  public void requireGrant(ConsoleAuthService.Principal user, long mailboxId) {
    Boolean allowed =
        jdbc.queryForObject(
            """
        SELECT EXISTS (
          SELECT 1 FROM email_mailbox_access a
          JOIN email_mailboxes m ON m.id=a.mailbox_id
            AND m.organization_id=a.organization_id
          JOIN email_mail_domains d ON d.id=m.domain_id
            AND d.organization_id=m.organization_id
          JOIN email_product_organizations o ON o.id=m.organization_id
          WHERE a.organization_id=? AND a.mailbox_id=? AND a.user_id=?
            AND m.status='ACTIVE' AND d.status='VERIFIED' AND o.status='ACTIVE')
        """,
            Boolean.class,
            user.organizationId(),
            mailboxId,
            user.userId());
    if (!Boolean.TRUE.equals(allowed)) {
      // Do not disclose that a different user's mailbox exists.
      throw new NotFoundException("Mailbox not found");
    }
  }
}
