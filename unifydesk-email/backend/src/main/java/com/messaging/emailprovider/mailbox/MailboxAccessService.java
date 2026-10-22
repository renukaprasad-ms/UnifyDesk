package com.messaging.emailprovider.mailbox;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.product.console.ConsoleAuthService.Principal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Only an explicit mailbox delegate may grant access; organization admin is not a read bypass. */
@Service
public class MailboxAccessService {
  private final JdbcTemplate jdbc;
  private final MailAuditService audit;

  public record Grant(String userId, boolean canRead, boolean canSend, boolean canDelegate) {}

  public MailboxAccessService(JdbcTemplate jdbc, MailAuditService audit) {
    this.jdbc = jdbc;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<Grant> list(Principal actor, long mailboxId) {
    requireDelegate(actor, mailboxId, false);
    return jdbc.query(
        """
        SELECT user_id,can_read,can_send,can_delegate FROM email_mailbox_access
        WHERE organization_id=? AND mailbox_id=? ORDER BY user_id LIMIT 100
        """,
        (rs, row) ->
            new Grant(rs.getString(1), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4)),
        actor.organizationId(),
        mailboxId);
  }

  @Transactional
  public void grant(
      Principal actor,
      long mailboxId,
      long targetUserId,
      boolean read,
      boolean send,
      boolean delegate) {
    requireDelegate(actor, mailboxId, true);
    if (targetUserId == actor.userId())
      throw new BadRequestException("Use another delegate to change your own access");
    if (!read && !send && !delegate)
      throw new BadRequestException("At least one permission is required");
    Boolean valid =
        jdbc.queryForObject(
            """
        SELECT EXISTS(SELECT 1 FROM email_console_users WHERE id=? AND organization_id=? AND status='ACTIVE')
        """,
            Boolean.class,
            targetUserId,
            actor.organizationId());
    if (!Boolean.TRUE.equals(valid)) throw new NotFoundException("Active user not found");
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM email_mailbox_access WHERE mailbox_id=?", Long.class, mailboxId);
    Boolean exists =
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM email_mailbox_access WHERE mailbox_id=? AND user_id=?)",
            Boolean.class,
            mailboxId,
            targetUserId);
    if (!Boolean.TRUE.equals(exists) && count != null && count >= 100)
      throw new BadRequestException("Mailbox grant limit reached");
    jdbc.update(
        """
        INSERT INTO email_mailbox_access(organization_id,mailbox_id,user_id,can_read,can_send,can_delegate)
        VALUES (?,?,?,?,?,?) ON CONFLICT(mailbox_id,user_id)
        DO UPDATE SET can_read=excluded.can_read,can_send=excluded.can_send,can_delegate=excluded.can_delegate
        """,
        actor.organizationId(),
        mailboxId,
        targetUserId,
        read,
        send,
        delegate);
    audit.record(actor, mailboxId, null, "GRANT", targetUserId);
  }

  @Transactional
  public void revoke(Principal actor, long mailboxId, long targetUserId) {
    requireDelegate(actor, mailboxId, true);
    if (targetUserId == actor.userId())
      throw new BadRequestException("Use another delegate to revoke your own access");
    int changed =
        jdbc.update(
            "DELETE FROM email_mailbox_access WHERE organization_id=? AND mailbox_id=? AND user_id=?",
            actor.organizationId(),
            mailboxId,
            targetUserId);
    if (changed != 0) audit.record(actor, mailboxId, null, "REVOKE", targetUserId);
  }

  private void requireDelegate(Principal actor, long mailboxId, boolean lock) {
    List<Long> rows =
        jdbc.query(
            """
        SELECT m.id FROM email_mailboxes m
        JOIN email_mailbox_access a ON a.mailbox_id=m.id AND a.organization_id=m.organization_id
        JOIN email_mail_domains d ON d.id=m.domain_id AND d.organization_id=m.organization_id
        JOIN email_product_organizations o ON o.id=m.organization_id
        JOIN email_console_users u ON u.id=a.user_id AND u.organization_id=a.organization_id
        JOIN email_console_sessions s ON s.user_id=u.id AND s.id=?
        WHERE m.id=? AND m.organization_id=? AND a.user_id=? AND a.can_delegate=TRUE
          AND m.status='ACTIVE' AND d.status='VERIFIED' AND o.status='ACTIVE'
          AND u.status='ACTIVE' AND s.revoked_at IS NULL AND s.expires_at>now()
        """
                + (lock ? " FOR UPDATE OF m" : ""),
            (rs, row) -> rs.getLong(1),
            actor.sessionId(),
            mailboxId,
            actor.organizationId(),
            actor.userId());
    if (rows.isEmpty()) throw new NotFoundException("Mailbox not found");
  }
}
