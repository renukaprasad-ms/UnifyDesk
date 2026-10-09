package com.messaging.emailprovider.mailbox.outbound;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.ConflictException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.common.id.IdGenerator;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A trusted local MTA feedback adapter submits normalized DSN/SMTP events.
 * Feedback never causes a new outbound delivery attempt.
 */
@Service
@ConditionalOnProperty(prefix="app.mail.feedback",name="enabled",havingValue="true")
public class MailboxDeliveryFeedbackService {
  private final JdbcTemplate jdbc;
  public MailboxDeliveryFeedbackService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

  public enum Type { DEFERRED, DELIVERED, BOUNCED, COMPLAINED }

  public record Event(String organizationId,String mailboxId,String intentId,
      String recipientId,String address,String externalEventId,Type type,
      String smtpCode) {}

  public record Result(String recipientId,String remoteStatus,boolean duplicate,
      boolean stateChanged) {}

  private record Current(String state,String remote,String address) {}

  @Transactional
  public Result accept(Event event) {
    if (event==null || event.type()==null || event.address()==null
        || event.externalEventId()==null
        || !event.externalEventId().matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}")
        || event.address().length()>320 || !event.address().matches(
            "[a-z0-9][a-z0-9._%+-]{0,63}@[a-z0-9-]+(\\.[a-z0-9-]+)+")
        || event.smtpCode()!=null && !event.smtpCode().matches("[245][0-9.]{2,15}")) {
      throw new BadRequestException("Invalid trusted mail feedback");
    }
    long org=id(event.organizationId());
    long mailbox=id(event.mailboxId());
    long intent=id(event.intentId());
    long recipient=id(event.recipientId());
    List<Current> matches=jdbc.query("""
        SELECT r.state,r.remote_status,r.recipient_address
        FROM email_mail_outbound_recipients r
        WHERE r.id=? AND r.organization_id=? AND r.mailbox_id=? AND r.intent_id=?
        FOR UPDATE
        """,(rs,row)->new Current(rs.getString(1),rs.getString(2),rs.getString(3)),
        recipient,org,mailbox,intent);
    if(matches.isEmpty() || !matches.get(0).address().equals(event.address())) {
      throw new NotFoundException("Mail feedback target unavailable");
    }
    Current current=matches.get(0);
    // Result status must be known or possibly accepted. Failed/preflight
    // messages cannot be arbitrarily marked delivered by forged events.
    if (!"HANDED_OFF".equals(current.state()) && !"UNKNOWN".equals(current.state())) {
      throw new ConflictException("SMTP handoff has not been accepted");
    }

    int inserted=jdbc.update("""
        INSERT INTO email_mail_outbound_feedback
          (id,organization_id,mailbox_id,recipient_id,external_event_id,
           event_type,status_code,occurred_at)
        VALUES (?,?,?,?,?,?,?,now())
        ON CONFLICT (recipient_id,external_event_id) DO NOTHING
        """,IdGenerator.nextId(),org,mailbox,recipient,event.externalEventId(),
        event.type().name(),event.smtpCode());
    if(inserted==0) {
      return new Result(Long.toString(recipient),current.remote(),true,false);
    }
    String desired=transition(current.remote(),event.type());
    boolean changed=!desired.equals(current.remote());
    if(changed) {
      jdbc.update("""
          UPDATE email_mail_outbound_recipients
          SET remote_status=?,remote_status_at=now(),
              remote_status_code=?,updated_at=now()
          WHERE id=? AND organization_id=? AND mailbox_id=? AND intent_id=?
          """,desired,event.smtpCode(),recipient,org,mailbox,intent);
    }
    return new Result(Long.toString(recipient),desired,false,changed);
  }

  static String transition(String previous,Type event) {
    // A complaint is terminal. A late conflicting "delivered" event cannot
    // erase a confirmed hard bounce or complaint.
    if ("COMPLAINED".equals(previous)) return previous;
    if (event==Type.COMPLAINED) return "COMPLAINED";
    if ("BOUNCED".equals(previous) || "DELIVERED".equals(previous)) return previous;
    if (event==Type.BOUNCED) return "BOUNCED";
    if (event==Type.DELIVERED) return "DELIVERED";
    if ("NOT_CONFIRMED".equals(previous)) return "DEFERRED";
    return previous;
  }

  static long id(String raw) {
    if(raw==null || !raw.matches("[1-9][0-9]{0,18}")) {
      throw new BadRequestException("Invalid mail identifier");
    }
    try {
      return Long.parseLong(raw);
    } catch(NumberFormatException invalid) {
      throw new BadRequestException("Invalid mail identifier");
    }
  }
}
