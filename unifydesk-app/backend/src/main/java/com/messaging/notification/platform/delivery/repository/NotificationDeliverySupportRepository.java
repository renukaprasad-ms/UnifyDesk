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
