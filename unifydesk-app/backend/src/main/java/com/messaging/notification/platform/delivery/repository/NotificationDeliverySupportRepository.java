package com.messaging.notification.platform.delivery.repository;

import com.messaging.notification.platform.delivery.model.DeliveryModels.DigestBucket;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
  public void suppressUnclaimedIntent(long intentId, String code, Instant at) {
    jdbcTemplate.update(
        """
        update notification_delivery_intents
        set state='SUPPRESSED',suppressed_at=?,last_error_code=?,claim_token=null,
            claim_expires_at=null,updated_at=now()
        where id=? and state in ('PENDING','DEFERRED','FAILED_RETRYABLE')
        """,
        OffsetDateTime.ofInstant(at, ZoneOffset.UTC),
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
        OffsetDateTime.ofInstant(nextCheckAt, ZoneOffset.UTC),
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
        OffsetDateTime.ofInstant(at, ZoneOffset.UTC),
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
        OffsetDateTime.ofInstant(at, ZoneOffset.UTC),
        bucketId);
  }

  private Instant instant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }
}
