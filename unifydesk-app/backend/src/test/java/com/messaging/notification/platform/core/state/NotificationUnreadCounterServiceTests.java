package com.messaging.notification.platform.core.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.sql.ResultSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class NotificationUnreadCounterServiceTests {

  @Test
  void permissionRevocationAndRegrantChangeVisibleCountWithoutHistoryScan() throws Exception {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    @SuppressWarnings({"rawtypes", "unchecked"})
    org.mockito.stubbing.Answer<Object> answer =
        invocation -> {
          String sql = invocation.getArgument(0);
          ResultSetExtractor extractor = invocation.getArgument(1);
          ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
          if (sql.contains("scope_kind='ACCOUNT'")) {
            org.mockito.Mockito.when(rs.next()).thenReturn(true, false);
            org.mockito.Mockito.when(rs.getLong(1)).thenReturn(2L);
          } else if (sql.contains("scope_kind='COMPANY'")) {
            org.mockito.Mockito.when(rs.next()).thenReturn(true, false);
            org.mockito.Mockito.when(rs.getLong(1)).thenReturn(1L);
          } else if (sql.contains("notification_unread_permission_counters")) {
            org.mockito.Mockito.when(rs.next()).thenReturn(true, true, false);
            org.mockito.Mockito.when(rs.getString("permission_key"))
                .thenReturn("email.read", "billing.read");
            org.mockito.Mockito.when(rs.getLong("unread_count")).thenReturn(4L, 5L);
          }
          return extractor.extractData(rs);
        };
    org.mockito.Mockito.when(
            jdbc.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
        .thenAnswer(answer);
    NotificationUnreadCounterService service = new NotificationUnreadCounterService(jdbc);

    NotificationUnreadCounterService.Counts granted =
        service.counts(7L, 22L, Set.of("email.read"));
    NotificationUnreadCounterService.Counts revoked =
        service.counts(7L, 22L, Set.of());
    NotificationUnreadCounterService.Counts regranted =
        service.counts(7L, 22L, Set.of("email.read"));

    assertThat(granted).isEqualTo(new NotificationUnreadCounterService.Counts(2, 5, 7));
    assertThat(revoked).isEqualTo(new NotificationUnreadCounterService.Counts(2, 1, 3));
    assertThat(regranted).isEqualTo(granted);
    verify(jdbc, never())
        .query(
            contains("from user_notifications"),
            any(ResultSetExtractor.class),
            any(Object[].class));
  }

  @Test
  void accountScopeDoesNotConsultPermissionBuckets() throws Exception {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    @SuppressWarnings({"rawtypes", "unchecked"})
    org.mockito.stubbing.Answer<Object> answer =
        invocation -> {
          ResultSetExtractor extractor = invocation.getArgument(1);
          ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
          org.mockito.Mockito.when(rs.next()).thenReturn(true, false);
          org.mockito.Mockito.when(rs.getLong(1)).thenReturn(3L);
          return extractor.extractData(rs);
        };
    org.mockito.Mockito.when(
            jdbc.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
        .thenAnswer(answer);
    NotificationUnreadCounterService service = new NotificationUnreadCounterService(jdbc);

    assertThat(service.counts(7L, null, Set.of()))
        .isEqualTo(new NotificationUnreadCounterService.Counts(3, 0, 3));

    verify(jdbc, never())
        .query(
            contains("notification_unread_permission_counters"),
            any(ResultSetExtractor.class),
            any(Object[].class));
  }

  @Test
  void permissionedAndUnpermissionedTransitionsMutateDifferentCounterBuckets() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    NotificationUnreadCounterService service = new NotificationUnreadCounterService(jdbc);

    service.increment(7L, 22L, null);
    service.decrement(7L, 22L, null);
    service.increment(7L, 22L, "email.read");
    service.decrement(7L, 22L, "email.read");

    verify(jdbc, atLeastOnce())
        .update(
            contains("notification_unread_counters"),
            any(Object[].class));
    verify(jdbc, atLeastOnce())
        .update(
            contains("notification_unread_permission_counters"),
            any(Object[].class));
  }
}
