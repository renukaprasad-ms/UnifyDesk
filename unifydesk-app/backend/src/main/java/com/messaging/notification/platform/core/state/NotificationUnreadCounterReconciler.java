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
    int batchSize = properties.getCore().getReconciliation().getBatchSize();
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
      long actual = actualUnread(counter.userId(), counter.companyId());
      if (actual != counter.unreadCount()) {
        jdbcTemplate.update(
            "update notification_unread_counters set unread_count=?,updated_at=now() where id=?",
            actual,
            counter.id());
        realtimePublisher.afterCommit(
            counter.userId(), counter.companyId(), null, "COUNTER_RECONCILED");
      }
    }
    createMissingCounters(batchSize);
  }

  private void createMissingCounters(int batchSize) {
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

  private long actualUnread(long userId, Long companyId) {
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
              where user_id=? and company_id=? and read_at is null
                and (expires_at is null or expires_at > now())
              """,
              Long.class,
              userId,
              companyId);
    }
    return value == null ? 0 : value;
  }

  private record CounterRow(
      long id, long userId, String scopeKind, Long companyId, long unreadCount) {}

  private record MissingScope(long userId, Long companyId, long unreadCount) {}
}
