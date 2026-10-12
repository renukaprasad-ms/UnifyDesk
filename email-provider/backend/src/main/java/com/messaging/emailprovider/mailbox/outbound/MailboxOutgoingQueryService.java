package com.messaging.emailprovider.mailbox.outbound;

import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.product.console.ConsoleAuthService;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mailbox-user-owned history. Handoff is not proof of final inbox delivery. */
@Service
@ConditionalOnProperty(prefix="app.mail.compose",name="enabled",havingValue="true")
public class MailboxOutgoingQueryService {
  private final JdbcTemplate jdbc;

  public record Outgoing(String intentId,String draftId,String state,String subject,
      long acceptedByPostfix,long pending,long failed,long unknown,
      long remotelyDelivered,long bounced,long complained,String createdAt) {}

  public record Recipient(String address,String type,String state,int attempts,
      String smtpCode,String remoteStatus,String remoteCode) {}

  public MailboxOutgoingQueryService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

  @Transactional(readOnly=true)
  public List<Outgoing> list(ConsoleAuthService.Principal principal,long mailboxId) {
    return jdbc.query("""
        SELECT intent.id,intent.draft_id,intent.state,d.subject,intent.created_at,
          count(*) FILTER(WHERE r.state='HANDED_OFF'),
          count(*) FILTER(WHERE r.state IN('PENDING','PROCESSING','DEFERRED')),
          count(*) FILTER(WHERE r.state='FAILED'),
          count(*) FILTER(WHERE r.state='UNKNOWN'),
          count(*) FILTER(WHERE r.remote_status='DELIVERED'),
          count(*) FILTER(WHERE r.remote_status='BOUNCED'),
          count(*) FILTER(WHERE r.remote_status='COMPLAINED')
        FROM email_mail_outbound_intents intent
        JOIN email_mail_drafts d ON d.id=intent.draft_id
          AND d.organization_id=intent.organization_id
          AND d.mailbox_id=intent.mailbox_id
        JOIN email_mailboxes m ON m.id=intent.mailbox_id
          AND m.organization_id=intent.organization_id AND m.status='ACTIVE'
        JOIN email_mailbox_access access ON access.mailbox_id=m.id
          AND access.organization_id=m.organization_id
          AND access.user_id=?
        JOIN email_mail_outbound_recipients r ON r.intent_id=intent.id
          AND r.organization_id=intent.organization_id
          AND r.mailbox_id=intent.mailbox_id
        WHERE intent.organization_id=? AND intent.mailbox_id=?
          AND d.author_user_id=?
        GROUP BY intent.id,intent.draft_id,intent.state,d.subject,intent.created_at
        ORDER BY intent.id DESC LIMIT 50
        """,(rs,row)->new Outgoing(Long.toString(rs.getLong(1)),
          Long.toString(rs.getLong(2)),rs.getString(3),rs.getString(4),
          rs.getLong(6),rs.getLong(7),rs.getLong(8),rs.getLong(9),
          rs.getLong(10),rs.getLong(11),rs.getLong(12),
          rs.getTimestamp(5).toInstant().toString()),
        principal.userId(),principal.organizationId(),mailboxId,principal.userId());
  }

  @Transactional(readOnly=true)
  public List<Recipient> recipients(ConsoleAuthService.Principal principal,
      long mailboxId,long intentId) {
    Boolean owns=jdbc.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM email_mail_outbound_intents i
          JOIN email_mail_drafts d ON d.id=i.draft_id
            AND d.organization_id=i.organization_id AND d.mailbox_id=i.mailbox_id
          JOIN email_mailbox_access a ON a.mailbox_id=i.mailbox_id
            AND a.organization_id=i.organization_id AND a.user_id=?
          JOIN email_mailboxes m ON m.id=i.mailbox_id
            AND m.organization_id=i.organization_id AND m.status='ACTIVE'
          WHERE i.id=? AND i.organization_id=? AND i.mailbox_id=?
            AND d.author_user_id=?)
        """,Boolean.class,principal.userId(),intentId,
        principal.organizationId(),mailboxId,principal.userId());
    if(!Boolean.TRUE.equals(owns)) throw new NotFoundException("Outgoing message not found");
    return jdbc.query("""
        SELECT recipient_address,recipient_type,state,attempts,last_smtp_code,
          remote_status,remote_status_code
        FROM email_mail_outbound_recipients
        WHERE intent_id=? AND organization_id=? AND mailbox_id=?
        ORDER BY id LIMIT 20
        """,(rs,row)->new Recipient(rs.getString(1),rs.getString(2),
            rs.getString(3),rs.getInt(4),rs.getString(5),
            rs.getString(6),rs.getString(7)),
        intentId,principal.organizationId(),mailboxId);
  }
}
