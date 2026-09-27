package com.messaging.notification.platform.core.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.notification.platform.core.config.NotificationPlatformProperties;
import com.messaging.notification.platform.core.state.NotificationUnreadCounterService;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class NotificationCoreCleanupServiceTests {

  @Test
  void userPurgeStopsAfterOneFullBoundedBatch() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    NotificationCoreCleanupService service = service(jdbc);
    when(jdbc.update(
            contains("delete from user_notifications"),
            eq(7L),
            eq(25)))
        .thenReturn(25);

    boolean complete = service.purgeUserBatch(7L, 25);

    assertThat(complete).isFalse();
    verify(jdbc, never())
        .update(
            contains("delete from notification_user_settings"),
            eq(7L),
            eq(25));
  }

  @Test
  void repeatedUserPurgeIsIdempotentlyCompleteWhenCoreRowsAreGone() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class)))
        .thenReturn(false);
    NotificationCoreCleanupService service = service(jdbc);

    assertThat(service.purgeUserBatch(7L, 100)).isTrue();
    assertThat(service.purgeUserBatch(7L, 100)).isTrue();
  }

  @Test
  void companyPurgeIsBoundedAndDoesNotDeleteDeliveryOwnedRows() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    NotificationCoreCleanupService service = service(jdbc);
    when(jdbc.update(
            contains("delete from notification_event_recipients"),
            eq(22L),
            eq(40)))
        .thenReturn(40);

    assertThat(service.purgeCompanyBatch(22L, 40)).isFalse();

    verify(jdbc, never())
        .update(
            contains("delete from notification_delivery_intents"),
            eq(22L),
            eq(40));
  }

  @Test
  void historyRetentionDecrementsCorrectPermissionCounterAndIsIdempotent() throws Exception {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    NotificationUnreadCounterService counters =
        org.mockito.Mockito.mock(NotificationUnreadCounterService.class);
    NotificationPlatformProperties properties = new NotificationPlatformProperties();
    NotificationCoreCleanupService service =
        new NotificationCoreCleanupService(jdbc, counters, properties);
    Instant now = Instant.parse("2026-09-27T08:00:00Z");
    AtomicInteger calls = new AtomicInteger();
    AtomicReference<Object[]> firstArgs = new AtomicReference<>();

    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenAnswer(
            invocation -> {
              firstArgs.compareAndSet(null, invocation.getArguments());
              if (calls.getAndIncrement() > 0) {
                return List.of();
              }
              @SuppressWarnings("unchecked")
              RowMapper<Object> mapper = invocation.getArgument(1);
              ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
              when(rs.getLong("id")).thenReturn(55L);
              when(rs.getLong("user_id")).thenReturn(7L);
              when(rs.getObject("company_id")).thenReturn(22L);
              when(rs.getString("required_permission_key")).thenReturn("email.read");
              when(rs.getTimestamp("read_at")).thenReturn(null);
              return List.of(mapper.mapRow(rs, 0));
            });
    when(jdbc.update(
            contains("delete from user_notifications where id in"),
            any(Object[].class)))
        .thenReturn(1);

    assertThat(service.cleanupNotificationHistoryBatch(now, 10)).isEqualTo(1);
    assertThat(service.cleanupNotificationHistoryBatch(now, 10)).isZero();

    verify(counters).adjust(7L, 22L, "email.read", -1L);
    Object[] invocationArgs = firstArgs.get();
    Object[] sqlArgs = (Object[]) invocationArgs[2];
    assertThat(sqlArgs[0]).isEqualTo(now);
    assertThat(sqlArgs[1]).isEqualTo(now.minus(Duration.ofDays(180)));
    assertThat(sqlArgs[2]).isEqualTo(10);
  }

  @Test
  void eventRetentionIsBoundedAndWaitsForDeliveryReferences() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    NotificationCoreCleanupService service = service(jdbc);
    Instant now = Instant.parse("2026-09-27T08:00:00Z");

    service.cleanupEventDataBatch(now, 50);

    verify(jdbc)
        .update(
            contains("not exists (\n                  select 1 from notification_delivery_intents"),
            eq(now.minus(Duration.ofDays(90))),
            eq(now.minus(Duration.ofDays(90))),
            eq(50));
  }

  private NotificationCoreCleanupService service(JdbcTemplate jdbc) {
    return new NotificationCoreCleanupService(
        jdbc,
        org.mockito.Mockito.mock(NotificationUnreadCounterService.class),
        new NotificationPlatformProperties());
  }
}
