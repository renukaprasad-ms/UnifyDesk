package com.messaging.managedemail.metering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ManagedEmailUsageReconcilerTests {

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void staleAcceptedReservationIsCommitted() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ManagedEmailUsageService usage = mock(ManagedEmailUsageService.class);
    ManagedEmailRuntimeProperties properties = new ManagedEmailRuntimeProperties();
    properties.setStaleReservationMinutes(30);
    ResultSet resultSet = reservationResult("ACCEPTED_BY_PROVIDER", "provider-1");
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenAnswer(
            invocation -> {
              RowMapper mapper = invocation.getArgument(1);
              return List.of(mapper.mapRow(resultSet, 0));
            });
    ManagedEmailUsageReconciler reconciler =
        new ManagedEmailUsageReconciler(jdbc, usage, properties);

    assertThat(reconciler.reconcileStale(10)).isEqualTo(1);

    verify(usage).commit(10L, 70L, 60L, 1, "provider-1");
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void staleUnknownReservationIsRetained() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ManagedEmailUsageService usage = mock(ManagedEmailUsageService.class);
    ManagedEmailRuntimeProperties properties = new ManagedEmailRuntimeProperties();
    ResultSet resultSet = reservationResult("UNKNOWN", null);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenAnswer(
            invocation -> {
              RowMapper mapper = invocation.getArgument(1);
              return List.of(mapper.mapRow(resultSet, 0));
            });
    ManagedEmailUsageReconciler reconciler =
        new ManagedEmailUsageReconciler(jdbc, usage, properties);

    assertThat(reconciler.reconcileStale(10)).isZero();

    verifyNoInteractions(usage);
  }

  private ResultSet reservationResult(String status, String providerMessageId) throws Exception {
    ResultSet resultSet = mock(ResultSet.class);
    when(resultSet.getLong("company_id")).thenReturn(10L);
    when(resultSet.getLong("recipient_id")).thenReturn(70L);
    when(resultSet.getLong("run_id")).thenReturn(60L);
    when(resultSet.getString("idempotency_key"))
        .thenReturn("managed-email:reserve:70:1");
    when(resultSet.getString("status")).thenReturn(status);
    when(resultSet.getString("provider_message_id")).thenReturn(providerMessageId);
    return resultSet;
  }
}
