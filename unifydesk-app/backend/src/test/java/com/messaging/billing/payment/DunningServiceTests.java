package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.service.SubscriptionLifecycleService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DunningServiceTests {

  private JdbcTemplate jdbc;
  private PaymentReconciliationService reconciliation;
  private RecurringChargeService recurringCharges;
  private SubscriptionLifecycleService lifecycle;
  private DunningService service;

  @BeforeEach
  void setUp() {
    jdbc = mock(JdbcTemplate.class);
    reconciliation = mock(PaymentReconciliationService.class);
    recurringCharges = mock(RecurringChargeService.class);
    lifecycle = mock(SubscriptionLifecycleService.class);
    CashfreeProperties properties = new CashfreeProperties();
    service = new DunningService(jdbc, reconciliation, recurringCharges, lifecycle, properties);
  }

  @Test
  void retryProgressionIsExactlyD0D2D5D7() {
    Instant d7 = Instant.parse("2026-10-17T00:00:00Z");

    assertEquals(DunningService.D2_FAILED, DunningService.nextStageAfterFailure(DunningService.DUE_FAILED));
    assertEquals(d7.minus(Duration.ofDays(2)),
        DunningService.nextBoundaryAfterFailure(DunningService.DUE_FAILED, d7));

    assertEquals(DunningService.D5_FAILED, DunningService.nextStageAfterFailure(DunningService.D2_FAILED));
    assertEquals(d7,
        DunningService.nextBoundaryAfterFailure(DunningService.D2_FAILED, d7));

    assertEquals(1, DunningService.attemptCountForStage(DunningService.DUE_FAILED));
    assertEquals(2, DunningService.attemptCountForStage(DunningService.D2_FAILED));
    assertEquals(3, DunningService.attemptCountForStage(DunningService.D5_FAILED));
  }

  @Test
  void ambiguousD2CommandIsReconciledWithoutLaunchingD5Early() {
    Instant grace = Instant.now().plus(Duration.ofDays(5));
    var job =
        new DunningService.Job(
            1L, 77L, 30L, 1, DunningService.DUE_FAILED, grace, "claim-1");

    when(jdbc.queryForList(contains("provider_order_id"), eq(String.class), eq(30L)))
        .thenReturn(List.of());
    when(jdbc.queryForObject(contains("select status"), eq(String.class), eq(30L)))
        .thenReturn("PENDING");
    when(jdbc.queryForList(contains("provider_command_id"), eq(Long.class), eq(30L)))
        .thenReturn(List.of(41L));
    doThrow(new RuntimeException("provider pending"))
        .when(recurringCharges)
        .reconcileAttempt(41L);

    service.processClaimed(job);

    verify(recurringCharges).reconcileAttempt(41L);
    verify(recurringCharges, never()).charge(anyLong(), any());
    verify(jdbc)
        .update(
            contains("dunning_stage"),
            eq(1),
            eq(DunningService.DUE_FAILED),
            any(),
            eq(1L),
            eq("claim-1"));
  }

  @Test
  void unresolvedAtD7ExhaustsGraceAndLetsB1SuspendDueSubscriptions() {
    var job =
        new DunningService.Job(
            1L,
            77L,
            30L,
            3,
            DunningService.D5_FAILED,
            Instant.now().minusSeconds(1),
            "claim-1");

    service.processClaimed(job);

    verify(lifecycle).applyDueLifecycle(any());
    verify(jdbc)
        .update(
            contains("set status=?"),
            eq("EXHAUSTED"),
            eq(3),
            eq(DunningService.GRACE_EXHAUSTED),
            isNull(),
            eq(1L),
            eq("claim-1"));
    verifyNoInteractions(recurringCharges);
  }
}
