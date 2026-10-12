package com.messaging.emailprovider.mailbox.inbound;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.ConflictException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.common.id.IdGenerator;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Private delivery-plane intake. The trusted SMTP adapter is responsible for
 * providing the original envelope recipient and a stable delivery key.
 *
 * MIME is written to durable storage before the database transaction. Crash
 * or uncertain commit may leave an orphan file, but cannot acknowledge mail
 * whose body was never durably persisted. PENDING_SCAN is never inbox-visible.
 */
@Service
@ConditionalOnProperty(prefix = "app.mail.inbound", name = "enabled", havingValue = "true")
public class InboundIntakeService {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final MailSpoolStore spool;
  private final boolean scannerEnabled;

  public InboundIntakeService(JdbcTemplate jdbc, TransactionTemplate transactions,
      MailSpoolStore spool,
      @Value("${app.mail.scanner.enabled:false}") boolean scannerEnabled) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.spool = spool;
    this.scannerEnabled = scannerEnabled;
  }

  public record Intake(String id, String state, boolean duplicate) {}
  record Recipient(String localPart, String domain) {}
  private record Target(long id, long organizationId, long quota, long used) {}
  private record Existing(long id, long size, String digest, String status) {}

  public Intake accept(String envelopeTo, String deliveryKey, InputStream message) {
    if (!scannerEnabled) {
      throw new ServiceUnavailableException("Inbound admission requires an available security scanner");
    }
    Recipient recipient = parseRecipient(envelopeTo);
    if (deliveryKey == null || deliveryKey.length() > 128
        || !deliveryKey.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
      throw new BadRequestException("Invalid stable delivery key");
    }
    // Reject unknown, unverified or disabled recipients BEFORE spool I/O.
    if (target(recipient, false).isEmpty()) {
      throw new NotFoundException("Recipient unavailable");
    }
    MailSpoolStore.Stored stored = spool.write(message);
    // The transaction rechecks authorization, locks the mailbox for per-mailbox
    // idempotency and quota reservation, and inserts the quarantine job.
    // On uncertain commit failures, deliberately retain the object for reconciliation.
    try {
      Intake accepted = transactions.execute(ignored -> persist(recipient, deliveryKey, stored));
      if (accepted == null) throw new IllegalStateException("Intake transaction returned no result");
      if (accepted.duplicate()) spool.delete(stored);
      return accepted;
    } catch (BadRequestException | ConflictException | NotFoundException rejected) {
      // These are definitive application rejections, not ambiguous DB commit failures.
      spool.delete(stored);
      throw rejected;
    }
  }

  private Intake persist(Recipient recipient, String deliveryKey, MailSpoolStore.Stored stored) {
    List<Target> matches = target(recipient, true);
    if (matches.isEmpty()) throw new NotFoundException("Recipient unavailable");
    Target mailbox = matches.get(0);
    List<Existing> seen = jdbc.query("""
        SELECT id,mime_size_bytes,sha256,status FROM email_mail_inbound_jobs
        WHERE mailbox_id=? AND delivery_key=?
        """, (rs, row) -> new Existing(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4)),
        mailbox.id(),deliveryKey);
    if (!seen.isEmpty()) {
      Existing previous = seen.get(0);
      if (previous.size() != stored.bytes() || !previous.digest().equals(stored.sha256())) {
        throw new ConflictException("Delivery key already used for different MIME");
      }
      return new Intake(Long.toString(previous.id()), previous.status(), true);
    }
    if (stored.bytes() > mailbox.quota() - mailbox.used()) {
      throw new ConflictException("Mailbox storage quota reached");
    }
    int reserved = jdbc.update("""
        UPDATE email_mailboxes
        SET storage_used_bytes=storage_used_bytes+?,updated_at=now()
        WHERE id=? AND organization_id=?
          AND storage_used_bytes<=storage_quota_bytes-?
        """, stored.bytes(), mailbox.id(),mailbox.organizationId(),stored.bytes());
    if (reserved != 1) throw new ConflictException("Mailbox storage quota reached");
    long id = IdGenerator.nextId();
    jdbc.update("""
        INSERT INTO email_mail_inbound_jobs
          (id,organization_id,mailbox_id,delivery_key,status,
           mime_object_key,mime_size_bytes,sha256)
        VALUES (?,?,?,?,'PENDING_SCAN',?,?,?)
        """,id,mailbox.organizationId(),mailbox.id(),deliveryKey,
        stored.key(),stored.bytes(),stored.sha256());
    return new Intake(Long.toString(id), "PENDING_SCAN", false);
  }

  private List<Target> target(Recipient recipient, boolean forUpdate) {
    String sql = """
        SELECT m.id,m.organization_id,m.storage_quota_bytes,m.storage_used_bytes
        FROM email_mailboxes m JOIN email_mail_domains d
          ON d.id=m.domain_id AND d.organization_id=m.organization_id
        JOIN email_product_organizations o ON o.id=m.organization_id
        WHERE m.local_part=? AND d.domain_name=?
          AND m.status='ACTIVE' AND d.status='VERIFIED'
          AND d.inbound_enabled=TRUE AND o.status='ACTIVE'
        """ + (forUpdate ? " FOR UPDATE OF m" : "");
    return jdbc.query(sql, (rs, row) -> new Target(
        rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4)),
        recipient.localPart(),recipient.domain());
  }

  static Recipient parseRecipient(String raw) {
    if (raw == null || raw.length() > 320 || raw.isBlank()
        || raw.indexOf('@') != raw.lastIndexOf('@')) {
      throw new BadRequestException("Invalid envelope recipient");
    }
    int at = raw.indexOf('@');
    if (at <= 0 || at == raw.length()-1) {
      throw new BadRequestException("Invalid envelope recipient");
    }
    String local = raw.substring(0,at).toLowerCase(Locale.ROOT);
    String domain = raw.substring(at+1).toLowerCase(Locale.ROOT);
    if (local.length() > 64 || domain.length() > 253
        || !local.matches("[a-z0-9][a-z0-9._+-]{0,63}") || local.contains("..")
        || local.endsWith(".") || !domain.contains(".")
        || !domain.matches("[a-z0-9-]+(\\.[a-z0-9-]+)+")) {
      throw new BadRequestException("Invalid envelope recipient");
    }
    return new Recipient(local,domain);
  }
}
