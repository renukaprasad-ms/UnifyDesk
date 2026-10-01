package com.messaging.notification.platform.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.company.service.CompanyIamPermissionService;
import com.messaging.notification.platform.core.api.NotificationApiDtos.ReadAllRequest;
import com.messaging.notification.platform.core.api.NotificationApiDtos.ReadStateRequest;
import com.messaging.notification.platform.core.config.NotificationPlatformProperties;
import com.messaging.notification.platform.core.realtime.NotificationRealtimePublisher;
import com.messaging.notification.platform.core.registry.NotificationTypeRegistry;
import com.messaging.notification.platform.core.state.NotificationUnreadCounterService;
import com.messaging.notification.platform.push.BrowserPushReadiness;
import com.messaging.security.service.CurrentUserService;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

class NotificationReadStateTests {

  @Test
  void markReadLocksRowAndDecrementsCounterOnlyOnUnreadToReadTransition() throws Exception {
    Fixture fixture = fixture();
    stubAddressedRows(fixture.jdbc, null, Instant.parse("2026-09-27T06:31:00Z"));
    when(fixture.jdbc.update(contains("read_at=now()"), any(Object[].class))).thenReturn(1);
    when(fixture.counters.counts(7L, null, Set.of()))
        .thenReturn(new NotificationUnreadCounterService.Counts(1, 0, 1));

    NotificationApiDtos.ReadStateResponse response =
        fixture.service.setReadState("55", new ReadStateRequest(null, true));

    assertThat(response.notification().readAt()).isNotNull();
    verify(fixture.counters).decrement(7L, null, null);
    verify(fixture.jdbc, atLeastOnce())
        .query(
            contains("for update"),
            any(ResultSetExtractor.class),
            any(Object[].class));
  }

  @Test
  void repeatedMarkReadDoesNotDoubleDecrementCounter() throws Exception {
    Fixture fixture = fixture();
    stubAddressedRows(
        fixture.jdbc,
        Instant.parse("2026-09-27T06:31:00Z"),
        Instant.parse("2026-09-27T06:31:00Z"));
    when(fixture.counters.counts(7L, null, Set.of()))
        .thenReturn(new NotificationUnreadCounterService.Counts(1, 0, 1));

    fixture.service.setReadState("55", new ReadStateRequest(null, true));

    verify(fixture.counters, never()).decrement(7L, null, null);
  }

  @Test
  void readAllCapsFutureBeforeAtRequestStartAndUsesBoundedLockedBatches() throws Exception {
    Fixture fixture = fixture();
    AtomicInteger batches = new AtomicInteger();
    @SuppressWarnings({"rawtypes", "unchecked"})
    org.mockito.stubbing.Answer<Object> queryAnswer =
        invocation -> {
          String sql = invocation.getArgument(0);
          RowMapper mapper = invocation.getArgument(1);
          if (sql.contains("from user_notifications") && batches.getAndIncrement() == 0) {
            ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
            stubRow(rs, null);
            return List.of(mapper.mapRow(rs, 0));
          }
          return List.of();
        };
    when(fixture.jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenAnswer(queryAnswer);
    when(fixture.jdbc.update(contains("read_at=now()"), any(Object[].class))).thenReturn(1);
    when(fixture.counters.counts(7L, null, Set.of()))
        .thenReturn(new NotificationUnreadCounterService.Counts(0, 0, 0));
    Instant beforeCall = Instant.now();

    NotificationApiDtos.ReadAllResponse response =
        fixture.service.readAll(new ReadAllRequest(null, beforeCall.plusSeconds(300)));

    assertThat(response.updated()).isEqualTo(1);
    assertThat(response.cutoffAt()).isBeforeOrEqualTo(Instant.now());
    assertThat(response.cutoffAt()).isAfterOrEqualTo(beforeCall);
    verify(fixture.counters).decrement(7L, null, null);
    verify(fixture.jdbc, atLeastOnce())
        .query(
            contains("last_event_at <= ?"),
            any(RowMapper.class),
            any(Object[].class));
  }

  private Fixture fixture() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    CurrentUserService currentUser = org.mockito.Mockito.mock(CurrentUserService.class);
    when(currentUser.currentUserId()).thenReturn(7L);
    NotificationUnreadCounterService counters =
        org.mockito.Mockito.mock(NotificationUnreadCounterService.class);
    NotificationRealtimePublisher realtime =
        org.mockito.Mockito.mock(NotificationRealtimePublisher.class);
    NotificationApiService service =
        new NotificationApiService(
            jdbc,
            currentUser,
            org.mockito.Mockito.mock(CompanyIamPermissionService.class),
            counters,
            realtime,
            new NotificationTypeRegistry(),
            new NotificationCursorCodec(new ObjectMapper()),
            new NotificationPlatformProperties(),
            org.mockito.Mockito.mock(BrowserPushReadiness.class));
    return new Fixture(jdbc, counters, service);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void stubAddressedRows(JdbcTemplate jdbc, Instant firstReadAt, Instant secondReadAt)
      throws Exception {
    AtomicInteger calls = new AtomicInteger();
    when(jdbc.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
        .thenAnswer(
            invocation -> {
              ResultSetExtractor extractor = invocation.getArgument(1);
              ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
              stubRow(rs, calls.getAndIncrement() == 0 ? firstReadAt : secondReadAt);
              return extractor.extractData(rs);
            });
  }

  private void stubRow(ResultSet rs, Instant readAt) throws Exception {
    when(rs.next()).thenReturn(true, false);
    when(rs.getLong("id")).thenReturn(55L);
    when(rs.getObject("company_id")).thenReturn(null);
    when(rs.getString("notification_type")).thenReturn("COMPANY_CREATED");
    when(rs.getString("category")).thenReturn("COMPANY");
    when(rs.getString("severity")).thenReturn("NORMAL");
    when(rs.getString("required_permission_key")).thenReturn(null);
    when(rs.getString("title")).thenReturn("Welcome to UnifyDesk");
    when(rs.getString("body")).thenReturn("Workspace ready");
    when(rs.getInt("aggregate_count")).thenReturn(1);
    when(rs.getString("action_kind")).thenReturn("ROUTE");
    when(rs.getString("action_path")).thenReturn("/workspace");
    when(rs.getTimestamp("first_event_at"))
        .thenReturn(Timestamp.from(Instant.parse("2026-09-27T06:30:00Z")));
    when(rs.getTimestamp("last_event_at"))
        .thenReturn(Timestamp.from(Instant.parse("2026-09-27T06:30:00Z")));
    when(rs.getTimestamp("seen_at")).thenReturn(null);
    when(rs.getTimestamp("read_at"))
        .thenReturn(readAt == null ? null : Timestamp.from(readAt));
  }

  private record Fixture(
      JdbcTemplate jdbc,
      NotificationUnreadCounterService counters,
      NotificationApiService service) {}
}
