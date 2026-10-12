package com.messaging.emailprovider.mailbox.outbound;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Bounded operational aggregates over indexed state/time windows.
 * No email addresses, subjects or customer content are exposed.
 */
@Service
@ConditionalOnProperty(prefix="app.mail.ops",name="enabled",havingValue="true")
public class MailboxOperationsService {
  private final JdbcTemplate jdbc;

  public record Snapshot(long outboundAccepted24h,long outboundFailed24h,
      long smtpUnknownAll,long remoteBounced24h,long remoteComplained24h,
      long inboundPendingScan,long inboundQuarantinedAll) {}

  public MailboxOperationsService(JdbcTemplate jdbc) {
    this.jdbc=jdbc;
  }

  public Snapshot snapshot() {
    Long accepted=number("""
        SELECT count(*) FROM email_mail_outbound_recipients
        WHERE state='HANDED_OFF' AND handed_off_at>=now()-INTERVAL '1 day'
        """);
    Long failed=number("""
        SELECT count(*) FROM email_mail_outbound_recipients
        WHERE state='FAILED' AND updated_at>=now()-INTERVAL '1 day'
        """);
    Long unknown=number("""
        SELECT count(*) FROM email_mail_outbound_recipients
        WHERE state='UNKNOWN'
        """);
    Long bounced=number("""
        SELECT count(*) FROM email_mail_outbound_recipients
        WHERE remote_status='BOUNCED'
          AND remote_status_at>=now()-INTERVAL '1 day'
        """);
    Long complained=number("""
        SELECT count(*) FROM email_mail_outbound_recipients
        WHERE remote_status='COMPLAINED'
          AND remote_status_at>=now()-INTERVAL '1 day'
        """);
    Long scanWaiting=number("""
        SELECT count(*) FROM email_mail_inbound_jobs
        WHERE status IN ('PENDING_SCAN','SCANNING')
        """);
    Long quarantined=number("""
        SELECT count(*) FROM email_mail_inbound_jobs
        WHERE status='QUARANTINED'
        """);
    return new Snapshot(accepted,failed,unknown,bounced,complained,
        scanWaiting,quarantined);
  }

  /** Read-only reconciliation queue. An uncertain DATA response is never retried. */
  public record UnknownAttempt(String recipientId,String intentId,String organizationId,
      String mailboxId,int attempts,String lastUpdated) {}

  public List<UnknownAttempt> unknownAttempts() {
    return jdbc.query("""
        SELECT id,intent_id,organization_id,mailbox_id,attempts,updated_at
        FROM email_mail_outbound_recipients
        WHERE state='UNKNOWN'
        ORDER BY updated_at DESC,id DESC LIMIT 100
        """,(rs,row)->new UnknownAttempt(
            Long.toString(rs.getLong(1)),Long.toString(rs.getLong(2)),
            Long.toString(rs.getLong(3)),Long.toString(rs.getLong(4)),
            rs.getInt(5),rs.getTimestamp(6).toInstant().toString()));
  }

  private long number(String query) {
    Long count=jdbc.queryForObject(query,Long.class);
    return count==null?0:count;
  }
}
