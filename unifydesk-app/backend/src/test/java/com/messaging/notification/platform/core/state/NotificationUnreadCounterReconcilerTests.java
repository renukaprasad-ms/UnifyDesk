package com.messaging.notification.platform.core.state;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.notification.platform.core.config.NotificationPlatformProperties;
import com.messaging.notification.platform.core.realtime.NotificationRealtimePublisher;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class NotificationUnreadCounterReconcilerTests {

  @Test
  void reconciliationSeparatesUnpermissionedAndPermissionScopedTruth() throws Exception {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    NotificationRealtimePublisher realtime =
        org.mockito.Mockito.mock(NotificationRealtimePublisher.class);
    NotificationUnreadCounterReconciler reconciler =
        new NotificationUnreadCounterReconciler(
            jdbc, new NotificationPlatformProperties(), realtime);

    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenAnswer(
            invocation -> {
              String sql = invocation.getArgument(0);
              @SuppressWarnings("unchecked")
              RowMapper<Object> mapper = invocation.getArgument(1);
              if (sql.contains("from notification_unread_counters")
                  && !sql.contains("left join")) {
                ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                when(rs.getLong("id")).thenReturn(1L);
                when(rs.getLong("user_id")).thenReturn(7L);
                when(rs.getString("scope_kind")).thenReturn("COMPANY");
                when(rs.getObject("company_id")).thenReturn(22L);
                when(rs.getLong("unread_count")).thenReturn(9L);
                return List.of(mapper.mapRow(rs, 0));
              }
              if (sql.contains("from notification_unread_permission_counters")
                  && !sql.contains("left join")) {
                ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                when(rs.getLong("user_id")).thenReturn(7L);
                when(rs.getLong("company_id")).thenReturn(22L);
                when(rs.getString("permission_key")).thenReturn("email.read");
                when(rs.getLong("unread_count")).thenReturn(8L);
                return List.of(mapper.mapRow(rs, 0));
              }
              return List.of();
            });

    when(jdbc.queryForObject(
            contains("required_permission_key is null"),
            eq(Long.class),
            eq(7L),
            eq(22L)))
        .thenReturn(2L);
    when(jdbc.queryForObject(
            contains("required_permission_key=?"),
            eq(Long.class),
            eq(7L),
            eq(22L),
            eq("email.read")))
        .thenReturn(3L);

    reconciler.reconcile();

    verify(jdbc)
        .update(
            contains("update notification_unread_counters"),
            eq(2L),
            eq(1L));
    verify(jdbc)
        .update(
            contains("update notification_unread_permission_counters"),
            eq(3L),
            eq(7L),
            eq(22L),
            eq("email.read"));
    verify(realtime, times(2)).afterCommit(7L, 22L, null, "COUNTER_RECONCILED");
  }
}
