package com.messaging.emailprovider.mailbox;

import com.messaging.emailprovider.common.exception.*;
import com.messaging.emailprovider.product.console.ConsoleAuthService.Principal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reversible message organization. Only explicit mailbox delegates may move messages. */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    prefix = "app.mail.inbound",
    name = "enabled",
    havingValue = "true")
public class MailboxLifecycleService {
  private final JdbcTemplate jdbc;
  private final MailboxReadService read;
  private final MailAuditService audit;

  public MailboxLifecycleService(
      JdbcTemplate jdbc, MailboxReadService read, MailAuditService audit) {
    this.jdbc = jdbc;
    this.read = read;
    this.audit = audit;
  }

  @Transactional
  public void move(Principal actor, long mailbox, long message, String folder) {
    requireDelegate(actor, mailbox);
    if (!"ARCHIVE".equals(folder) && !"TRASH".equals(folder))
      throw new BadRequestException("Choose Archive or Trash");
    var rows =
        jdbc.query(
            "SELECT folder FROM email_mailbox_messages WHERE organization_id=? AND mailbox_id=? AND id=? AND deleted_at IS NULL AND purged_at IS NULL FOR UPDATE",
            (rs, row) -> rs.getString(1),
            actor.organizationId(),
            mailbox,
            message);
    if (rows.isEmpty()) throw new NotFoundException("Message not found");
    if (rows.getFirst().equals(folder)) return;
    jdbc.update(
        """
        UPDATE email_mailbox_messages SET previous_folder=CASE WHEN folder='ARCHIVE' THEN previous_folder ELSE folder END,folder=?,
          trashed_at=CASE WHEN ?='TRASH' THEN now() ELSE NULL END,
          deleted_at=CASE WHEN ?='TRASH' THEN now() ELSE NULL END,updated_at=now() WHERE id=?
        """,
        folder,
        folder,
        folder,
        message);
    audit.record(actor, mailbox, message, "MOVE_MESSAGE", null);
  }

  @Transactional
  public void restore(Principal actor, long mailbox, long message) {
    requireDelegate(actor, mailbox);
    var rows =
        jdbc.query(
            """
        SELECT msg.id FROM email_mailbox_messages msg
        WHERE msg.organization_id=? AND msg.mailbox_id=? AND msg.id=? AND msg.purged_at IS NULL
          AND msg.folder IN ('TRASH','ARCHIVE')
          AND NOT EXISTS(SELECT 1 FROM email_mail_object_gc gc WHERE gc.message_id=msg.id)
        FOR UPDATE OF msg
        """,
            (rs, row) -> rs.getLong(1),
            actor.organizationId(),
            mailbox,
            message);
    if (rows.isEmpty()) throw new NotFoundException("Restorable message not found");
    jdbc.update(
        """
        UPDATE email_mailbox_messages SET folder=CASE
          WHEN previous_folder IN ('INBOX','SPAM','SENT','ARCHIVE') THEN previous_folder
          WHEN direction='OUTBOUND' THEN 'SENT' ELSE 'INBOX' END,
          previous_folder=NULL,trashed_at=NULL,deleted_at=NULL,updated_at=now() WHERE id=?
        """,
        message);
    audit.record(actor, mailbox, message, "RESTORE_MESSAGE", null);
  }

  private void requireDelegate(Principal actor, long mailbox) {
    read.requireGrant(actor, mailbox);
    var rows =
        jdbc.query(
            """
        SELECT m.id FROM email_mailboxes m JOIN email_mailbox_access a
          ON a.mailbox_id=m.id AND a.organization_id=m.organization_id
        WHERE m.id=? AND m.organization_id=? AND a.user_id=? AND a.can_delegate=TRUE
        FOR UPDATE OF m
        """,
            (rs, row) -> rs.getLong(1),
            mailbox,
            actor.organizationId(),
            actor.userId());
    if (rows.isEmpty()) throw new NotFoundException("Mailbox not found");
  }
}
