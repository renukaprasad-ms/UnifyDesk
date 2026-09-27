package com.messaging.notification.platform.delivery.repository;

import com.messaging.common.id.IdGenerator;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryIntent;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DigestBucket;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class NotificationDeliverySupportRepository {

  private final JdbcTemplate jdbcTemplate;

  public Optional<DigestBucket> activeDigestBucket(
      long userId, long companyId, String notificationType) {
    List<DigestBucket> rows =
        jdbcTemplate.query(
            """
            select *
            from notification_digest_buckets
            where user_id=? and company_id=? and notification_type=?
              and state in ('OPEN','ELIGIBLE','CLAIMED')
            order by id
            limit 1
            """,
            (rs, rowNum) ->
                new DigestBucket(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getLong("company_id"),
                    rs.getString("notification_type"),
                    rs.getString("state"),
                    rs.getInt("contribution_count"),
                    instant(rs.getObject("first_contribution_at", OffsetDateTime.class)),
                    instant(rs.getObject("last_contribution_at", OffsetDateTime.class)),
                    instant(rs.getObject("eligible_at", OffsetDateTime.class)),
                    instant(rs.getObject("next_attempt_at", OffsetDateTime.class)),
                    rs.getString("claim_token"),
                    instant(rs.getObject("claim_expires_at", OffsetDateTime.class)),
                    instant(rs.getObject("sent_at", OffsetDateTime.class)),
                    instant(rs.getObject("suppressed_at", OffsetDateTime.class)),
                    rs.getString("last_error_code")),
            userId,
            companyId,
            notificationType);
    return rows.stream().findFirst();
  }

  @Transactional
  public boolean addDigestContributionSafely(
      DeliveryIntent intent, Instant eligibleAt, Instant contributionAt) {
    Long bucketId =
        jdbcTemplate.query(
            """
            select id
            from notification_digest_buckets
            where user_id=? and company_id=? and notification_type=?
              and state in ('OPEN','ELIGIBLE')
            order by id
            limit 1
            for update
            """,
            rs -> rs.next() ? rs.getLong(1) : null,
            intent.userId(),
            intent.companyId(),
            intent.notificationType());

    if (bucketId == null) {
      bucketId = IdGenerator.nextId();
      try {
        jdbcTemplate.update(
            """
            insert into notification_digest_buckets(
              id,user_id,company_id,notification_type,state,contribution_count,
              first_contribution_at,last_contribution_at,eligible_at,next_attempt_at,
              created_at,updated_at
            ) values (?,?,?,?, 'OPEN',1,?,?,?,?,now(),now())
            """,
            bucketId,
            intent.userId(),
            intent.companyId(),
            intent.notificationType(),
            utc(contributionAt),
            utc(contributionAt),
            utc(eligibleAt),
            utc(eligibleAt));
      } catch (DuplicateKeyException claimedOrRaced) {
        return false;
      }
    } else {
      jdbcTemplate.update(
          """
          update notification_digest_buckets
          set contribution_count=contribution_count+1,
              last_contribution_at=?,
              eligible_at=?,
              next_attempt_at=?,
              state='OPEN',
              updated_at=now()
          where id=? and state in ('OPEN','ELIGIBLE')
          """,
          utc(contributionAt),
          utc(eligibleAt),
          utc(eligibleAt),
          bucketId);
    }

    try {
      jdbcTemplate.update(
          """
          insert into notification_digest_items(digest_bucket_id,delivery_intent_id,created_at)
          values (?,?,now())
          """,
          bucketId,
          intent.id());
      return true;
    } catch (DuplicateKeyException duplicate) {
      return false;
    }
  }

  public String notificationGroupKey(Long notificationId) {
    if (notificationId == null) {
      return null;
    }
    return jdbcTemplate.query(
        "select group_key from user_notifications where id=?",
        rs -> rs.next() ? rs.getString(1) : null,
        notificationId);
  }

  public int nextEmailAttemptNo(long intentId) {
    Integer value =
        jdbcTemplate.queryForObject(
            """
            select coalesce(max(attempt_no),0)+1
            from notification_delivery_attempts
            where intent_id=? and subscription_id is null
            """,
            Integer.class,
            intentId);
    return value == null ? 1 : value;
  }

  @Transactional
  public long beginProviderAttempt(
      long intentId, Long subscriptionId, int attemptNo, String channel, Instant startedAt) {
    long id = IdGenerator.nextId();
    jdbcTemplate.update(
        """
        insert into notification_delivery_attempts(
          id,intent_id,subscription_id,attempt_no,channel,started_at,outcome,created_at
        ) values (?,?,?,?,?,?,'UNKNOWN',now())
        """,
        id,
        intentId,
        subscriptionId,
        attemptNo,
        channel,
        utc(startedAt));
    return id;
  }

  @Transactional
  public void completeProviderAttempt(
      long attemptId,
      Instant completedAt,
      String outcome,
      Integer providerStatusCode,
      String errorCode,
      long latencyMs) {
    jdbcTemplate.update(
        """
        update notification_delivery_attempts
        set completed_at=?,outcome=?,provider_status_code=?,provider_error_code=?,latency_ms=?
        where id=? and completed_at is null
        """,
        utc(completedAt),
        outcome,
        providerStatusCode,
        errorCode,
        Math.max(0, latencyMs),
        attemptId);
  }

  @Transactional
  public int recoverAmbiguousEmailIntentClaims() {
    return jdbcTemplate.update(
        """
        update notification_delivery_intents d
        set state='UNKNOWN',claim_token=null,claim_expires_at=null,
            last_error_code='AMBIGUOUS_PROVIDER_CRASH',updated_at=now()
        where d.channel='EMAIL'
          and d.state='CLAIMED'
          and d.claim_expires_at < now()
          and exists (
            select 1
            from notification_delivery_attempts a
            where a.intent_id=d.id
              and a.channel='EMAIL'
              and a.completed_at is null
          )
        """);
  }

  @Transactional
  public int recoverAmbiguousDigestClaims() {
    List<Long> bucketIds =
        jdbcTemplate.queryForList(
            """
            select distinct b.id
            from notification_digest_buckets b
            join notification_digest_items i on i.digest_bucket_id=b.id
            join notification_delivery_attempts a on a.intent_id=i.delivery_intent_id
            where b.state='CLAIMED'
              and b.claim_expires_at < now()
              and a.channel='EMAIL'
              and a.completed_at is null
            """,
            Long.class);
    for (Long bucketId : bucketIds) {
      jdbcTemplate.update(
          """
          update notification_digest_buckets
          set state='UNKNOWN',claim_token=null,claim_expires_at=null,
              last_error_code='AMBIGUOUS_PROVIDER_CRASH',updated_at=now()
          where id=? and state='CLAIMED'
          """,
          bucketId);
      jdbcTemplate.update(
          """
          update notification_delivery_intents d
          set state='UNKNOWN',claim_token=null,claim_expires_at=null,
              last_error_code='AMBIGUOUS_PROVIDER_CRASH',updated_at=now()
          where d.id in (
            select delivery_intent_id
            from notification_digest_items
            where digest_bucket_id=?
          )
            and d.state not in ('SENT','SUPPRESSED','FAILED_PERMANENT','UNKNOWN','DEAD')
          """,
          bucketId);
    }
    return bucketIds.size();
  }

  @Transactional
  public int recoverAmbiguousPushClaims() {
    List<Long> intentIds =
        jdbcTemplate.queryForList(
            """
            select distinct p.intent_id
            from notification_push_attempts p
            join notification_delivery_attempts a
              on a.intent_id=p.intent_id
             and a.subscription_id=p.subscription_id
             and a.attempt_no=p.attempt_no
            where p.state='CLAIMED'
              and p.claim_expires_at < now()
              and a.channel='WEB_PUSH'
              and a.completed_at is null
            """,
            Long.class);
    jdbcTemplate.update(
        """
        update notification_push_attempts p
        set state='UNKNOWN',claim_token=null,claim_expires_at=null,
            last_error_code='AMBIGUOUS_PROVIDER_CRASH',updated_at=now()
        where p.state='CLAIMED'
          and p.claim_expires_at < now()
          and exists (
            select 1
            from notification_delivery_attempts a
            where a.intent_id=p.intent_id
              and a.subscription_id=p.subscription_id
              and a.attempt_no=p.attempt_no
              and a.channel='WEB_PUSH'
              and a.completed_at is null
          )
        """);
    for (Long intentId : intentIds) {
      jdbcTemplate.update(
          """
          update notification_delivery_intents
          set state='UNKNOWN',claim_token=null,claim_expires_at=null,
              last_error_code='AMBIGUOUS_PROVIDER_CRASH',updated_at=now()
          where id=? and state not in ('SENT','SUPPRESSED','FAILED_PERMANENT','UNKNOWN','DEAD')
          """,
          intentId);
    }
    return intentIds.size();
  }

  @Transactional
  public void suppressUnclaimedIntent(long intentId, String code, Instant at) {
    jdbcTemplate.update(
        """
        update notification_delivery_intents
        set state='SUPPRESSED',suppressed_at=?,last_error_code=?,claim_token=null,
            claim_expires_at=null,updated_at=now()
        where id=? and state in ('PENDING','DEFERRED','FAILED_RETRYABLE')
        """,
        utc(at),
        code,
        intentId);
  }

  @Transactional
  public void releasePushIntent(long intentId, String claimToken, Instant nextCheckAt) {
    jdbcTemplate.update(
        """
        update notification_delivery_intents
        set state='DEFERRED',next_attempt_at=?,claim_token=null,claim_expires_at=null,
            last_error_code='PUSH_ATTEMPTS_ACTIVE',updated_at=now()
        where id=? and claim_token=? and state='CLAIMED'
        """,
        utc(nextCheckAt),
        intentId,
        claimToken);
  }

  @Transactional
  public void terminalDigestAndIntents(
      long bucketId, String claimToken, String state, String code, Instant at) {
    jdbcTemplate.update(
        """
        update notification_digest_buckets
        set state=?,claim_token=null,claim_expires_at=null,last_error_code=?,
            suppressed_at=case when ?='SUPPRESSED' then ? else suppressed_at end,
            updated_at=now()
        where id=? and claim_token=? and state='CLAIMED'
        """,
        state,
        code,
        state,
        utc(at),
        bucketId,
        claimToken);
    jdbcTemplate.update(
        """
        update notification_delivery_intents d
        set state=?,last_error_code=?,
            suppressed_at=case when ?='SUPPRESSED' then ? else suppressed_at end,
            claim_token=null,claim_expires_at=null,updated_at=now()
        where d.id in (
          select delivery_intent_id from notification_digest_items where digest_bucket_id=?
        )
          and d.state not in ('SENT','SUPPRESSED','FAILED_PERMANENT','UNKNOWN','DEAD')
        """,
        state,
        code,
        state,
        utc(at),
        bucketId);
  }

  private OffsetDateTime utc(Instant value) {
    return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
  }

  private Instant instant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }
}
