package com.messaging.emailprovider.mailbox;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.ConflictException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.common.id.IdGenerator;
import java.net.IDN;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organization-scoped mailbox metadata. This does not activate mail routing or SMTP.
 * Uses indexed point queries and bounded listings; no cross-tenant entity loading.
 */
@Service
public class MailboxProvisioningService {
  private static final int MAX_LIST_RESULTS = 100;
  private final JdbcTemplate jdbc;

  public MailboxProvisioningService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Domain(String id, String name, String status) {}
  public record Mailbox(String id, String address, String status, long quotaBytes) {}

  @Transactional
  public Domain registerDomain(long organizationId, String value) {
    String domain = normalizeDomain(value);
    Long count = jdbc.queryForObject(
        "SELECT count(*) FROM email_mail_domains WHERE organization_id=?", Long.class, organizationId);
    if (count == null || count >= 20) {
      throw new ConflictException("Organization domain limit reached");
    }
    long id = IdGenerator.nextId();
    try {
      jdbc.update("""
          INSERT INTO email_mail_domains(id,organization_id,domain_name,status)
          VALUES (?,?,?,'PENDING')
          """, id, organizationId, domain);
    } catch (DuplicateKeyException duplicate) {
      // Do not reveal whether another organization owns the domain.
      throw new ConflictException("Domain is unavailable");
    }
    return new Domain(Long.toString(id), domain, "PENDING");
  }

  @Transactional(readOnly = true)
  public List<Domain> domains(long organizationId) {
    return jdbc.query("""
        SELECT id,domain_name,status FROM email_mail_domains
        WHERE organization_id=? ORDER BY id DESC LIMIT ?
        """, (rs, row) -> new Domain(Long.toString(rs.getLong(1)),
        rs.getString(2), rs.getString(3)), organizationId, MAX_LIST_RESULTS);
  }

  @Transactional
  public Mailbox createMailbox(long organizationId, long domainId, String localPart,
      String displayName) {
    String local = normalizeLocalPart(localPart);
    String display = displayName == null ? null : displayName.trim();
    if (display != null && display.length() > 160) {
      throw new BadRequestException("Display name is too long");
    }
    // The parent domain must belong to this organization. PENDING domains
    // may stage DISABLED mailboxes but cannot receive or send real mail.
    List<String> domain = jdbc.query("""
        SELECT domain_name FROM email_mail_domains
        WHERE id=? AND organization_id=? AND status <> 'SUSPENDED'
        """, (rs, row) -> rs.getString(1), domainId, organizationId);
    if (domain.isEmpty()) throw new NotFoundException("Mail domain not found");
    Long count = jdbc.queryForObject("""
        SELECT count(*) FROM email_mailboxes WHERE organization_id=?
        """, Long.class, organizationId);
    if (count == null || count >= 100) {
      throw new ConflictException("Organization mailbox limit reached");
    }
    long id = IdGenerator.nextId();
    try {
      int inserted = jdbc.update("""
          INSERT INTO email_mailboxes(id,organization_id,domain_id,local_part,display_name,status)
          SELECT ?,d.organization_id,d.id,?,?,'DISABLED'
          FROM email_mail_domains d
          WHERE d.id=? AND d.organization_id=? AND d.status <> 'SUSPENDED'
          """, id, local, display, domainId, organizationId);
      if (inserted != 1) throw new NotFoundException("Mail domain not found");
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("Mailbox address already registered");
    }
    return new Mailbox(Long.toString(id), local + "@" + domain.get(0),
        "DISABLED", 1073741824L);
  }

  @Transactional(readOnly = true)
  public List<Mailbox> mailboxes(long organizationId, long domainId) {
    return jdbc.query("""
        SELECT m.id,m.local_part || '@' || d.domain_name,m.status,m.storage_quota_bytes
        FROM email_mailboxes m JOIN email_mail_domains d
          ON m.domain_id=d.id AND m.organization_id=d.organization_id
        WHERE m.organization_id=? AND m.domain_id=?
        ORDER BY m.id DESC LIMIT ?
        """, (rs, row) -> new Mailbox(Long.toString(rs.getLong(1)),
        rs.getString(2), rs.getString(3), rs.getLong(4)),
        organizationId, domainId, MAX_LIST_RESULTS);
  }

  static String normalizeDomain(String value) {
    if (value == null || value.length() > 253 || value.isBlank()
        || !value.equals(value.trim())) throw new BadRequestException("Invalid domain");
    String domain;
    try {
      domain = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException invalid) {
      throw new BadRequestException("Invalid domain");
    }
    if (domain.length() > 253 || !domain.contains(".") || domain.endsWith(".")
        || domain.startsWith(".") || domain.contains("..")
        || domain.chars().anyMatch(c -> c <= 32 || c >= 127)) {
      throw new BadRequestException("Invalid domain");
    }
    for (String label : domain.split("\\.")) {
      if (label.isEmpty() || label.length() > 63
          || label.startsWith("-") || label.endsWith("-")) {
        throw new BadRequestException("Invalid domain");
      }
    }
    return domain;
  }

  static String normalizeLocalPart(String value) {
    if (value == null || value.length() > 64
        || !value.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}")
        || value.contains("..") || value.endsWith(".")) {
      throw new BadRequestException("Invalid mailbox local part");
    }
    return value.toLowerCase(Locale.ROOT);
  }
}
