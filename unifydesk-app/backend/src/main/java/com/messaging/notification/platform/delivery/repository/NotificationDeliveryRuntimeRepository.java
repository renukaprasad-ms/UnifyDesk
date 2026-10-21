package com.messaging.notification.platform.delivery.repository;

import com.messaging.common.id.IdGenerator;
import com.messaging.notification.platform.delivery.model.DeliveryModels.Channel;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryIntent;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryMode;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DigestBucket;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DigestItemState;
import com.messaging.notification.platform.delivery.model.DeliveryModels.EffectivePreference;
import com.messaging.notification.platform.delivery.model.DeliveryModels.PushAttempt;
import com.messaging.notification.platform.delivery.model.DeliveryModels.PushAttemptState;
import com.messaging.notification.platform.delivery.model.DeliveryModels.PushSubscription;
import com.messaging.notification.platform.delivery.model.DeliveryModels.QuietHours;
import com.messaging.notification.platform.delivery.model.DeliveryModels.RecipientSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class NotificationDeliveryRuntimeRepository {

  static final String PUSH_SUBSCRIPTION_INSERT_SQL =
      """
      insert into notification_push_subscriptions(
        id,user_id,endpoint_hash,endpoint_ciphertext,p256dh_ciphertext,auth_ciphertext,
        expiration_time,device_label,status,created_at,updated_at
      ) values (?,?,?,?,?,?,?,?, 'ACTIVE',now(),now())
      on conflict (endpoint_hash) do nothing
      returning *
      """;

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public List<DeliveryIntent> claimDueIntents(
      Channel channel, DeliveryMode excludedMode, int limit, Instant leaseUntil) {
    String token = UUID.randomUUID().toString();
    String sql =
        """
        with candidate as (
          select d0.id
          from notification_delivery_intents d0
          where d0.channel = ?
            and d0.delivery_mode <> ?
            and d0.not_before <= now()
            and d0.next_attempt_at <= now()
            and (
              d0.state in ('PENDING','DEFERRED','FAILED_RETRYABLE')
              or (
                d0.state = 'CLAIMED'
                and d0.claim_expires_at < now()
                and not exists (
                  select 1
                  from notification_delivery_attempts a
                  where a.intent_id=d0.id
                    and a.subscription_id is null
                    and a.started_at >= d0.updated_at
                    and a.provider_call_state in ('IN_FLIGHT','COMPLETED')
                )
              )
            )
          order by d0.next_attempt_at, d0.id
          for update skip locked
          limit ?
        )
        update notification_delivery_intents d
        set state='CLAIMED',
            claim_token=?,
            claim_expires_at=?,
            updated_at=now()
        from candidate
        where d.id=candidate.id
        returning d.*
        """;
    return jdbcTemplate.query(
        sql,
        this::mapIntent,
        channel.name(),
        excludedMode.name(),
        limit,
        token,
        OffsetDateTime.ofInstant(leaseUntil, ZoneOffset.UTC));
  }

  @Transactional
  public List<DeliveryIntent> findUnaggregatedDigestIntents(int limit) {
    String sql =
        """
        select d.*
        from notification_delivery_intents d
        where d.channel='EMAIL'
          and d.delivery_mode='DIGEST'
          and d.state in ('PENDING','DEFERRED','FAILED_RETRYABLE')
          and d.not_before <= now()
          and not exists (
            select 1
            from notification_digest_items i
            where i.delivery_intent_id=d.id
          )
        order by d.created_at,d.id
        limit ?
        """;
    return jdbcTemplate.query(sql, this::mapIntent, limit);
  }

  @Transactional
  public boolean addDigestContribution(
      DeliveryIntent intent, Instant eligibleAt, Instant contributionAt) {
    Long bucketId =
        jdbcTemplate.query(
            """
            select id
            from notification_digest_buckets
            where user_id=?
              and company_id=?
              and notification_type=?
              and state in ('OPEN','ELIGIBLE','CLAIMED')
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
          OffsetDateTime.ofInstant(contributionAt, ZoneOffset.UTC),
          OffsetDateTime.ofInstant(contributionAt, ZoneOffset.UTC),
          OffsetDateTime.ofInstant(eligibleAt, ZoneOffset.UTC),
          OffsetDateTime.ofInstant(eligibleAt, ZoneOffset.UTC));
    } else {
      jdbcTemplate.update(
          """
          update notification_digest_buckets
          set contribution_count=contribution_count+1,
              last_contribution_at=?,
              eligible_at=?,
              next_attempt_at=?,
              state='OPEN',
              claim_token=null,
              claim_expires_at=null,
              updated_at=now()
          where id=?
          """,
          OffsetDateTime.ofInstant(contributionAt, ZoneOffset.UTC),
          OffsetDateTime.ofInstant(eligibleAt, ZoneOffset.UTC),
          OffsetDateTime.ofInstant(eligibleAt, ZoneOffset.UTC),
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

  @Transactional
  public List<DigestBucket> claimDueDigestBuckets(int limit, Instant leaseUntil) {
    String token = UUID.randomUUID().toString();
    return jdbcTemplate.query(
        """
        with candidate as (
          select b0.id
          from notification_digest_buckets b0
          where b0.eligible_at <= now()
            and b0.next_attempt_at <= now()
            and (
              b0.state in ('OPEN','ELIGIBLE','FAILED_RETRYABLE')
              or (
                b0.state='CLAIMED'
                and b0.claim_expires_at < now()
                and not exists (
                  select 1
                  from notification_digest_items i
                  join notification_delivery_attempts a
                    on a.intent_id=i.delivery_intent_id
                   and a.subscription_id is null
                  where i.digest_bucket_id=b0.id
                    and a.started_at >= b0.updated_at
                    and a.provider_call_state in ('IN_FLIGHT','COMPLETED')
                )
              )
            )
          order by b0.next_attempt_at,b0.id
          for update skip locked
          limit ?
        )
        update notification_digest_buckets b
        set state='CLAIMED',claim_token=?,claim_expires_at=?,updated_at=now()
        from candidate
        where b.id=candidate.id
        returning b.*
        """,
        this::mapDigestBucket,
        limit,
        token,
        OffsetDateTime.ofInstant(leaseUntil, ZoneOffset.UTC));
  }

  public String companyBusinessEmail(Long companyId) {
    if (companyId == null) return null;
    return jdbcTemplate.query(
        "select business_email from companies where id=?",
        rs -> rs.next() ? rs.getString("business_email") : null,
        companyId);
  }

  public Optional<RecipientSnapshot> recipientSnapshot(long userId, Long companyId) {
    String sql =
        """
        select u.id,u.email,u.name,u.status,u.is_verified,
               c.id as company_id,c.name as company_name,c.timezone as company_timezone,
               c.status as company_status,cm.status as member_status
        from users u
        left join companies c on c.id=?
        left join company_members cm on cm.company_id=c.id and cm.user_id=u.id
        where u.id=?
        """;
    List<RecipientSnapshot> rows =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) -> {
              Long loadedCompanyId = rs.getObject("company_id", Long.class);
              String companyStatus = rs.getString("company_status");
              String memberStatus = rs.getString("member_status");
              return new RecipientSnapshot(
                  rs.getLong("id"),
                  rs.getString("email"),
                  rs.getString("name"),
                  "ACTIVE".equals(rs.getString("status")),
                  rs.getBoolean("is_verified"),
                  loadedCompanyId,
                  rs.getString("company_name"),
                  rs.getString("company_timezone"),
                  companyId == null || (loadedCompanyId != null && "ACTIVE".equals(companyStatus)),
                  companyId == null || (loadedCompanyId != null && "ACTIVE".equals(memberStatus)));
            },
            companyId,
            userId);
    return rows.stream().findFirst();
  }

  public EffectivePreference effectivePreference(
      long userId, Long companyId, String notificationType) {
    if (companyId != null) {
      List<EffectivePreference> company =
          jdbcTemplate.query(
              """
              select email_mode,browser_enabled
              from notification_preference_overrides
              where user_id=? and company_id=? and notification_type=?
              limit 1
              """,
              (rs, n) ->
                  new EffectivePreference(
                      rs.getString("email_mode"),
                      rs.getObject("browser_enabled", Boolean.class) == Boolean.TRUE),
              userId,
              companyId,
              notificationType);
      if (!company.isEmpty()) {
        return company.getFirst();
      }
    }
    List<EffectivePreference> account =
        jdbcTemplate.query(
            """
            select email_mode,browser_enabled
            from notification_preference_overrides
            where user_id=? and company_id is null and notification_type=?
            limit 1
            """,
            (rs, n) ->
                new EffectivePreference(
                    rs.getString("email_mode"),
                    rs.getObject("browser_enabled", Boolean.class) == Boolean.TRUE),
            userId,
            notificationType);
    if (!account.isEmpty()) {
      return account.getFirst();
    }
    return new EffectivePreference(null, false);
  }

  public QuietHours quietHours(long userId, Long companyId, String companyTimezone) {
    if (companyId != null) {
      List<QuietHours> company =
          jdbcTemplate.query(
              """
              select timezone,quiet_hours_enabled,quiet_start,quiet_end
              from notification_user_settings
              where user_id=? and company_id=?
              limit 1
              """,
              (rs, n) -> mapQuietHours(rs, companyTimezone),
              userId,
              companyId);
      if (!company.isEmpty()) {
        return company.getFirst();
      }
    }
    List<QuietHours> account =
        jdbcTemplate.query(
            """
            select timezone,quiet_hours_enabled,quiet_start,quiet_end
            from notification_user_settings
            where user_id=? and company_id is null
            limit 1
            """,
            (rs, n) -> mapQuietHours(rs, companyTimezone),
            userId);
    if (!account.isEmpty()) {
      return account.getFirst();
    }
    return new QuietHours(
        companyTimezone == null || companyTimezone.isBlank() ? "UTC" : companyTimezone,
        true,
        LocalTime.of(22, 0),
        LocalTime.of(8, 0));
  }

  public long authoritativeInboxUnreadCount(long companyId) {
    Long count =
        jdbcTemplate.queryForObject(
            """
            select coalesce(sum(unread_count),0)
            from inbox_conversations
            where company_id=? and unread_count > 0
            """,
            Long.class,
            companyId);
    return count == null ? 0L : count;
  }

  public DigestItemState digestItemState(long bucketId) {
    List<Long> ids =
        jdbcTemplate.queryForList(
            """
            select i.delivery_intent_id
            from notification_digest_items i
            where i.digest_bucket_id=?
            order by i.delivery_intent_id
            """,
            Long.class,
            bucketId);
    if (ids.isEmpty()) {
      return new DigestItemState(List.of(), false);
    }
    String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
    List<Object> args = new ArrayList<>(ids);
    Integer count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from user_notifications n
            join notification_delivery_intents d on d.notification_id=n.id
            where d.id in (%s)
              and n.read_at is null
              and n.seen_at is null
            """
                .formatted(placeholders),
            Integer.class,
            args.toArray());
    return new DigestItemState(ids, count != null && count > 0);
  }

  public Instant lastSuccessfulDigestAt(long userId, long companyId, String notificationType) {
    return jdbcTemplate.query(
        """
        select sent_at
        from notification_digest_buckets
        where user_id=? and company_id=? and notification_type=? and state='SENT'
        order by sent_at desc
        limit 1
        """,
        rs -> rs.next() ? instant(rs, "sent_at") : null,
        userId,
        companyId,
        notificationType);
  }

  public int acceptedOptionalEmailsSince(long userId, Instant since) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from notification_delivery_attempts a
            join notification_delivery_intents d on d.id=a.intent_id
            where d.user_id=?
              and a.channel='EMAIL'
              and a.outcome='ACCEPTED'
              and a.completed_at >= ?
              and d.notification_type <> 'COMPANY_CREATED'
            """,
            Integer.class,
            userId,
            OffsetDateTime.ofInstant(since, ZoneOffset.UTC));
    return count == null ? 0 : count;
  }

  public List<Long> digestIntentIds(long bucketId) {
    return jdbcTemplate.queryForList(
        "select delivery_intent_id from notification_digest_items where digest_bucket_id=?",
        Long.class,
        bucketId);
  }

  @Transactional
  public void markIntentSent(long intentId, String claimToken, Instant at) {
    jdbcTemplate.update(
        """
        update notification_delivery_intents
        set state='SENT',sent_at=?,claim_token=null,claim_expires_at=null,last_error_code=null,
            updated_at=now()
        where id=? and claim_token=? and state='CLAIMED'
        """,
        OffsetDateTime.ofInstant(at, ZoneOffset.UTC),
        intentId,
        claimToken);
  }

  @Transactional
  public void markIntentSuppressed(long intentId, String claimToken, String code, Instant at) {
    jdbcTemplate.update(
        """
        update notification_delivery_intents
        set state='SUPPRESSED',suppressed_at=?,claim_token=null,claim_expires_at=null,
            last_error_code=?,updated_at=now()
        where id=? and claim_token=? and state='CLAIMED'
        """,
        OffsetDateTime.ofInstant(at, ZoneOffset.UTC),
        code,
        intentId,
        claimToken);
  }

  @Transactional
  public void markIntentRetry(
      long intentId, String claimToken, String code, int attemptCount, Instant nextAttemptAt) {
    jdbcTemplate.update(
        """
        update notification_delivery_intents
        set state='FAILED_RETRYABLE',attempt_count=?,next_attempt_at=?,
            claim_token=null,claim_expires_at=null,last_error_code=?,updated_at=now()
        where id=? and claim_token=? and state='CLAIMED'
        """,
        attemptCount,
        OffsetDateTime.ofInstant(nextAttemptAt, ZoneOffset.UTC),
        code,
        intentId,
        claimToken);
  }

  @Transactional
  public void markIntentTerminal(
      long intentId, String claimToken, String state, String code, int attemptCount) {
    jdbcTemplate.update(
        """
        update notification_delivery_intents
        set state=?,attempt_count=?,claim_token=null,claim_expires_at=null,last_error_code=?,
            updated_at=now()
        where id=? and claim_token=? and state='CLAIMED'
        """,
        state,
        attemptCount,
        code,
        intentId,
        claimToken);
  }

  @Transactional
  public void deferIntent(long intentId, String claimToken, String code, Instant nextAttemptAt) {
    jdbcTemplate.update(
        """
        update notification_delivery_intents
        set state='DEFERRED',next_attempt_at=?,claim_token=null,claim_expires_at=null,
            last_error_code=?,updated_at=now()
        where id=? and claim_token=? and state='CLAIMED'
        """,
        OffsetDateTime.ofInstant(nextAttemptAt, ZoneOffset.UTC),
        code,
        intentId,
        claimToken);
  }

  @Transactional
  public void markDigestSent(long bucketId, String claimToken, Instant at) {
    jdbcTemplate.update(
        """
        update notification_digest_buckets
        set state='SENT',sent_at=?,claim_token=null,claim_expires_at=null,last_error_code=null,
            updated_at=now()
        where id=? and claim_token=? and state='CLAIMED'
        """,
        OffsetDateTime.ofInstant(at, ZoneOffset.UTC),
        bucketId,
        claimToken);
    jdbcTemplate.update(
        """
        update notification_delivery_intents d
        set state='SENT',sent_at=?,claim_token=null,claim_expires_at=null,last_error_code=null,
            updated_at=now()
        where d.id in (
          select delivery_intent_id from notification_digest_items where digest_bucket_id=?
        )
          and d.state not in ('SENT','SUPPRESSED','FAILED_PERMANENT','UNKNOWN','DEAD')
        """,
        OffsetDateTime.ofInstant(at, ZoneOffset.UTC),
        bucketId);
  }

  @Transactional
  public void markDigestSuppressed(long bucketId, String claimToken, String code, Instant at) {
    jdbcTemplate.update(
        """
        update notification_digest_buckets
        set state='SUPPRESSED',suppressed_at=?,claim_token=null,claim_expires_at=null,
            last_error_code=?,updated_at=now()
        where id=? and claim_token=? and state='CLAIMED'
        """,
        OffsetDateTime.ofInstant(at, ZoneOffset.UTC),
        code,
        bucketId,
        claimToken);
    jdbcTemplate.update(
        """
        update notification_delivery_intents d
        set state='SUPPRESSED',suppressed_at=?,last_error_code=?,updated_at=now()
        where d.id in (
          select delivery_intent_id from notification_digest_items where digest_bucket_id=?
        )
          and d.state not in ('SENT','SUPPRESSED','FAILED_PERMANENT','UNKNOWN','DEAD')
        """,
        OffsetDateTime.ofInstant(at, ZoneOffset.UTC),
        code,
        bucketId);
  }

  @Transactional
  public void deferDigest(long bucketId, String claimToken, String code, Instant nextAttemptAt) {
    jdbcTemplate.update(
        """
        update notification_digest_buckets
        set state='OPEN',eligible_at=?,next_attempt_at=?,claim_token=null,claim_expires_at=null,
            last_error_code=?,updated_at=now()
        where id=? and claim_token=? and state='CLAIMED'
        """,
        OffsetDateTime.ofInstant(nextAttemptAt, ZoneOffset.UTC),
        OffsetDateTime.ofInstant(nextAttemptAt, ZoneOffset.UTC),
        code,
        bucketId,
        claimToken);
  }

  @Transactional
  public void retryDigest(long bucketId, String claimToken, String code, Instant nextAttemptAt) {
    jdbcTemplate.update(
        """
        update notification_digest_buckets
        set state='FAILED_RETRYABLE',next_attempt_at=?,claim_token=null,claim_expires_at=null,
            last_error_code=?,updated_at=now()
        where id=? and claim_token=? and state='CLAIMED'
        """,
        OffsetDateTime.ofInstant(nextAttemptAt, ZoneOffset.UTC),
        code,
        bucketId,
        claimToken);
  }

  @Transactional
  public void terminalDigest(long bucketId, String claimToken, String state, String code) {
    jdbcTemplate.update(
        """
        update notification_digest_buckets
        set state=?,claim_token=null,claim_expires_at=null,last_error_code=?,updated_at=now()
        where id=? and claim_token=? and state='CLAIMED'
        """,
        state,
        code,
        bucketId,
        claimToken);
  }

  public Optional<PushSubscription> findSubscriptionByEndpointHash(String endpointHash) {
    List<PushSubscription> rows =
        jdbcTemplate.query(
            "select * from notification_push_subscriptions where endpoint_hash=?",
            this::mapSubscription,
            endpointHash);
    return rows.stream().findFirst();
  }

  public Optional<PushSubscription> findSubscriptionForUser(long id, long userId) {
    List<PushSubscription> rows =
        jdbcTemplate.query(
            "select * from notification_push_subscriptions where id=? and user_id=?",
            this::mapSubscription,
            id,
            userId);
    return rows.stream().findFirst();
  }

  public List<PushSubscription> activeSubscriptions(long userId) {
    return jdbcTemplate.query(
        """
        select *
        from notification_push_subscriptions
        where user_id=? and status='ACTIVE'
          and (expiration_time is null or expiration_time > now())
        order by created_at desc
        """,
        this::mapSubscription,
        userId);
  }

  public List<PushSubscription> subscriptionsForUser(long userId) {
    return jdbcTemplate.query(
        """
        select *
        from notification_push_subscriptions
        where user_id=?
        order by created_at desc
        """,
        this::mapSubscription,
        userId);
  }

  public void lockUserForPushRegistration(long userId) {
    jdbcTemplate.queryForObject("select id from users where id=? for update", Long.class, userId);
  }

  public int activeSubscriptionCount(long userId) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from notification_push_subscriptions
            where user_id=?
              and status='ACTIVE'
              and (expiration_time is null or expiration_time > now())
            """,
            Integer.class,
            userId);
    return count == null ? 0 : count;
  }

  @Transactional
  public int expireSubscriptionsForUser(long userId, int limit) {
    return jdbcTemplate.update(
        """
        update notification_push_subscriptions
        set status='EXPIRED',last_failure_at=now(),updated_at=now()
        where id in (
          select id
          from notification_push_subscriptions
          where user_id=?
            and status='ACTIVE'
            and expiration_time is not null
            and expiration_time <= now()
          order by expiration_time,id
          limit ?
        )
        """,
        userId,
        Math.max(1, limit));
  }

  @Transactional
  public Optional<PushSubscription> insertSubscriptionIfEndpointAvailable(
      long userId,
      String endpointHash,
      String endpointCiphertext,
      String p256dhCiphertext,
      String authCiphertext,
      Instant expirationTime,
      String deviceLabel) {
    long id = IdGenerator.nextId();
    List<PushSubscription> inserted =
        jdbcTemplate.query(
            PUSH_SUBSCRIPTION_INSERT_SQL,
            this::mapSubscription,
            id,
            userId,
            endpointHash,
            endpointCiphertext,
            p256dhCiphertext,
            authCiphertext,
            expirationTime == null
                ? null
                : OffsetDateTime.ofInstant(expirationTime, ZoneOffset.UTC),
            deviceLabel);
    return inserted.stream().findFirst();
  }

  @Transactional
  public PushSubscription refreshSubscription(
      long id,
      long userId,
      String endpointCiphertext,
      String p256dhCiphertext,
      String authCiphertext,
      Instant expirationTime,
      String deviceLabel) {
    jdbcTemplate.update(
        """
        update notification_push_subscriptions
        set endpoint_ciphertext=?,p256dh_ciphertext=?,auth_ciphertext=?,expiration_time=?,
            device_label=?,status='ACTIVE',revoked_at=null,updated_at=now()
        where id=? and user_id=?
        """,
        endpointCiphertext,
        p256dhCiphertext,
        authCiphertext,
        expirationTime == null ? null : OffsetDateTime.ofInstant(expirationTime, ZoneOffset.UTC),
        deviceLabel,
        id,
        userId);
    return findSubscriptionForUser(id, userId).orElseThrow();
  }

  @Transactional
  public boolean revokeSubscription(long id, long userId) {
    return jdbcTemplate.update(
            """
            update notification_push_subscriptions
            set status='REVOKED',revoked_at=coalesce(revoked_at,now()),updated_at=now()
            where id=? and user_id=?
            """,
            id,
            userId)
        > 0;
  }

  @Transactional
  public void markSubscriptionInvalid(long id, String status) {
    jdbcTemplate.update(
        """
        update notification_push_subscriptions
        set status=?,last_failure_at=now(),updated_at=now()
        where id=?
        """,
        status,
        id);
  }

  @Transactional
  public void markSubscriptionSuccess(long id) {
    jdbcTemplate.update(
        """
        update notification_push_subscriptions
        set last_success_at=now(),updated_at=now()
        where id=?
        """,
        id);
  }

  @Transactional
  public int expireSubscriptions(int limit) {
    return jdbcTemplate.update(
        """
        update notification_push_subscriptions
        set status='EXPIRED',last_failure_at=now(),updated_at=now()
        where id in (
          select id
          from notification_push_subscriptions
          where status='ACTIVE' and expiration_time is not null and expiration_time <= now()
          order by expiration_time,id
          limit ?
        )
        """,
        limit);
  }

  @Transactional
  public void ensurePushAttempts(long intentId, List<PushSubscription> subscriptions) {
    for (PushSubscription subscription : subscriptions) {
      jdbcTemplate.update(
          """
          insert into notification_push_attempts(
            id,intent_id,subscription_id,attempt_no,state,next_attempt_at,created_at,updated_at
          ) values (?,?,?,0,'PENDING',now(),now(),now())
          on conflict (intent_id,subscription_id) do nothing
          """,
          IdGenerator.nextId(),
          intentId,
          subscription.id());
    }
  }

  @Transactional
  public List<PushAttempt> claimDuePushAttempts(int limit, Instant leaseUntil) {
    String token = UUID.randomUUID().toString();
    return jdbcTemplate.query(
        """
        with candidate as (
          select p0.id
          from notification_push_attempts p0
          where p0.next_attempt_at <= now()
            and (
              p0.state in ('PENDING','FAILED_RETRYABLE')
              or (
                p0.state='CLAIMED'
                and p0.claim_expires_at < now()
                and not exists (
                  select 1
                  from notification_delivery_attempts a
                  where a.intent_id=p0.intent_id
                    and a.subscription_id=p0.subscription_id
                    and a.attempt_no=p0.attempt_no
                    and a.provider_call_state in ('IN_FLIGHT','COMPLETED')
                )
              )
            )
          order by p0.next_attempt_at,p0.id
          for update skip locked
          limit ?
        )
        update notification_push_attempts p
        set state='CLAIMED',
            claim_token=?,
            claim_expires_at=?,
            attempt_no=case when p.state='CLAIMED' then p.attempt_no else p.attempt_no+1 end,
            updated_at=now()
        from candidate
        where p.id=candidate.id
        returning p.*
        """,
        this::mapPushAttempt,
        limit,
        token,
        OffsetDateTime.ofInstant(leaseUntil, ZoneOffset.UTC));
  }

  public Optional<DeliveryIntent> findIntent(long intentId) {
    List<DeliveryIntent> rows =
        jdbcTemplate.query(
            "select * from notification_delivery_intents where id=?", this::mapIntent, intentId);
    return rows.stream().findFirst();
  }

  public Optional<PushSubscription> findSubscription(long id) {
    List<PushSubscription> rows =
        jdbcTemplate.query(
            "select * from notification_push_subscriptions where id=?", this::mapSubscription, id);
    return rows.stream().findFirst();
  }

  @Transactional
  public void markPushAttemptAccepted(long id, String claimToken, int status) {
    jdbcTemplate.update(
        """
        update notification_push_attempts
        set state='ACCEPTED',accepted_at=now(),last_http_status=?,last_error_code=null,
            claim_token=null,claim_expires_at=null,updated_at=now()
        where id=? and claim_token=? and state='CLAIMED'
        """,
        status,
        id,
        claimToken);
  }

  @Transactional
  public void markPushAttemptRetry(
      long id, String claimToken, Integer status, String code, Instant nextAttemptAt) {
    jdbcTemplate.update(
        """
        update notification_push_attempts
        set state='FAILED_RETRYABLE',next_attempt_at=?,last_http_status=?,last_error_code=?,
            claim_token=null,claim_expires_at=null,updated_at=now()
        where id=? and claim_token=? and state='CLAIMED'
        """,
        OffsetDateTime.ofInstant(nextAttemptAt, ZoneOffset.UTC),
        status,
        code,
        id,
        claimToken);
  }

  @Transactional
  public void markPushAttemptTerminal(
      long id, String claimToken, String state, Integer status, String code) {
    jdbcTemplate.update(
        """
        update notification_push_attempts
        set state=?,last_http_status=?,last_error_code=?,claim_token=null,claim_expires_at=null,
            updated_at=now()
        where id=? and claim_token=? and state='CLAIMED'
        """,
        state,
        status,
        code,
        id,
        claimToken);
  }

  public PushIntentSummary pushIntentSummary(long intentId) {
    return jdbcTemplate.query(
        """
        select count(*) as total,
               count(*) filter (where state='ACCEPTED') as accepted,
               count(*) filter (where state in ('PENDING','CLAIMED','FAILED_RETRYABLE')) as pending
        from notification_push_attempts
        where intent_id=?
        """,
        rs -> {
          rs.next();
          return new PushIntentSummary(
              rs.getInt("total"), rs.getInt("accepted"), rs.getInt("pending"));
        },
        intentId);
  }

  @Transactional
  public void finalizePushIntent(long intentId, boolean accepted, String code, Instant at) {
    if (accepted) {
      jdbcTemplate.update(
          """
          update notification_delivery_intents
          set state='SENT',sent_at=?,claim_token=null,claim_expires_at=null,last_error_code=null,
              updated_at=now()
          where id=? and state not in ('SENT','SUPPRESSED','FAILED_PERMANENT','UNKNOWN','DEAD')
          """,
          OffsetDateTime.ofInstant(at, ZoneOffset.UTC),
          intentId);
    } else {
      jdbcTemplate.update(
          """
          update notification_delivery_intents
          set state='FAILED_PERMANENT',claim_token=null,claim_expires_at=null,last_error_code=?,
              updated_at=now()
          where id=? and state not in ('SENT','SUPPRESSED','FAILED_PERMANENT','UNKNOWN','DEAD')
          """,
          code,
          intentId);
    }
  }

  private DeliveryIntent mapIntent(ResultSet rs, int rowNum) throws SQLException {
    return new DeliveryIntent(
        rs.getLong("id"),
        rs.getLong("notification_event_id"),
        rs.getObject("notification_id", Long.class),
        rs.getObject("company_id", Long.class),
        rs.getLong("user_id"),
        rs.getString("notification_type"),
        rs.getString("required_permission_key"),
        Channel.valueOf(rs.getString("channel")),
        DeliveryMode.valueOf(rs.getString("delivery_mode")),
        rs.getString("idempotency_key"),
        rs.getString("state"),
        instant(rs, "not_before"),
        instant(rs, "next_attempt_at"),
        rs.getInt("attempt_count"),
        rs.getString("claim_token"),
        instant(rs, "claim_expires_at"),
        instant(rs, "created_at"));
  }

  private DigestBucket mapDigestBucket(ResultSet rs, int rowNum) throws SQLException {
    return new DigestBucket(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getLong("company_id"),
        rs.getString("notification_type"),
        rs.getString("state"),
        rs.getInt("contribution_count"),
        instant(rs, "first_contribution_at"),
        instant(rs, "last_contribution_at"),
        instant(rs, "eligible_at"),
        instant(rs, "next_attempt_at"),
        rs.getString("claim_token"),
        instant(rs, "claim_expires_at"),
        instant(rs, "sent_at"),
        instant(rs, "suppressed_at"),
        rs.getString("last_error_code"));
  }

  private PushSubscription mapSubscription(ResultSet rs, int rowNum) throws SQLException {
    return new PushSubscription(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("endpoint_hash"),
        rs.getString("endpoint_ciphertext"),
        rs.getString("p256dh_ciphertext"),
        rs.getString("auth_ciphertext"),
        instant(rs, "expiration_time"),
        rs.getString("device_label"),
        rs.getString("status"),
        instant(rs, "created_at"),
        instant(rs, "last_success_at"),
        instant(rs, "last_failure_at"),
        instant(rs, "revoked_at"),
        instant(rs, "updated_at"));
  }

  private PushAttempt mapPushAttempt(ResultSet rs, int rowNum) throws SQLException {
    return new PushAttempt(
        rs.getLong("id"),
        rs.getLong("intent_id"),
        rs.getLong("subscription_id"),
        rs.getInt("attempt_no"),
        PushAttemptState.valueOf(rs.getString("state")),
        instant(rs, "next_attempt_at"),
        rs.getString("claim_token"),
        instant(rs, "claim_expires_at"),
        rs.getObject("last_http_status", Integer.class),
        rs.getString("last_error_code"),
        instant(rs, "accepted_at"));
  }

  private QuietHours mapQuietHours(ResultSet rs, String companyTimezone) throws SQLException {
    String timezone = rs.getString("timezone");
    Time start = rs.getTime("quiet_start");
    Time end = rs.getTime("quiet_end");
    return new QuietHours(
        timezone == null || timezone.isBlank()
            ? (companyTimezone == null || companyTimezone.isBlank() ? "UTC" : companyTimezone)
            : timezone,
        rs.getBoolean("quiet_hours_enabled"),
        start == null ? LocalTime.of(22, 0) : start.toLocalTime(),
        end == null ? LocalTime.of(8, 0) : end.toLocalTime());
  }

  private Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  public record PushIntentSummary(int total, int accepted, int pending) {}
}
