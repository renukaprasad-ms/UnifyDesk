package com.messaging.managedsms.metareach;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.common.id.IdGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable per-recipient DLR reconciliation. Never resubmits SMS. */
@Service @RequiredArgsConstructor
public class MetaReachDlrScheduler {
  private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
  private static final DateTimeFormatter DLR_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
  private final JdbcTemplate jdbc;
  private final MetaReachSmsClient client;
  private final MetaReachProperties properties;
  private final TransactionTemplate transaction;

  public void enqueue(Long companyId,Long runId,Long recipientId,String correlation,
                      String messageId,String mobile) {
    jdbc.update("""
        insert into managed_sms_metareach_dlr_jobs
          (id,company_id,run_id,recipient_id,correlation_token,message_id,mobile,
           state,poll_count,next_poll_at,expires_at,created_at)
        values (?,?,?,?,?,?,?,'PENDING',0,now()+interval '30 seconds',now()+interval '2 hours',now())
        on conflict (correlation_token) do nothing
        """,IdGenerator.nextId(),companyId,runId,recipientId,correlation,messageId,
          MetaReachSmsClient.mobile(mobile));
  }

  @Scheduled(fixedDelayString="${app.managed-sms.metareach.dlr-poll-ms:30000}")
  public void poll() {
    if (!properties.ready()) return;
    List<Job> jobs = transaction.execute(status -> {
      List<Job> claimed=jdbc.query("""
          select id,company_id,run_id,recipient_id,correlation_token,message_id,mobile,poll_count
          from managed_sms_metareach_dlr_jobs
          where state='PENDING' and next_poll_at<=now()
          order by next_poll_at,id for update skip locked limit 40
          """,(rs,n) -> new Job(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),
              rs.getString(5),rs.getString(6),rs.getString(7),rs.getInt(8)));
      for (Job job:claimed) jdbc.update("""
          update managed_sms_metareach_dlr_jobs
          set state='CLAIMED', lease_until=now()+interval '2 minutes'
          where id=? and state='PENDING'
          """,job.id());
      return claimed;
    });
    if (jobs==null) return;
    for (Job job:jobs) {
      try {
        Optional<JsonNode> dlr=client.dlr(job.messageId());
        transaction.executeWithoutResult(status -> reconcile(job,dlr));
      } catch (RuntimeException ignored) {
        transaction.executeWithoutResult(status -> reschedule(job,false));
      }
    }
  }

  @Scheduled(fixedDelayString="${app.managed-sms.metareach.dlr-reclaim-ms:120000}")
  public void recoverStaleClaims() {
    jdbc.update("""
        update managed_sms_metareach_dlr_jobs set state='PENDING',lease_until=null,
        next_poll_at=now()+interval '30 seconds'
        where state='CLAIMED' and lease_until<now()
        """);
  }

  @Scheduled(fixedDelayString="${app.managed-sms.metareach.dlr-repair-ms:60000}")
  public void repairMissingJobs() {
    if (!properties.ready()) return;
    // Repair a post-acceptance database failure without ever resending the SMS.
    jdbc.query("""
        select c.company_id,c.run_id,c.recipient_id,c.correlation_token,
               c.provider_request_id,r.destination
        from managed_sms_correlations c
        join managed_sms_run_bindings rb
          on rb.run_id=c.run_id and rb.company_id=c.company_id
        join broadcast_recipients r
          on r.id=c.recipient_id and r.run_id=c.run_id and r.company_id=c.company_id
        left join managed_sms_metareach_dlr_jobs j on j.correlation_token=c.correlation_token
        where rb.delivery_provider='METAREACH_SMS'
          and c.provider_request_id is not null
          and c.created_at>now()-interval '2 hours'
          and j.id is null
        order by c.created_at limit 100
        """,rs -> {
      try {
        enqueue(rs.getLong(1),rs.getLong(2),rs.getLong(3),
            rs.getString(4),rs.getString(5),rs.getString(6));
      } catch (RuntimeException ignored) { /* Periodic repair will try again. */ }
    });
  }

  private void reconcile(Job job,Optional<JsonNode> response) {
    if (response.isEmpty()) { reschedule(job,false); return; }
    JsonNode rows=response.get().path("data");
    if (rows.size()>200) {reschedule(job,false);return;}
    for (JsonNode item:rows) {
      if (!job.messageId().equals(item.path("id").asText(""))) continue;
      String actual=item.path("mobile").asText("").replaceAll("[^0-9]","");
      if (!sameIndianNumber(job.mobile(),actual)) continue;
      String eventType=deliveryEventType(item.path("status").asText(""));
      if (eventType==null) {reschedule(job,true);return;}
      String time=item.path("delvd_time").asText("");
      Instant observedAt;
      try {observedAt=LocalDateTime.parse(time,DLR_TIME).atZone(INDIA).toInstant();}
      catch(Exception ignored) { observedAt=Instant.now(); }
      String key=hash(job.correlation()+"|"+job.messageId()+"|"+eventType+"|"+time);
      jdbc.update("""
          insert into managed_sms_provider_events(
             id,provider,provider_account_key,provider_event_key,raw_hash,event_type,
             correlation_token,provider_request_id,payload_json,trust_status,processing_status,
             provider_event_at,received_at,processed_at)
          values (?,'METAREACH_SMS',?,?,?, ?,?,?,?,'TRUSTED','PENDING',?,now(),null)
          on conflict (provider,provider_account_key,provider_event_key) do nothing
          """,IdGenerator.nextId(),properties.accountKey(),key,hash(item.toString()),
          eventType,job.correlation(),job.messageId(),item.toString(),Timestamp.from(observedAt));
      jdbc.update("update managed_sms_metareach_dlr_jobs set state='TERMINAL',lease_until=null where id=?",job.id());
      return;
    }
    reschedule(job,true);
  }

  private void reschedule(Job job,boolean contacted) {
    // Bound polling traffic; keep unknown outcomes unresolved, never invent delivery.
    int n=job.pollCount()+1;
    long delay=n<3?60:n<7?180:n<13?600:1800;
    jdbc.update("""
        update managed_sms_metareach_dlr_jobs
        set state=case when expires_at<=now() then 'EXPIRED' else 'PENDING' end,
            poll_count=?,lease_until=null,
            next_poll_at=now()+(? * interval '1 second')
        where id=? and state='CLAIMED'
        """,n,delay,job.id());
  }

  static String deliveryEventType(String status) {
    if (status==null) return null;
    return switch(status.trim().toLowerCase(Locale.ROOT)) {
      case "delivered" -> "DELIVERED";
      case "failed", "undelivered", "expired" -> "FAILED";
      case "rejected", "blocked", "ndnc" -> "REJECTED";
      default -> null;
    };
  }

  private static boolean sameIndianNumber(String expected,String actual) {
    if (expected==null || actual==null) return false;
    return expected.matches("91[6-9][0-9]{9}") &&
           (actual.equals(expected) || actual.equals(expected.substring(2)));
  }
  private static String hash(String input) {
    try {
      byte[] digest=MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception ex) {throw new IllegalStateException(ex);}
  }
  private record Job(long id,long companyId,long runId,long recipientId,
                     String correlation,String messageId,String mobile,int pollCount) {}
}
