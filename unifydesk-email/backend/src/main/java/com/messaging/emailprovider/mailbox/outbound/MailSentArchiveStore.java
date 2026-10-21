package com.messaging.emailprovider.mailbox.outbound;

import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import com.messaging.emailprovider.mailbox.inbound.MailSpoolStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stores an immutable signed MIME image once per outgoing intent.
 *
 * A crash before the database insert may leave an orphaned .eml object;
 * it must be reconciled conservatively, never blindly deleted on DB errors.
 * The exact persisted MIME bytes are reused for every SMTP recipient.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail.outbound", name = "enabled", havingValue = "true")
public class MailSentArchiveStore {
  public static final int MAX_SIGNED_MIME = 262144;

  private final JdbcTemplate jdbc;
  private final MailSpoolStore spool;

  record Archive(String key, long bytes, String digest) {}

  public MailSentArchiveStore(JdbcTemplate jdbc, MailSpoolStore spool) {
    this.jdbc = jdbc;
    this.spool = spool;
  }

  /**
   * An atomic unique intent insert chooses exactly one signed MIME version.
   * This must happen strictly BEFORE the SMTP DATA attempt.
   */
  public byte[] prepare(long organizationId, long mailboxId, long intentId,
      byte[] signedMime) {
    if (signedMime == null || signedMime.length == 0
        || signedMime.length > MAX_SIGNED_MIME) {
      throw new ServiceUnavailableException("Signed message exceeds archive limits");
    }
    Archive archived = lookup(organizationId, mailboxId, intentId);
    if (archived != null) return readVerified(archived);

    MailSpoolStore.Stored staged = spool.write(new ByteArrayInputStream(signedMime));
    // The spool is durable before the database reference is inserted. On an
    // ambiguous database error, preserve the object for operator reconciliation.
    int inserted = jdbc.update("""
        INSERT INTO email_mail_outbound_archives
          (intent_id,organization_id,mailbox_id,mime_object_key,mime_size_bytes,sha256)
        VALUES (?,?,?,?,?,?)
        ON CONFLICT (intent_id) DO NOTHING
        """, intentId, organizationId, mailboxId, staged.key(),
        staged.bytes(), staged.sha256());
    if (inserted == 0) {
      // Another concurrent SMTP worker won; discard only our temporary copy.
      spool.delete(staged);
    }
    Archive authoritative = lookup(organizationId, mailboxId, intentId);
    if (authoritative == null) {
      throw new ServiceUnavailableException("Outgoing MIME archive unavailable");
    }
    return readVerified(authoritative);
  }

  public byte[] getVerified(long organizationId, long mailboxId, long intentId) {
    Archive archive = lookup(organizationId, mailboxId, intentId);
    if (archive == null) {
      throw new ServiceUnavailableException("Outgoing MIME archive not found");
    }
    return readVerified(archive);
  }

  private Archive lookup(long organizationId, long mailboxId, long intentId) {
    List<Archive> matches = jdbc.query("""
        SELECT mime_object_key,mime_size_bytes,sha256
        FROM email_mail_outbound_archives
        WHERE intent_id=? AND organization_id=? AND mailbox_id=?
        """, (rs, row) -> new Archive(rs.getString(1), rs.getLong(2),
        rs.getString(3)), intentId, organizationId, mailboxId);
    return matches.isEmpty() ? null : matches.get(0);
  }

  private byte[] readVerified(Archive archive) {
    if (archive.bytes() <= 0 || archive.bytes() > MAX_SIGNED_MIME
        || archive.digest() == null || !archive.digest().matches("[0-9a-f]{64}")) {
      throw new ServiceUnavailableException("Invalid MIME archive integrity metadata");
    }
    try (InputStream in = spool.open(archive.key())) {
      byte[] raw = in.readNBytes((int) archive.bytes() + 1);
      if (raw.length != archive.bytes()) {
        throw new ServiceUnavailableException("Archived MIME length has changed");
      }
      byte[] actual = MessageDigest.getInstance("SHA-256").digest(raw);
      if (!MessageDigest.isEqual(actual,
          HexFormat.of().parseHex(archive.digest()))) {
        throw new ServiceUnavailableException("Archived MIME digest mismatch");
      }
      return raw;
    } catch (IOException | NoSuchAlgorithmException failure) {
      throw new ServiceUnavailableException("Archived MIME could not be read", failure);
    }
  }
}
