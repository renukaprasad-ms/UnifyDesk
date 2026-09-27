package com.messaging.notification.platform.delivery.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class NotificationDeliveryCleanupRepository {

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public int revokeUserPushSubscriptionsBatch(long userId, int limit) {
    return jdbcTemplate.update(
        """
        update notification_push_subscriptions
        set status='REVOKED',revoked_at=coalesce(revoked_at,now()),updated_at=now()
        where id in (
          select id
          from notification_push_subscriptions
          where user_id=? and status='ACTIVE'
          order by id
          limit ?
        )
        """,
        userId,
        bounded(limit));
  }

  @Transactional
  public int deleteUserDeliveryIntentsBatch(long userId, int limit) {
    return jdbcTemplate.update(
        """
        delete from notification_delivery_intents
        where id in (
          select id
          from notification_delivery_intents
          where user_id=?
          order by id
          limit ?
        )
        """,
        userId,
        bounded(limit));
  }

  @Transactional
  public int deleteUserDigestBucketsBatch(long userId, int limit) {
    return jdbcTemplate.update(
        """
        delete from notification_digest_buckets
        where id in (
          select id
          from notification_digest_buckets
          where user_id=?
          order by id
          limit ?
        )
        """,
        userId,
        bounded(limit));
  }

  @Transactional
  public int deleteUserPushSubscriptionsBatch(long userId, int limit) {
    return jdbcTemplate.update(
        """
        delete from notification_push_subscriptions
        where id in (
          select id
          from notification_push_subscriptions
          where user_id=? and status <> 'ACTIVE'
          order by id
          limit ?
        )
        """,
        userId,
        bounded(limit));
  }

  public boolean hasUserDeliveryData(long userId) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            """
            select
              exists(select 1 from notification_delivery_intents where user_id=?)
              or exists(select 1 from notification_digest_buckets where user_id=?)
              or exists(select 1 from notification_push_subscriptions where user_id=?)
            """,
            Boolean.class,
            userId,
            userId,
            userId);
    return Boolean.TRUE.equals(exists);
  }

  @Transactional
  public int deleteCompanyDeliveryIntentsBatch(long companyId, int limit) {
    return jdbcTemplate.update(
        """
        delete from notification_delivery_intents
        where id in (
          select id
          from notification_delivery_intents
          where company_id=?
          order by id
          limit ?
        )
        """,
        companyId,
        bounded(limit));
  }

  @Transactional
  public int deleteCompanyDigestBucketsBatch(long companyId, int limit) {
    return jdbcTemplate.update(
        """
        delete from notification_digest_buckets
        where id in (
          select id
          from notification_digest_buckets
          where company_id=?
          order by id
          limit ?
        )
        """,
        companyId,
        bounded(limit));
  }

  public boolean hasCompanyDeliveryData(long companyId) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            """
            select
              exists(select 1 from notification_delivery_intents where company_id=?)
              or exists(select 1 from notification_digest_buckets where company_id=?)
            """,
            Boolean.class,
            companyId,
            companyId);
    return Boolean.TRUE.equals(exists);
  }

  @Transactional
  public int deleteRetainedDeliveryAttemptsBatch(
      Instant normalCutoff, Instant exceptionalCutoff, int limit) {
    return jdbcTemplate.update(
        """
        delete from notification_delivery_attempts a
        where a.id in (
          select a2.id
          from notification_delivery_attempts a2
          join notification_delivery_intents d on d.id=a2.intent_id
          where (
              (
                (a2.outcome='UNKNOWN' or d.state in ('UNKNOWN','DEAD'))
                and a2.created_at < ?
              )
              or
              (
                a2.outcome <> 'UNKNOWN'
                and d.state in ('SENT','SUPPRESSED','FAILED_PERMANENT')
                and a2.created_at < ?
              )
          )
          order by a2.created_at,a2.id
          limit ?
        )
        """,
        utc(exceptionalCutoff),
        utc(normalCutoff),
        bounded(limit));
  }

  @Transactional
  public int deleteRetainedPushAttemptsBatch(
      Instant normalCutoff, Instant exceptionalCutoff, int limit) {
    return jdbcTemplate.update(
        """
        delete from notification_push_attempts p
        where p.id in (
          select p2.id
          from notification_push_attempts p2
          join notification_delivery_intents d on d.id=p2.intent_id
          where (
              (
                (p2.state in ('UNKNOWN','DEAD') or d.state in ('UNKNOWN','DEAD'))
                and p2.updated_at < ?
              )
              or
              (
                p2.state in ('ACCEPTED','FAILED_PERMANENT')
                and d.state in ('SENT','SUPPRESSED','FAILED_PERMANENT')
                and p2.updated_at < ?
              )
          )
          order by p2.updated_at,p2.id
          limit ?
        )
        """,
        utc(exceptionalCutoff),
        utc(normalCutoff),
        bounded(limit));
  }

  @Transactional
  public int deleteRetainedDigestBucketsBatch(Instant cutoff, int limit) {
    return jdbcTemplate.update(
        """
        delete from notification_digest_buckets b
        where b.id in (
          select b2.id
          from notification_digest_buckets b2
          where b2.state in ('SENT','SUPPRESSED','DEAD','UNKNOWN')
            and b2.updated_at < ?
          order by b2.updated_at,b2.id
          limit ?
        )
        """,
        utc(cutoff),
        bounded(limit));
  }

  @Transactional
  public int deleteRetainedDeliveryIntentsBatch(Instant cutoff, int limit) {
    return jdbcTemplate.update(
        """
        delete from notification_delivery_intents d
        where d.id in (
          select d2.id
          from notification_delivery_intents d2
          where d2.state in ('SENT','SUPPRESSED','FAILED_PERMANENT','UNKNOWN','DEAD')
            and d2.updated_at < ?
          order by d2.updated_at,d2.id
          limit ?
        )
        """,
        utc(cutoff),
        bounded(limit));
  }

  @Transactional
  public int deleteRetainedPushSubscriptionsBatch(Instant cutoff, int limit) {
    return jdbcTemplate.update(
        """
        delete from notification_push_subscriptions s
        where s.id in (
          select s2.id
          from notification_push_subscriptions s2
          where s2.status in ('REVOKED','EXPIRED','INVALID')
            and s2.updated_at < ?
            and not exists (
              select 1
              from notification_push_attempts p
              where p.subscription_id=s2.id
                and p.state in ('PENDING','CLAIMED','FAILED_RETRYABLE')
            )
          order by s2.updated_at,s2.id
          limit ?
        )
        """,
        utc(cutoff),
        bounded(limit));
  }

  private int bounded(int limit) {
    return Math.max(1, limit);
  }

  private OffsetDateTime utc(Instant value) {
    return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
  }
}
