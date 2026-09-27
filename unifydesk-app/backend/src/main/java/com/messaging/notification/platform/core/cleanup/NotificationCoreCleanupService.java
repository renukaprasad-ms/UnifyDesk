package com.messaging.notification.platform.core.cleanup;

import com.messaging.notification.platform.core.config.NotificationPlatformProperties;
import com.messaging.notification.platform.core.state.NotificationUnreadCounterService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationCoreCleanupService {

  private static final int MAX_BATCH_SIZE = 5000;

  private final JdbcTemplate jdbcTemplate;
  private final NotificationUnreadCounterService counterService;
  private final NotificationPlatformProperties properties;

  @Transactional
  public boolean purgeUserBatch(long userId, int requestedBatchSize) {
    int batchSize = batchSize(requestedBatchSize);

    if (!drain(
        """
        delete from user_notifications n
        where n.ctid in (
          select candidate.ctid
          from user_notifications candidate
          where candidate.user_id=?
            and not exists (
              select 1 from notification_delivery_intents i
              where i.notification_id=candidate.id
            )
          limit ?
        )
        """,
        userId,
        batchSize)) {
      return false;
    }
    if (!drain(
        """
        delete from notification_event_recipients
        where ctid in (
          select ctid from notification_event_recipients
          where user_id=? limit ?
        )
        """,
        userId,
        batchSize)) {
      return false;
    }
    if (!drain(
        """
        delete from notification_user_settings
        where ctid in (
          select ctid from notification_user_settings
          where user_id=? limit ?
        )
        """,
        userId,
        batchSize)) {
      return false;
    }
    if (!drain(
        """
        delete from notification_preference_overrides
        where ctid in (
          select ctid from notification_preference_overrides
          where user_id=? limit ?
        )
        """,
        userId,
        batchSize)) {
      return false;
    }
    if (!drain(
        """
        delete from notification_unread_permission_counters
        where ctid in (
          select ctid from notification_unread_permission_counters
          where user_id=? limit ?
        )
        """,
        userId,
        batchSize)) {
      return false;
    }
    if (!drain(
        """
        delete from notification_unread_counters
        where ctid in (
          select ctid from notification_unread_counters
          where user_id=? limit ?
        )
        """,
        userId,
        batchSize)) {
      return false;
    }
    if (!drain(
        """
        delete from notification_events e
        where e.ctid in (
          select candidate.ctid
          from notification_events candidate
          where candidate.recipient_user_id=?
            and not exists (
              select 1 from notification_delivery_intents i
              where i.notification_event_id=candidate.id
            )
            and not exists (
              select 1 from notification_event_recipients r
              where r.notification_event_id=candidate.id
            )
            and not exists (
              select 1 from notification_fanout_jobs j
              where j.event_id=candidate.id
            )
          limit ?
        )
        """,
        userId,
        batchSize)) {
      return false;
    }

    return !userCoreRowsRemain(userId);
  }

  @Transactional
  public boolean purgeCompanyBatch(long companyId, int requestedBatchSize) {
    int batchSize = batchSize(requestedBatchSize);

    if (!drain(
        """
        delete from user_notifications n
        where n.ctid in (
          select candidate.ctid
          from user_notifications candidate
          where candidate.company_id=?
            and not exists (
              select 1 from notification_delivery_intents i
              where i.notification_id=candidate.id
            )
          limit ?
        )
        """,
        companyId,
        batchSize)) {
      return false;
    }
    if (!drain(
        """
        delete from notification_event_recipients
        where ctid in (
          select ctid from notification_event_recipients
          where company_id=? limit ?
        )
        """,
        companyId,
        batchSize)) {
      return false;
    }
    if (!drain(
        """
        delete from notification_user_settings
        where ctid in (
          select ctid from notification_user_settings
          where company_id=? limit ?
        )
        """,
        companyId,
        batchSize)) {
      return false;
    }
    if (!drain(
        """
        delete from notification_preference_overrides
        where ctid in (
          select ctid from notification_preference_overrides
          where company_id=? limit ?
        )
        """,
        companyId,
        batchSize)) {
      return false;
    }
    if (!drain(
        """
        delete from notification_unread_permission_counters
        where ctid in (
          select ctid from notification_unread_permission_counters
          where company_id=? limit ?
        )
        """,
        companyId,
        batchSize)) {
      return false;
    }
    if (!drain(
        """
        delete from notification_unread_counters
        where ctid in (
          select ctid from notification_unread_counters
          where company_id=? limit ?
        )
        """,
        companyId,
        batchSize)) {
      return false;
    }
    if (!drain(
        """
        delete from notification_fanout_jobs
        where ctid in (
          select ctid from notification_fanout_jobs
          where company_id=? limit ?
        )
        """,
        companyId,
        batchSize)) {
      return false;
    }
    if (!drain(
        """
        delete from notification_events e
        where e.ctid in (
          select candidate.ctid
          from notification_events candidate
          where candidate.company_id=?
            and not exists (
              select 1 from notification_delivery_intents i
              where i.notification_event_id=candidate.id
            )
            and not exists (
              select 1 from notification_event_recipients r
              where r.notification_event_id=candidate.id
            )
            and not exists (
              select 1 from notification_fanout_jobs j
              where j.event_id=candidate.id
            )
          limit ?
        )
        """,
        companyId,
        batchSize)) {
      return false;
    }

    return !companyCoreRowsRemain(companyId);
  }

  @Transactional
  public int cleanupNotificationHistoryBatch(Instant now, int requestedBatchSize) {
    int batchSize = batchSize(requestedBatchSize);
    Instant retentionCutoff = now.minus(properties.getRetention().getNotifications());
    List<HistoryRow> rows =
        jdbcTemplate.query(
            """
            select n.id,n.user_id,n.company_id,n.required_permission_key,n.read_at
            from user_notifications n
            where (
                (n.expires_at is not null and n.expires_at <= ?)
                or n.last_event_at < ?
              )
              and not exists (
                select 1 from notification_delivery_intents i
                where i.notification_id=n.id
              )
            order by coalesce(n.expires_at,n.last_event_at),n.id
            for update skip locked
            limit ?
            """,
            (rs, rowNum) ->
                new HistoryRow(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    (Long) rs.getObject("company_id"),
                    rs.getString("required_permission_key"),
                    rs.getTimestamp("read_at") != null),
            utc(now),
            utc(retentionCutoff),
            batchSize);
    if (rows.isEmpty()) {
      return 0;
    }

    Map<CounterKey, Long> unreadDeltas = new LinkedHashMap<>();
    for (HistoryRow row : rows) {
      if (!row.read()) {
        CounterKey key =
            new CounterKey(row.userId(), row.companyId(), row.requiredPermissionKey());
        unreadDeltas.merge(key, 1L, Long::sum);
      }
    }

    int deleted = deleteNotificationIds(rows.stream().map(HistoryRow::id).toList());
    if (deleted > 0) {
      unreadDeltas.forEach(
          (key, count) ->
              counterService.adjust(
                  key.userId(), key.companyId(), key.requiredPermissionKey(), -count));
    }
    return deleted;
  }

  @Transactional
  public int cleanupEventDataBatch(Instant now, int requestedBatchSize) {
    int batchSize = batchSize(requestedBatchSize);
    Instant cutoff = now.minus(properties.getRetention().getEvents());

    int receipts =
        jdbcTemplate.update(
            """
            delete from notification_event_recipients r
            where r.ctid in (
              select r2.ctid
              from notification_event_recipients r2
              join notification_events e on e.id=r2.notification_event_id
              where (
                  e.materialized_at < ?
                  or (e.publish_state='DEAD' and e.updated_at < ?)
                )
                and not exists (
                  select 1 from notification_delivery_intents i
                  where i.notification_event_id=e.id
                )
              order by r2.notification_event_id,r2.id
              limit ?
            )
            """,
            utc(cutoff),
            utc(cutoff),
            batchSize);

    int fanout =
        jdbcTemplate.update(
            """
            delete from notification_fanout_jobs j
            where j.ctid in (
              select j2.ctid
              from notification_fanout_jobs j2
              join notification_events e on e.id=j2.event_id
              where (
                  e.materialized_at < ?
                  or (e.publish_state='DEAD' and e.updated_at < ?)
                )
                and j2.state in ('COMPLETED','DEAD')
                and not exists (
                  select 1 from notification_delivery_intents i
                  where i.notification_event_id=e.id
                )
              order by j2.id
              limit ?
            )
            """,
            utc(cutoff),
            utc(cutoff),
            batchSize);

    int events =
        jdbcTemplate.update(
            """
            delete from notification_events e
            where e.ctid in (
              select candidate.ctid
              from notification_events candidate
              where (
                  candidate.materialized_at < ?
                  or (candidate.publish_state='DEAD' and candidate.updated_at < ?)
                )
                and not exists (
                  select 1 from notification_delivery_intents i
                  where i.notification_event_id=candidate.id
                )
                and not exists (
                  select 1 from notification_event_recipients r
                  where r.notification_event_id=candidate.id
                )
                and not exists (
                  select 1 from notification_fanout_jobs j
                  where j.event_id=candidate.id
                )
              order by coalesce(candidate.materialized_at,candidate.updated_at),candidate.id
              limit ?
            )
            """,
            utc(cutoff),
            utc(cutoff),
            batchSize);

    return Math.addExact(Math.addExact(receipts, fanout), events);
  }

  private OffsetDateTime utc(Instant value) {
    return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
  }

  private boolean drain(String sql, long rootId, int batchSize) {
    return jdbcTemplate.update(sql, rootId, batchSize) < batchSize;
  }

  private boolean userCoreRowsRemain(long userId) {
    Boolean remains =
        jdbcTemplate.queryForObject(
            """
            select
              exists(select 1 from user_notifications where user_id=?)
              or exists(select 1 from notification_event_recipients where user_id=?)
              or exists(select 1 from notification_user_settings where user_id=?)
              or exists(select 1 from notification_preference_overrides where user_id=?)
              or exists(select 1 from notification_unread_permission_counters where user_id=?)
              or exists(select 1 from notification_unread_counters where user_id=?)
              or exists(select 1 from notification_events where recipient_user_id=?)
            """,
            Boolean.class,
            userId,
            userId,
            userId,
            userId,
            userId,
            userId,
            userId);
    return Boolean.TRUE.equals(remains);
  }

  private boolean companyCoreRowsRemain(long companyId) {
    Boolean remains =
        jdbcTemplate.queryForObject(
            """
            select
              exists(select 1 from user_notifications where company_id=?)
              or exists(select 1 from notification_event_recipients where company_id=?)
              or exists(select 1 from notification_user_settings where company_id=?)
              or exists(select 1 from notification_preference_overrides where company_id=?)
              or exists(select 1 from notification_unread_permission_counters where company_id=?)
              or exists(select 1 from notification_unread_counters where company_id=?)
              or exists(select 1 from notification_fanout_jobs where company_id=?)
              or exists(select 1 from notification_events where company_id=?)
            """,
            Boolean.class,
            companyId,
            companyId,
            companyId,
            companyId,
            companyId,
            companyId,
            companyId,
            companyId);
    return Boolean.TRUE.equals(remains);
  }

  private int deleteNotificationIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return 0;
    }
    List<Object> args = new ArrayList<>(ids);
    String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
    return jdbcTemplate.update(
        "delete from user_notifications where id in (" + placeholders + ")", args.toArray());
  }

  private int batchSize(int requestedBatchSize) {
    return Math.max(1, Math.min(MAX_BATCH_SIZE, requestedBatchSize));
  }

  private record HistoryRow(
      long id,
      long userId,
      Long companyId,
      String requiredPermissionKey,
      boolean read) {}

  private record CounterKey(
      long userId, Long companyId, String requiredPermissionKey) {}
}
