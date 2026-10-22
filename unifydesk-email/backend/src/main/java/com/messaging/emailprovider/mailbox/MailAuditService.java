package com.messaging.emailprovider.mailbox;

import com.messaging.emailprovider.common.id.IdGenerator;
import com.messaging.emailprovider.product.console.ConsoleAuthService.Principal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Audit writes fail closed. Never record subjects, addresses, tokens, or message bodies. */
@Service
public class MailAuditService {
  private final JdbcTemplate jdbc;

  public MailAuditService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void record(
      Principal actor, long mailboxId, Long messageId, String action, Long targetUserId) {
    jdbc.update(
        """
        INSERT INTO email_mail_audit
        (id,organization_id,mailbox_id,actor_user_id,target_user_id,message_id,action)
        VALUES (?,?,?,?,?,?,?)
        """,
        IdGenerator.nextId(),
        actor.organizationId(),
        mailboxId,
        actor.userId(),
        targetUserId,
        messageId,
        action);
  }
}
