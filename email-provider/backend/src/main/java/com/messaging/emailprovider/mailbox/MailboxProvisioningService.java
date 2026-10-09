package com.messaging.emailprovider.mailbox;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.ConflictException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.common.id.IdGenerator;
import com.messaging.emailprovider.dns.DnsResolver;
import com.messaging.emailprovider.product.auth.ApiKeyCrypto;
import java.net.IDN;
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
  private static final String DNS_PREFIX = "ud-mail-verification=";
  private final JdbcTemplate jdbc;
  private final DnsResolver dns;

  public MailboxProvisioningService(JdbcTemplate jdbc, DnsResolver dns) {
    this.jdbc = jdbc;
    this.dns = dns;
  }

  public record Domain(String id, String name, String status) {}
  // TXT value is a one-time response; only its SHA-256 digest is stored.
  public record DomainChallenge(Domain domain, String txtName, String txtValue) {}
  public record Mailbox(String id, String address, String status, long quotaBytes) {}

  @Transactional
  public DomainChallenge registerDomain(long organizationId, String value) {
    String domain = normalizeDomain(value);
    lockOrganization(organizationId);
    Long count = jdbc.queryForObject(
        "SELECT count(*) FROM email_mail_domains WHERE organization_id=?", Long.class, organizationId);
    if (count == null || count >= 20) {
      throw new ConflictException("Organization domain limit reached");
    }
    long id = IdGenerator.nextId();
    String token = ApiKeyCrypto.random(32);
    try {
      jdbc.update("""
          INSERT INTO email_mail_domains(
            id,organization_id,domain_name,status,verification_token_hash)
          VALUES (?,?,?,'PENDING',?)
          """, id, organizationId, domain, ApiKeyCrypto.hash(token));
    } catch (DuplicateKeyException duplicate) {
      // Do not reveal whether another organization owns the domain.
      throw new ConflictException("Domain is unavailable");
    }
    return new DomainChallenge(new Domain(Long.toString(id), domain, "PENDING"),
        txtName(domain), DNS_PREFIX + token);
  }

  /** Rotate a lost proof; concurrent verification with the old token fails closed. */
  @Transactional
  public DomainChallenge rotateChallenge(long organizationId, long domainId) {
    lockOrganization(organizationId);
    List<String> names = jdbc.query("""
        SELECT domain_name FROM email_mail_domains
        WHERE id=? AND organization_id=? AND status='PENDING' FOR UPDATE
        """, (rs, row) -> rs.getString(1), domainId, organizationId);
    if (names.isEmpty()) throw new NotFoundException("Pending mail domain not found");
    String token = ApiKeyCrypto.random(32);
    jdbc.update("""
        UPDATE email_mail_domains SET verification_token_hash=?,updated_at=now()
        WHERE id=? AND organization_id=? AND status='PENDING'
        """, ApiKeyCrypto.hash(token), domainId, organizationId);
    return new DomainChallenge(new Domain(Long.toString(domainId), names.get(0), "PENDING"),
        txtName(names.get(0)), DNS_PREFIX + token);
  }

  /**
   * Do DNS network I/O outside a DB transaction, then atomically accept
   * the proof only if the stored challenge hasn't rotated.
   */
  public Domain verifyDomain(long organizationId, long domainId) {
    record Pending(String name, String hash, String status) {}
    List<Pending> rows = jdbc.query("""
        SELECT domain_name,verification_token_hash,status FROM email_mail_domains
        WHERE id=? AND organization_id=?
        """, (rs, row) -> new Pending(rs.getString(1), rs.getString(2),
        rs.getString(3)), domainId, organizationId);
    if (rows.isEmpty()) throw new NotFoundException("Mail domain not found");
    Pending pending = rows.get(0);
    if ("VERIFIED".equals(pending.status())) {
      return new Domain(Long.toString(domainId), pending.name(), "VERIFIED");
    }
    if (!"PENDING".equals(pending.status()) || pending.hash() == null) {
      throw new ConflictException("Mail domain cannot be verified");
    }
    boolean verified = dns.findTxtRecords(txtName(pending.name())).stream()
        .limit(64)
        .map(txt -> txt.replace("\\\"", "").trim())
        .filter(txt -> txt.startsWith(DNS_PREFIX) && txt.length() < 120)
        .map(txt -> txt.substring(DNS_PREFIX.length()))
        .anyMatch(token -> ApiKeyCrypto.matches(token, pending.hash()));
    if (!verified) throw new ConflictException("DNS ownership TXT record not found");
    int updated = jdbc.update("""
        UPDATE email_mail_domains SET status='VERIFIED',verified_at=now(),updated_at=now()
        WHERE id=? AND organization_id=? AND status='PENDING'
          AND verification_token_hash=?
          AND EXISTS (SELECT 1 FROM email_product_organizations o
                      WHERE o.id=? AND o.status='ACTIVE')
        """, domainId, organizationId, pending.hash(), organizationId);
    if (updated != 1) throw new ConflictException("Domain changed; retry verification");
    // Verification is ownership only: no MX, mailboxes or SMTP service enabled.
    return new Domain(Long.toString(domainId), pending.name(), "VERIFIED");
  }

  private static String txtName(String domain) {
    return "_unifydesk-mail." + domain;
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
    lockOrganization(organizationId);
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

  // Serialize per-organization provisioning so concurrent requests cannot bypass limits.
  private void lockOrganization(long organizationId) {
    List<Long> found = jdbc.query(
        "SELECT id FROM email_product_organizations WHERE id=? AND status='ACTIVE' FOR UPDATE",
        (rs, row) -> rs.getLong(1), organizationId);
    if (found.isEmpty()) throw new NotFoundException("Organization not found");
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
