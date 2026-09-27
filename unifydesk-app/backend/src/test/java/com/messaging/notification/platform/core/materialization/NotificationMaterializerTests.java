package com.messaging.notification.platform.core.materialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.company.service.CompanyIamPermissionService;
import com.messaging.notification.platform.core.materialization.NotificationMaterializer.MaterializationResult;
import com.messaging.notification.platform.core.realtime.NotificationRealtimePublisher;
import com.messaging.notification.platform.core.registry.NotificationTypeRegistry;
import com.messaging.notification.platform.core.state.NotificationUnreadCounterService;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class NotificationMaterializerTests {

  @Test
  void duplicateRecipientReceiptProducesNoSecondVisibleEffect() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    stubEventQuery(jdbc, "company.created", null, null, "{\"companyName\":\"Acme\"}");
    when(jdbc.update(
            contains("insert into notification_event_recipients"), any(Object[].class)))
        .thenReturn(0);
    NotificationUnreadCounterService counters =
        org.mockito.Mockito.mock(NotificationUnreadCounterService.class);
    NotificationRealtimePublisher realtime =
        org.mockito.Mockito.mock(NotificationRealtimePublisher.class);
    NotificationMaterializer materializer =
        materializer(jdbc, counters, realtime, true);

    MaterializationResult result = materializer.materialize(10L, 7L);

    assertThat(result).isEqualTo(MaterializationResult.DUPLICATE);
    verify(counters, never()).increment(anyLong(), any(), any());
    verify(realtime, never()).afterCommit(any(Long.class), any(), any(), anyString());
  }

  @Test
  void coalescedUniqueEventReopensReadBucketAndIncrementsCounterOnce() throws Exception {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    stubEventQuery(
        jdbc, "inbox.activity", "email.read", "inbox:33", "{\"connectionId\":\"33\"}");
    when(jdbc.update(
            contains("insert into notification_event_recipients"), any(Object[].class)))
        .thenReturn(1);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    when(jdbc.queryForObject(
            contains("from users"), eq(Boolean.class), any(Object[].class)))
        .thenReturn(true);
    when(jdbc.queryForObject(
            contains("from company_members"), eq(Boolean.class), any(Object[].class)))
        .thenReturn(true);

    @SuppressWarnings({"rawtypes", "unchecked"})
    org.mockito.stubbing.Answer<Object> queryAnswer =
        invocation -> {
          String sql = invocation.getArgument(0);
          ResultSetExtractor extractor = invocation.getArgument(1);
          ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
          if (sql.contains("from notification_events")) {
            stubEventResultSet(
                rs, "inbox.activity", "email.read", "inbox:33", "{\"connectionId\":\"33\"}");
          } else if (sql.contains("from user_notifications")) {
            when(rs.next()).thenReturn(true, false);
            when(rs.getLong("id")).thenReturn(55L);
            when(rs.getInt("aggregate_count")).thenReturn(3);
            when(rs.getTimestamp("read_at"))
                .thenReturn(Timestamp.from(Instant.parse("2026-09-27T06:20:00Z")));
          } else {
            when(rs.next()).thenReturn(false);
          }
          return extractor.extractData(rs);
        };
    when(jdbc.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
        .thenAnswer(queryAnswer);

    CompanyIamPermissionService permissions =
        org.mockito.Mockito.mock(CompanyIamPermissionService.class);
    when(permissions.hasPermission(7L, 22L, "email.read")).thenReturn(true);
    NotificationUnreadCounterService counters =
        org.mockito.Mockito.mock(NotificationUnreadCounterService.class);
    NotificationRealtimePublisher realtime =
        org.mockito.Mockito.mock(NotificationRealtimePublisher.class);
    NotificationMaterializer materializer =
        new NotificationMaterializer(
            jdbc,
            new ObjectMapper(),
            new NotificationTypeRegistry(),
            permissions,
            counters,
            realtime);

    MaterializationResult result = materializer.materialize(10L, 7L);

    assertThat(result).isEqualTo(MaterializationResult.COALESCED);
    verify(counters).increment(7L, 22L, "email.read");
    verify(realtime).afterCommit(7L, 22L, 55L, "COALESCED");
  }

  @Test
  void permissionRevokedBeforeMaterializationSuppressesRecipient() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    stubEventQuery(
        jdbc, "inbox.activity", "email.read", "inbox:33", "{\"connectionId\":\"33\"}");
    when(jdbc.update(
            contains("insert into notification_event_recipients"), any(Object[].class)))
        .thenReturn(1);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    when(jdbc.queryForObject(
            contains("from users"), eq(Boolean.class), any(Object[].class)))
        .thenReturn(true);
    when(jdbc.queryForObject(
            contains("from company_members"), eq(Boolean.class), any(Object[].class)))
        .thenReturn(true);
    CompanyIamPermissionService permissions =
        org.mockito.Mockito.mock(CompanyIamPermissionService.class);
    when(permissions.hasPermission(7L, 22L, "email.read")).thenReturn(false);
    NotificationUnreadCounterService counters =
        org.mockito.Mockito.mock(NotificationUnreadCounterService.class);
    NotificationRealtimePublisher realtime =
        org.mockito.Mockito.mock(NotificationRealtimePublisher.class);

    MaterializationResult result =
        new NotificationMaterializer(
                jdbc,
                new ObjectMapper(),
                new NotificationTypeRegistry(),
                permissions,
                counters,
                realtime)
            .materialize(10L, 7L);

    assertThat(result).isEqualTo(MaterializationResult.SUPPRESSED);
    verify(counters, never()).increment(anyLong(), any(), any());
    verify(realtime, never()).afterCommit(any(Long.class), any(), any(), anyString());
  }

  private NotificationMaterializer materializer(
      JdbcTemplate jdbc,
      NotificationUnreadCounterService counters,
      NotificationRealtimePublisher realtime,
      boolean permission) {
    CompanyIamPermissionService permissions =
        org.mockito.Mockito.mock(CompanyIamPermissionService.class);
    when(permissions.hasPermission(any(), any(), anyString())).thenReturn(permission);
    return new NotificationMaterializer(
        jdbc,
        new ObjectMapper(),
        new NotificationTypeRegistry(),
        permissions,
        counters,
        realtime);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void stubEventQuery(
      JdbcTemplate jdbc,
      String eventType,
      String permission,
      String groupKey,
      String variablesJson) {
    when(jdbc.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
        .thenAnswer(
            invocation -> {
              ResultSetExtractor extractor = invocation.getArgument(1);
              ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
              stubEventResultSet(rs, eventType, permission, groupKey, variablesJson);
              return extractor.extractData(rs);
            });
  }

  private void stubEventResultSet(
      ResultSet rs,
      String eventType,
      String permission,
      String groupKey,
      String variablesJson)
      throws Exception {
    when(rs.next()).thenReturn(true, false);
    when(rs.getLong("id")).thenReturn(10L);
    when(rs.getString("event_type")).thenReturn(eventType);
    when(rs.getInt("schema_version")).thenReturn(1);
    when(rs.getObject("company_id")).thenReturn(22L);
    when(rs.getString("required_permission_key")).thenReturn(permission);
    when(rs.getString("group_key")).thenReturn(groupKey);
    when(rs.getString(7)).thenReturn(variablesJson);
    when(rs.getTimestamp("occurred_at"))
        .thenReturn(Timestamp.from(Instant.parse("2026-09-27T06:30:00Z")));
  }
}
