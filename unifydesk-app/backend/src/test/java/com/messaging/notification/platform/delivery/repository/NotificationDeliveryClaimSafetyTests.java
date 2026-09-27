package com.messaging.notification.platform.delivery.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.notification.platform.delivery.model.DeliveryModels.Channel;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryIntent;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryMode;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DigestBucket;
import com.messaging.notification.platform.delivery.model.DeliveryModels.PushAttempt;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class NotificationDeliveryClaimSafetyTests {

  private static final Instant LEASE_UNTIL = Instant.parse("2026-09-27T08:02:00Z");

  @Test
  void emailClaimDoesNotRacePastInFlightOrCompletedCurrentProviderAttempt() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.query(
            anyString(),
            org.mockito.ArgumentMatchers.<RowMapper<DeliveryIntent>>any(),
            any(Object[].class)))
        .thenReturn(List.of());
    NotificationDeliveryRuntimeRepository repository =
        new NotificationDeliveryRuntimeRepository(jdbcTemplate);

    repository.claimDueIntents(Channel.EMAIL, DeliveryMode.DIGEST, 100, LEASE_UNTIL);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate)
        .query(
            sql.capture(),
            org.mockito.ArgumentMatchers.<RowMapper<DeliveryIntent>>any(),
            any(Object[].class));
    assertThat(sql.getValue())
        .contains("a.started_at >= d0.updated_at")
        .contains("a.provider_call_state in ('IN_FLIGHT','COMPLETED')");
  }

  @Test
  void digestClaimRequiresRecoveryForCurrentInFlightOrCompletedAttempt() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.query(
            anyString(),
            org.mockito.ArgumentMatchers.<RowMapper<DigestBucket>>any(),
            any(Object[].class)))
        .thenReturn(List.of());
    NotificationDeliveryRuntimeRepository repository =
        new NotificationDeliveryRuntimeRepository(jdbcTemplate);

    repository.claimDueDigestBuckets(100, LEASE_UNTIL);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate)
        .query(
            sql.capture(),
            org.mockito.ArgumentMatchers.<RowMapper<DigestBucket>>any(),
            any(Object[].class));
    assertThat(sql.getValue())
        .contains("a.started_at >= b0.updated_at")
        .contains("a.provider_call_state in ('IN_FLIGHT','COMPLETED')");
  }

  @Test
  void pushExpiredClaimKeepsAttemptNumberAndCannotSkipAmbiguousAttempt() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.query(
            anyString(),
            org.mockito.ArgumentMatchers.<RowMapper<PushAttempt>>any(),
            any(Object[].class)))
        .thenReturn(List.of());
    NotificationDeliveryRuntimeRepository repository =
        new NotificationDeliveryRuntimeRepository(jdbcTemplate);

    repository.claimDuePushAttempts(100, LEASE_UNTIL);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate)
        .query(
            sql.capture(),
            org.mockito.ArgumentMatchers.<RowMapper<PushAttempt>>any(),
            any(Object[].class));
    assertThat(sql.getValue())
        .contains("a.attempt_no=p0.attempt_no")
        .contains("a.provider_call_state in ('IN_FLIGHT','COMPLETED')")
        .contains("case when p.state='CLAIMED' then p.attempt_no else p.attempt_no+1 end");
  }
}
