package com.messaging.notification.platform.delivery.repository;

import com.messaging.common.id.IdGenerator;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProviderAttemptLifecycleRepository {

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public long prepareAttempt(
      long intentId, Long subscriptionId, int attemptNo, String channel, Instant preparedAt) {
    long id = IdGenerator.nextId();
    jdbcTemplate.update(
        """
        insert into notification_delivery_attempts(
          id,intent_id,subscription_id,attempt_no,channel,started_at,outcome,
          provider_call_state,provider_call_started_at,created_at
        ) values (?,?,?,?,?,?,'UNKNOWN','PREPARED',null,now())
        on conflict do nothing
        """,
        id,
        intentId,
        subscriptionId,
        attemptNo,
        channel,
        utc(preparedAt));

    Long stored =
        jdbcTemplate.query(
            """
            select id
            from notification_delivery_attempts
            where intent_id=?
              and subscription_id is not distinct from ?
              and attempt_no=?
            order by id
            limit 1
            """,
            rs -> rs.next() ? rs.getLong(1) : null,
            intentId,
            subscriptionId,
            attemptNo);
    if (stored == null) {
      throw new IllegalStateException("Provider attempt could not be prepared");
    }
    return stored;
  }

  @Transactional
  public boolean markInFlight(long attemptId, Instant providerCallStartedAt) {
    return jdbcTemplate.update(
            """
            update notification_delivery_attempts
            set provider_call_state='IN_FLIGHT',provider_call_started_at=?
            where id=? and provider_call_state='PREPARED' and completed_at is null
            """,
            utc(providerCallStartedAt),
            attemptId)
        == 1;
  }

  @Transactional
  public boolean completeAttempt(
      long attemptId,
      Instant completedAt,
      String outcome,
      Integer providerStatusCode,
      String errorCode,
      long latencyMs) {
    return jdbcTemplate.update(
            """
            update notification_delivery_attempts
            set completed_at=?,outcome=?,provider_status_code=?,provider_error_code=?,latency_ms=?,
                provider_call_state='COMPLETED'
            where id=? and provider_call_state='IN_FLIGHT' and completed_at is null
            """,
            utc(completedAt),
            outcome,
            providerStatusCode,
            errorCode,
            Math.max(0, latencyMs),
            attemptId)
        == 1;
  }

  public int nextOrPreparedEmailAttemptNo(long intentId) {
    Integer prepared =
        jdbcTemplate.query(
            """
            select attempt_no
            from notification_delivery_attempts
            where intent_id=?
              and subscription_id is null
              and provider_call_state='PREPARED'
              and completed_at is null
            order by attempt_no desc
            limit 1
            """,
            rs -> rs.next() ? rs.getInt(1) : null,
            intentId);
    if (prepared != null) {
      return prepared;
    }
    Integer next =
        jdbcTemplate.queryForObject(
            """
            select coalesce(max(attempt_no),0)+1
            from notification_delivery_attempts
            where intent_id=? and subscription_id is null
            """,
            Integer.class,
            intentId);
    return next == null ? 1 : next;
  }

  public List<ExpiredEmailClaim> expiredEmailClaims(int limit) {
    return jdbcTemplate.query(
        """
        select d.id as intent_id,d.claim_token,
               a.id as attempt_id,a.attempt_no,a.provider_call_state,a.outcome,
               a.completed_at,a.provider_error_code
        from notification_delivery_intents d
        left join lateral (
          select a.id,a.attempt_no,a.provider_call_state,a.outcome,
                 a.completed_at,a.provider_error_code
          from notification_delivery_attempts a
          where a.intent_id=d.id and a.subscription_id is null
          order by a.attempt_no desc,a.id desc
          limit 1
        ) a on true
        where d.channel='EMAIL'
          and d.delivery_mode <> 'DIGEST'
          and d.state='CLAIMED'
          and d.claim_expires_at < now()
        order by d.claim_expires_at,d.id
        limit ?
        """,
        (rs, rowNum) ->
            new ExpiredEmailClaim(
                rs.getLong("intent_id"),
                rs.getString("claim_token"),
                rs.getObject("attempt_id", Long.class),
                rs.getObject("attempt_no", Integer.class),
                rs.getString("provider_call_state"),
                rs.getString("outcome"),
                instant(rs.getObject("completed_at", OffsetDateTime.class)),
                rs.getString("provider_error_code")),
        limit);
  }

  public List<ExpiredDigestClaim> expiredDigestClaims(int limit) {
    return jdbcTemplate.query(
        """
        select b.id as bucket_id,b.claim_token,
               a.intent_id,a.attempt_id,a.attempt_no,a.provider_call_state,a.outcome,
               a.completed_at,a.provider_error_code
        from notification_digest_buckets b
        left join lateral (
          select i.delivery_intent_id as intent_id,a.id as attempt_id,a.attempt_no,
                 a.provider_call_state,a.outcome,a.completed_at,a.provider_error_code
          from notification_digest_items i
          join notification_delivery_attempts a
            on a.intent_id=i.delivery_intent_id
           and a.subscription_id is null
          where i.digest_bucket_id=b.id
          order by a.attempt_no desc,a.id desc
          limit 1
        ) a on true
        where b.state='CLAIMED'
          and b.claim_expires_at < now()
        order by b.claim_expires_at,b.id
        limit ?
        """,
        (rs, rowNum) ->
            new ExpiredDigestClaim(
                rs.getLong("bucket_id"),
                rs.getString("claim_token"),
                rs.getObject("intent_id", Long.class),
                rs.getObject("attempt_id", Long.class),
                rs.getObject("attempt_no", Integer.class),
                rs.getString("provider_call_state"),
                rs.getString("outcome"),
                instant(rs.getObject("completed_at", OffsetDateTime.class)),
                rs.getString("provider_error_code")),
        limit);
  }

  public List<ExpiredPushClaim> expiredPushClaims(int limit) {
    return jdbcTemplate.query(
        """
        select p.id as push_attempt_id,p.intent_id,p.subscription_id,p.claim_token,p.attempt_no,
               a.id as attempt_id,a.provider_call_state,a.outcome,a.completed_at,
               a.provider_status_code,a.provider_error_code
        from notification_push_attempts p
        left join notification_delivery_attempts a
          on a.intent_id=p.intent_id
         and a.subscription_id=p.subscription_id
         and a.attempt_no=p.attempt_no
        where p.state='CLAIMED'
          and p.claim_expires_at < now()
        order by p.claim_expires_at,p.id
        limit ?
        """,
        (rs, rowNum) ->
            new ExpiredPushClaim(
                rs.getLong("push_attempt_id"),
                rs.getLong("intent_id"),
                rs.getLong("subscription_id"),
                rs.getString("claim_token"),
                rs.getInt("attempt_no"),
                rs.getObject("attempt_id", Long.class),
                rs.getString("provider_call_state"),
                rs.getString("outcome"),
                instant(rs.getObject("completed_at", OffsetDateTime.class)),
                rs.getObject("provider_status_code", Integer.class),
                rs.getString("provider_error_code")),
        limit);
  }

  @Transactional
  public boolean resetPushClaimForSafeRetry(long pushAttemptId, String claimToken) {
    return jdbcTemplate.update(
            """
            update notification_push_attempts
            set state='PENDING',next_attempt_at=now(),
                attempt_no=greatest(attempt_no-1,0),
                claim_token=null,claim_expires_at=null,
                last_error_code='PRE_PROVIDER_CRASH',updated_at=now()
            where id=? and claim_token=? and state='CLAIMED'
            """,
            pushAttemptId,
            claimToken)
        == 1;
  }

  @Transactional
  public void markPushIntentUnknown(long intentId, String code) {
    jdbcTemplate.update(
        """
        update notification_delivery_intents
        set state='UNKNOWN',claim_token=null,claim_expires_at=null,last_error_code=?,updated_at=now()
        where id=? and state not in ('SENT','SUPPRESSED','FAILED_PERMANENT','UNKNOWN','DEAD')
        """,
        code,
        intentId);
  }

  private OffsetDateTime utc(Instant value) {
    return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
  }

  private Instant instant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }

  public record ExpiredEmailClaim(
      long intentId,
      String claimToken,
      Long attemptId,
      Integer attemptNo,
      String providerCallState,
      String outcome,
      Instant completedAt,
      String errorCode) {}

  public record ExpiredDigestClaim(
      long bucketId,
      String claimToken,
      Long representativeIntentId,
      Long attemptId,
      Integer attemptNo,
      String providerCallState,
      String outcome,
      Instant completedAt,
      String errorCode) {}

  public record ExpiredPushClaim(
      long pushAttemptId,
      long intentId,
      long subscriptionId,
      String claimToken,
      int attemptNo,
      Long deliveryAttemptId,
      String providerCallState,
      String outcome,
      Instant completedAt,
      Integer providerStatusCode,
      String errorCode) {}
}
