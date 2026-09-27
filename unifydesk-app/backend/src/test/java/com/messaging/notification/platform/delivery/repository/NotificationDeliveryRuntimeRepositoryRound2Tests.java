package com.messaging.notification.platform.delivery.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.messaging.notification.platform.delivery.model.DeliveryModels.RecipientSnapshot;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class NotificationDeliveryRuntimeRepositoryRound2Tests {

  @Test
  void nonNullCompanyScopeFailsClosedWhenCompanyJoinIsMissing() throws Exception {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    ResultSet resultSet = mock(ResultSet.class);
    when(resultSet.getLong("id")).thenReturn(7L);
    when(resultSet.getString("email")).thenReturn("user@example.com");
    when(resultSet.getString("name")).thenReturn("User");
    when(resultSet.getString("status")).thenReturn("ACTIVE");
    when(resultSet.getBoolean("is_verified")).thenReturn(true);
    when(resultSet.getObject("company_id", Long.class)).thenReturn(null);
    when(resultSet.getString("company_status")).thenReturn(null);
    when(resultSet.getString("member_status")).thenReturn(null);

    when(jdbcTemplate.query(
            anyString(), org.mockito.ArgumentMatchers.<RowMapper<RecipientSnapshot>>any(), eq(9L), eq(7L)))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              RowMapper<RecipientSnapshot> mapper = invocation.getArgument(1);
              return List.of(mapper.mapRow(resultSet, 0));
            });

    NotificationDeliveryRuntimeRepository repository =
        new NotificationDeliveryRuntimeRepository(jdbcTemplate);

    RecipientSnapshot snapshot = repository.recipientSnapshot(7L, 9L).orElseThrow();

    assertThat(snapshot.companyId()).isNull();
    assertThat(snapshot.companyEligible()).isFalse();
    assertThat(snapshot.activeMember()).isFalse();
  }

  @Test
  void accountScopeDoesNotRequireCompanyOrMembership() throws Exception {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    ResultSet resultSet = mock(ResultSet.class);
    when(resultSet.getLong("id")).thenReturn(7L);
    when(resultSet.getString("email")).thenReturn("user@example.com");
    when(resultSet.getString("name")).thenReturn("User");
    when(resultSet.getString("status")).thenReturn("ACTIVE");
    when(resultSet.getBoolean("is_verified")).thenReturn(true);
    when(resultSet.getObject("company_id", Long.class)).thenReturn(null);

    when(jdbcTemplate.query(
            anyString(),
            org.mockito.ArgumentMatchers.<RowMapper<RecipientSnapshot>>any(),
            eq((Long) null),
            eq(7L)))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              RowMapper<RecipientSnapshot> mapper = invocation.getArgument(1);
              return List.of(mapper.mapRow(resultSet, 0));
            });

    NotificationDeliveryRuntimeRepository repository =
        new NotificationDeliveryRuntimeRepository(jdbcTemplate);

    RecipientSnapshot snapshot = repository.recipientSnapshot(7L, null).orElseThrow();

    assertThat(snapshot.companyEligible()).isTrue();
    assertThat(snapshot.activeMember()).isTrue();
  }
}
