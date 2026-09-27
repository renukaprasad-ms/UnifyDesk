package com.messaging.notification.platform.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.company.service.CompanyIamPermissionService;
import com.messaging.notification.platform.core.config.NotificationPlatformProperties;
import com.messaging.notification.platform.core.realtime.NotificationRealtimePublisher;
import com.messaging.notification.platform.core.registry.NotificationTypeRegistry;
import com.messaging.notification.platform.core.state.NotificationUnreadCounterService;
import com.messaging.notification.platform.push.BrowserPushReadiness;
import com.messaging.security.service.CurrentUserService;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class NotificationApiPaginationTests {

  @Test
  void scansPastMoreThanFourPageSizesOfHiddenRowsWithoutIamNPlusOne() throws Exception {
    Fixture fixture = fixture();
    when(fixture.permissions.effectivePermissions(7L, 22L)).thenReturn(Set.of("email.read"));
    AtomicInteger window = new AtomicInteger();
    AtomicReference<String> firstSql = new AtomicReference<>();

    when(fixture.jdbc.query(anyString(), org.mockito.ArgumentMatchers.any(RowMapper.class), org.mockito.ArgumentMatchers.any(Object[].class)))
        .thenAnswer(
            invocation -> {
              String sql = invocation.getArgument(0);
              firstSql.compareAndSet(null, sql);
              @SuppressWarnings("unchecked")
              RowMapper<Object> mapper = invocation.getArgument(1);
              int current = window.getAndIncrement();
              List<Candidate> candidates = new ArrayList<>();
              if (current < 4) {
                long baseId = 1000L - current * 21L;
                for (int i = 0; i < 21; i++) {
                  candidates.add(
                      new Candidate(
                          baseId - i,
                          22L,
                          "billing.read",
                          Instant.parse("2026-09-27T07:00:00Z").minusSeconds(current * 60L + i)));
                }
              } else if (current == 4) {
                candidates.add(
                    new Candidate(
                        1L, null, null, Instant.parse("2026-09-27T06:00:00Z")));
              }
              List<Object> mapped = new ArrayList<>();
              int rowNum = 0;
              for (Candidate candidate : candidates) {
                mapped.add(mapper.mapRow(resultSet(candidate), rowNum++));
              }
              return mapped;
            });

    NotificationApiDtos.NotificationPage page =
        fixture.service.list("22", 20, null, false);

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().getFirst().id()).isEqualTo("1");
    assertThat(page.hasMore()).isFalse();
    assertThat(page.nextCursor()).isNull();
    assertThat(window.get()).isEqualTo(5);
    assertThat(firstSql.get()).contains("required_permission_key in (?)");
    verify(fixture.permissions, times(1)).effectivePermissions(7L, 22L);
    verify(fixture.permissions, never())
        .hasPermission(anyLong(), anyLong(), anyString());
  }

  @Test
  void hasMoreAlwaysReturnsCursorWhenAnAdditionalVisibleRowExists() throws Exception {
    Fixture fixture = fixture();
    when(fixture.permissions.effectivePermissions(7L, 22L)).thenReturn(Set.of("email.read"));

    when(fixture.jdbc.query(anyString(), org.mockito.ArgumentMatchers.any(RowMapper.class), org.mockito.ArgumentMatchers.any(Object[].class)))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              RowMapper<Object> mapper = invocation.getArgument(1);
              List<Object> mapped = new ArrayList<>();
              for (int i = 0; i < 21; i++) {
                Candidate candidate =
                    new Candidate(
                        200L - i,
                        null,
                        null,
                        Instant.parse("2026-09-27T07:00:00Z").minusSeconds(i));
                mapped.add(mapper.mapRow(resultSet(candidate), i));
              }
              return mapped;
            });

    NotificationApiDtos.NotificationPage page =
        fixture.service.list("22", 20, null, false);

    assertThat(page.items()).hasSize(20);
    assertThat(page.hasMore()).isTrue();
    assertThat(page.nextCursor()).isNotBlank();
  }

  private Fixture fixture() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    CurrentUserService currentUser = org.mockito.Mockito.mock(CurrentUserService.class);
    when(currentUser.currentUserId()).thenReturn(7L);
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(22L), eq(7L)))
        .thenReturn(true);
    CompanyIamPermissionService permissions =
        org.mockito.Mockito.mock(CompanyIamPermissionService.class);
    NotificationApiService service =
        new NotificationApiService(
            jdbc,
            currentUser,
            permissions,
            org.mockito.Mockito.mock(NotificationUnreadCounterService.class),
            org.mockito.Mockito.mock(NotificationRealtimePublisher.class),
            new NotificationTypeRegistry(),
            new NotificationCursorCodec(new ObjectMapper()),
            new NotificationPlatformProperties(),
            org.mockito.Mockito.mock(BrowserPushReadiness.class));
    return new Fixture(jdbc, permissions, service);
  }

  private ResultSet resultSet(Candidate candidate) throws Exception {
    ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
    when(rs.getLong("id")).thenReturn(candidate.id());
    when(rs.getObject("company_id")).thenReturn(candidate.companyId());
    when(rs.getString("notification_type")).thenReturn("INBOX_ACTIVITY");
    when(rs.getString("category")).thenReturn("INBOX");
    when(rs.getString("severity")).thenReturn("NORMAL");
    when(rs.getString("required_permission_key")).thenReturn(candidate.permissionKey());
    when(rs.getString("title")).thenReturn("New inbox activity");
    when(rs.getString("body")).thenReturn("1 new inbox message");
    when(rs.getInt("aggregate_count")).thenReturn(1);
    when(rs.getString("action_kind")).thenReturn("ROUTE");
    when(rs.getString("action_path")).thenReturn("/inbox/email?connectionId=33");
    when(rs.getTimestamp("first_event_at")).thenReturn(Timestamp.from(candidate.lastEventAt()));
    when(rs.getTimestamp("last_event_at")).thenReturn(Timestamp.from(candidate.lastEventAt()));
    when(rs.getTimestamp("seen_at")).thenReturn(null);
    when(rs.getTimestamp("read_at")).thenReturn(null);
    return rs;
  }

  private record Candidate(
      long id, Long companyId, String permissionKey, Instant lastEventAt) {}

  private record Fixture(
      JdbcTemplate jdbc,
      CompanyIamPermissionService permissions,
      NotificationApiService service) {}
}
