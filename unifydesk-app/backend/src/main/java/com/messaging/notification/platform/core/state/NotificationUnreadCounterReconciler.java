package com.messaging.notification.platform.core.state;

import com.messaging.common.id.IdGenerator;
import com.messaging.notification.platform.core.config.NotificationPlatformProperties;
import com.messaging.notification.platform.core.realtime.NotificationRealtimePublisher;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationUnreadCounterReconciler {

  private final JdbcTemplate jdbcTemplate;
  private final NotificationPlatformProperties properties;
  private final NotificationRealtimePublisher realtimePublisher;

  @Scheduled(
      fixedDelayString = "${app.notification-platform.core.reconciliation.interval-ms:900000}",
      initialDelayString = "${app.notification-platform.core.reconciliation.interval-ms:900000}")
  public void reconcile() {
    int batchSize = Math.max(1, properties.getCore().getReconciliation().getBatchSize());
    reconcileBaseCounters(batchSize);
    reconcilePermissionCounters(batchSize);
    createMissingBaseCounters(batchSize);
    createMissingPermissionCounters(batchSize);
  }

  private void reconcileBaseCounters(int batchSize) {
    for (CounterRow counter :
        jdbcTemplate.query(
            """
            select id,user_id,scope_kind,company_id,unread_count
            from notification_unread_counters
            order by updated_at asc,id
            limit ?
            """,
            (rs, rowNum) ->
                new CounterRow(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getString("scope_kind"),
                    (Long) rs.getObject("company_id"),
                    rs.getLong("unread_count")),
            batchSize)) {
      long actual = actualBaseUnread(counter.userId(), counter.companyId());
      if (actual != counter.unreadCount()) {
        jdbcTemplate.update(
            "update notification_unread_counters set unread_count=?,updated_at=now() where id=?",
            actual,
            counter.id());
        realtimePublisher.afterCommit(
            counter.userId(), counter.companyId(), null, "COUNTER_RECONCILED");
      }
    }
  }

  private void reconcilePermissionCounters(int batchSize) {
    for (PermissionCounterRow counter :
        jdbcTemplate.query(
            """
            select user_id,company_id,permission_key,unread_count
            from notification_unread_permission_counters
            order by updated_at asc,user_id,company_id,permission_key
            limit ?
            """,
            (rs, rowNum) ->
                new PermissionCounterRow(
                    rs.getLong("user_id"),
                    rs.getLong("company_id"),
                    rs.getString("permission_key"),
                    rs.getLong("unread_count")),
            batchSize)) {
      long actual =
          actualPermissionUnread(
              counter.userId(), counter.companyId(), counter.permissionKey());
      if (actual != counter.unreadCount()) {
        jdbcTemplate.update(
            """
            update notification_unread_permission_counters
            set unread_count=?,updated_at=now()
            where user_id=? and company_id=? and permission_key=?
            """,
            actual,
            counter.userId(),
            counter.companyId(),
            counter.permissionKey());
        realtimePublisher.afterCommit(
            counter.userId(), counter.companyId(), null, "COUNTER_RECONCILED");
      }
    }
  }

  private void createMissingBaseCounters(int batchSize) {
    List<MissingScope> missing =
        jdbcTemplate.query(
            """
            select n.user_id,n.company_id,count(*) as unread_count
            from user_notifications n
            left join notification_unread_counters c
              on c.user_id=n.user_id
             and (
               (n.company_id is null and c.scope_kind='ACCOUNT' and c.company_id is null)
               or
               (n.company_id is not null and c.scope_kind='COMPANY' and c.company_id=n.company_id)
             )
            where n.read_at is null
              and (n.expires_at is null or n.expires_at > now())
              and (n.company_id is null or n.required_permission_key is null)
              and c.id is null
            group by n.user_id,n.company_id
            order by n.user_id,n.company_id nulls first
            limit ?
            """,
            (rs, rowNum) ->
                new MissingScope(
                    rs.getLong("user_id"),
                    (Long) rs.getObject("company_id"),
                    rs.getLong("unread_count")),
            batchSize);
    for (MissingScope scope : missing) {
      if (scope.companyId() == null) {
        jdbcTemplate.update(
            """
            insert into notification_unread_counters(
              id,user_id,scope_kind,company_id,unread_count,updated_at)
            values (?,?,'ACCOUNT',null,?,now())
            on conflict (user_id) where scope_kind='ACCOUNT' do nothing
            """,
            IdGenerator.nextId(),
            scope.userId(),
            scope.unreadCount());
      } else {
        jdbcTemplate.update(
            """
            insert into notification_unread_counters(
              id,user_id,scope_kind,company_id,unread_count,updated_at)
            values (?,?,'COMPANY',?,?,now())
            on conflict (user_id,company_id) where scope_kind='COMPANY' do nothing
            """,
            IdGenerator.nextId(),
            scope.userId(),
            scope.companyId(),
            scope.unreadCount());
      }
      realtimePublisher.afterCommit(
          scope.userId(), scope.companyId(), null, "COUNTER_RECONCILED");
    }
  }

  private void createMissingPermissionCounters(int batchSize) {
    List<MissingPermissionScope> missing =
        jdbcTemplate.query(
            """
            select n.user_id,n.company_id,n.required_permission_key,count(*) as unread_count
            from user_notifications n
            left join notification_unread_permission_counters c
              on c.user_id=n.user_id
             and c.company_id=n.company_id
             and c.permission_key=n.required_permission_key
            where n.company_id is not null
              and n.required_permission_key is not null
              and n.read_at is null
              and (n.expires_at is null or n.expires_at > now())
              and c.user_id is null
            group by n.user_id,n.company_id,n.required_permission_key
            order by n.user_id,n.company_id,n.required_permission_key
            limit ?
            """,
            (rs, rowNum) ->
                new MissingPermissionScope(
                    rs.getLong("user_id"),
                    rs.getLong("company_id"),
                    rs.getString("required_permission_key"),
                    rs.getLong("unread_count")),
            batchSize);
    for (MissingPermissionScope scope : missing) {
      jdbcTemplate.update(
          """
          insert into notification_unread_permission_counters(
            user_id,company_id,permission_key,unread_count,updated_at)
          values (?,?,?,?,now())
          on conflict (user_id,company_id,permission_key) do nothing
          """,
          scope.userId(),
          scope.companyId(),
          scope.permissionKey(),
          scope.unreadCount());
      realtimePublisher.afterCommit(
          scope.userId(), scope.companyId(), null, "COUNTER_RECONCILED");
    }
  }

  private long actualBaseUnread(long userId, Long companyId) {
    Long value;
    if (companyId == null) {
      value =
          jdbcTemplate.queryForObject(
              """
              select count(*) from user_notifications
              where user_id=? and company_id is null and read_at is null
                and (expires_at is null or expires_at > now())
              """,
              Long.class,
              userId);
    } else {
      value =
          jdbcTemplate.queryForObject(
              """
              select count(*) from user_notifications
              where user_id=? and company_id=? and required_permission_key is null
                and read_at is null
                and (expires_at is null or expires_at > now())
              """,
              Long.class,
              userId,
              companyId);
    }
    return value == null ? 0 : value;
  }

  private long actualPermissionUnread(
      long userId, long companyId, String requiredPermissionKey) {
    Long value =
        jdbcTemplate.queryForObject(
            """
            select count(*) from user_notifications
            where user_id=? and company_id=? and required_permission_key=?
              and read_at is null
              and (expires_at is null or expires_at > now())
            """,
            Long.class,
            userId,
            companyId,
            requiredPermissionKey);
    return value == null ? 0 : value;
  }

  private record CounterRow(
      long id, long userId, String scopeKind, Long companyId, long unreadCount) {}

  private record PermissionCounterRow(
      long userId, long companyId, String permissionKey, long unreadCount) {}

  private record MissingScope(long userId, Long companyId, long unreadCount) {}

  private record MissingPermissionScope(
      long userId, long companyId, String permissionKey, long unreadCount) {}
}
