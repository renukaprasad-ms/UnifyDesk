package com.messaging.emailprovider.mailbox.outbound;

import com.messaging.emailprovider.dkim.entity.DkimKey;
import com.messaging.emailprovider.dkim.service.DkimKeyService;
import com.messaging.emailprovider.dkim.service.DkimSigner;
import com.messaging.emailprovider.smtp.SmtpDeliveryOutcome;
import com.messaging.emailprovider.smtp.SmtpDeliveryResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Independent mailbox outbound worker. SMTP occurs outside DB transactions.
 * Crashed/in-doubt PROCESSING attempts become UNKNOWN, NEVER blind retries.
 */
@Component
@ConditionalOnProperty(prefix="app.mail.outbound",name="enabled",havingValue="true")
public class MailboxOutboundWorker {
  private static final int MAX_ATTEMPTS=3;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final MailboxMimeBuilder mime;
  private final DkimKeyService dkimKeys;
  private final DkimSigner signer;
  private final MailboxSmtpRelay relay;
  private final MailboxSendingRateLimiter limiter;

  record Claim(long recipientId,long intentId,long organizationId,
      long mailboxId,String recipient,UUID lease,int attempt) {}

  private record Context(long sendingDomainId,String domain,String sender,
      String subject,String body,String to,String cc,Instant acceptedAt,
      String replyMessageId) {}

  private record Counts(long total,long pending,long handed,long failed,long unknown) {}

  public MailboxOutboundWorker(JdbcTemplate jdbc,TransactionTemplate transactions,
      MailboxMimeBuilder mime,DkimKeyService dkimKeys,DkimSigner signer,
      MailboxSmtpRelay relay,MailboxSendingRateLimiter limiter) {
    this.jdbc=jdbc;
    this.transactions=transactions;
    this.mime=mime;
    this.dkimKeys=dkimKeys;
    this.signer=signer;
    this.relay=relay;
    this.limiter=limiter;
  }

  @Scheduled(fixedDelayString="${app.mail.outbound.interval-ms:5000}")
  public void poll() {
    // Any timed-out attempt might already have been accepted after SMTP DATA.
    // Never silently resend. Operator investigation must decide recovery.
    List<Long> expired=jdbc.query("""
        WITH stale AS (
          SELECT id FROM email_mail_outbound_recipients
          WHERE state='PROCESSING'
            AND claimed_at < now()-INTERVAL '180 seconds'
          ORDER BY claimed_at,id FOR UPDATE SKIP LOCKED LIMIT 50
        )
        UPDATE email_mail_outbound_recipients r
        SET state='UNKNOWN',claim_token=NULL,claimed_at=NULL,
            last_error='SMTP_RESULT_UNCERTAIN_AFTER_WORKER_FAILURE',
            updated_at=now()
        WHERE r.id IN(SELECT id FROM stale)
        RETURNING r.intent_id
        """,(rs,row)->rs.getLong(1));
    for(Long intentId:expired) refreshIntent(intentId);

    // Sequential, bounded dispatch keeps CPU, sockets and memory predictable.
    for(int i=0;i<4;i++) {
      Claim claim=claim();
      if(claim==null) return;
      process(claim);
    }
  }

  private Claim claim() {
    UUID lease=UUID.randomUUID();
    List<Claim> rows=jdbc.query("""
        WITH candidate AS (
          SELECT id FROM email_mail_outbound_recipients
          WHERE state IN ('PENDING','DEFERRED') AND attempts<3
            AND (retry_after IS NULL OR retry_after<=now())
          ORDER BY retry_after NULLS FIRST,id
          FOR UPDATE SKIP LOCKED LIMIT 1
        )
        UPDATE email_mail_outbound_recipients r
        SET state='PROCESSING',attempts=attempts+1,claimed_at=now(),
            claim_token=?,retry_after=NULL,updated_at=now()
        WHERE r.id IN(SELECT id FROM candidate)
        RETURNING r.id,r.intent_id,r.organization_id,r.mailbox_id,
          r.recipient_address,r.claim_token,r.attempts
        """,(rs,row)->new Claim(rs.getLong(1),rs.getLong(2),
            rs.getLong(3),rs.getLong(4),rs.getString(5),
            (UUID)rs.getObject(6),rs.getInt(7)),lease);
    if(rows.isEmpty()) return null;
    Claim claim=rows.get(0);
    jdbc.update("""
        UPDATE email_mail_outbound_intents SET state='PROCESSING',updated_at=now()
        WHERE id=? AND organization_id=? AND mailbox_id=?
          AND state IN ('HELD','PROCESSING')
        """,claim.intentId(),claim.organizationId(),claim.mailboxId());
    return claim;
  }

  private void process(Claim job) {
    List<Context> matches=jdbc.query("""
        SELECT sd.id,d.domain_name,m.local_part || '@' || d.domain_name,
          dr.subject,dr.body_text,dr.recipients_to,dr.recipients_cc,
          intent.created_at,source.message_id_header
        FROM email_mail_outbound_recipients r
        JOIN email_mail_outbound_intents intent ON intent.id=r.intent_id
          AND intent.organization_id=r.organization_id
          AND intent.mailbox_id=r.mailbox_id
        JOIN email_mail_drafts dr ON dr.id=intent.draft_id
          AND dr.organization_id=intent.organization_id
          AND dr.mailbox_id=intent.mailbox_id
        JOIN email_mailboxes m ON m.id=r.mailbox_id
          AND m.organization_id=r.organization_id
        JOIN email_console_users author ON author.id=dr.author_user_id
          AND author.organization_id=dr.organization_id AND author.status='ACTIVE'
        JOIN email_mailbox_access acl ON acl.mailbox_id=m.id
          AND acl.organization_id=m.organization_id AND acl.user_id=author.id
        JOIN email_mail_domains d ON d.id=m.domain_id
          AND d.organization_id=m.organization_id
        JOIN email_product_organizations org ON org.id=m.organization_id
        JOIN email_product_projects project ON project.organization_id=org.id
          AND project.status='ACTIVE' AND project.sending_enabled=true
        JOIN sending_domains sd ON sd.company_id=project.id
          AND sd.domain_name=d.domain_name AND sd.status='VERIFIED'
          AND sd.dkim_verified_at IS NOT NULL
        LEFT JOIN email_mailbox_messages source ON source.id=dr.reply_to_message_id
          AND source.organization_id=dr.organization_id
          AND source.mailbox_id=dr.mailbox_id
        WHERE r.id=? AND r.organization_id=? AND r.mailbox_id=?
          AND r.claim_token=? AND r.state='PROCESSING'
          AND m.status='ACTIVE' AND d.status='VERIFIED'
          AND d.outbound_enabled=true AND org.status='ACTIVE'
          AND dr.status='PENDING_TRANSPORT'
        LIMIT 1
        """,(rs,row)->new Context(rs.getLong(1),rs.getString(2),rs.getString(3),
            rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),
            rs.getTimestamp(8).toInstant(),rs.getString(9)),
        job.recipientId(),job.organizationId(),job.mailboxId(),job.lease());
    if(matches.isEmpty()) {
      complete(job,"FAILED",null,"SENDER_DOMAIN_NOT_AUTHORIZED");
      return;
    }
    Context context=matches.get(0);
    // Prepare/sign before SMTP I/O: preflight failures may safely be retried.
    byte[] signed;
    try {
      DkimKey key=dkimKeys.getActiveForDomain(context.sendingDomainId());
      if(key.getVerifiedAt()==null) {
        complete(job,"DEFERRED",null,"MAILBOX_DKIM_NOT_VERIFIED");
        return;
      }
      if(!limiter.permit(job.organizationId(),job.mailboxId())) {
        complete(job,"DEFERRED",null,"MAILBOX_RATE_LIMITED_OR_UNAVAILABLE");
        return;
      }
      byte[] unsigned=mime.build(new MailboxMimeBuilder.Message(job.intentId(),
          context.domain(),context.sender(),addresses(context.to()),
          addresses(context.cc()),context.subject(),context.body(),
          context.acceptedAt(),context.replyMessageId()));
      signed=signer.sign(unsigned,context.domain(),key.getSelector(),
          dkimKeys.loadPrivateKey(context.sendingDomainId()));
    } catch(Exception preflightFailed) {
      // If recording this fails, the lease expires into UNKNOWN; no send occurred.
      complete(job,"DEFERRED",null,"MAILBOX_OUTBOUND_PREFLIGHT_FAILED");
      return;
    }

    // SMTP may accept DATA even if the client subsequently fails. Never wrap
    // outcome persistence in a catch that changes HANDED_OFF/UNKNOWN into DEFERRED.
    SmtpDeliveryResult result;
    try {
      result=relay.submit(context.sender(),job.recipient(),signed);
    } catch(Exception uncertain) {
      result=new SmtpDeliveryResult(SmtpDeliveryOutcome.UNKNOWN,null,
          "SMTP_HANDOFF_RESULT_UNCERTAIN");
    }
    complete(job,mapOutcome(result),result==null?null:result.statusCode(),
        "SMTP_HANDOFF_"+(result==null || result.outcome()==null
            ?"UNKNOWN":result.outcome().name()));
  }

  static String mapOutcome(SmtpDeliveryResult result) {
    if(result==null || result.outcome()==null) return "UNKNOWN";
    return switch(result.outcome()) {
      case HANDED_OFF -> "HANDED_OFF";
      case UNKNOWN -> "UNKNOWN";
      case DEFERRED -> "DEFERRED";
      case FAILED -> "FAILED";
    };
  }

  private static List<String> addresses(String commaSeparated) {
    return commaSeparated==null || commaSeparated.isBlank()
        ?List.of():List.of(commaSeparated.split(","));
  }

  private void complete(Claim claim,String target,String smtpCode,String error) {
    String state=("DEFERRED".equals(target) && claim.attempt()>=MAX_ATTEMPTS)
        ?"FAILED":target;
    int backoff=Math.min(1800,60*(1<<Math.min(claim.attempt()-1,4)));
    transactions.executeWithoutResult(ignored->{
      int done=jdbc.update("""
          UPDATE email_mail_outbound_recipients SET state=?,
            claim_token=NULL,claimed_at=NULL,updated_at=now(),
            retry_after=CASE WHEN ?='DEFERRED'
              THEN now()+(? * INTERVAL '1 second') ELSE NULL END,
            last_smtp_code=?,last_error=?,
            handed_off_at=CASE WHEN ?='HANDED_OFF' THEN now() ELSE handed_off_at END
          WHERE id=? AND intent_id=? AND organization_id=? AND mailbox_id=?
            AND claim_token=? AND state='PROCESSING'
          """,state,state,backoff,smtpCode,error,state,
          claim.recipientId(),claim.intentId(),claim.organizationId(),
          claim.mailboxId(),claim.lease());
      if(done==1) refreshIntent(claim.intentId());
    });
  }

  private void refreshIntent(long intentId) {
    List<Counts> counts=jdbc.query("""
        SELECT count(*),
          count(*) FILTER(WHERE state IN('PENDING','PROCESSING','DEFERRED')),
          count(*) FILTER(WHERE state='HANDED_OFF'),
          count(*) FILTER(WHERE state='FAILED'),
          count(*) FILTER(WHERE state='UNKNOWN')
        FROM email_mail_outbound_recipients WHERE intent_id=?
        """,(rs,row)->new Counts(rs.getLong(1),rs.getLong(2),rs.getLong(3),
          rs.getLong(4),rs.getLong(5)),intentId);
    if(counts.isEmpty() || counts.get(0).total()==0) return;
    Counts c=counts.get(0);
    String state=c.pending()>0?"PROCESSING"
        :c.unknown()>0?"UNKNOWN"
        :c.handed()==c.total()?"HANDOFF_COMPLETED"
        :c.handed()>0?"PARTIAL":"FAILED";
    jdbc.update("""
        UPDATE email_mail_outbound_intents SET state=?,updated_at=now()
        WHERE id=?
        """,state,intentId);
  }
}
