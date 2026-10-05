package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.common.exception.BadRequestException;
import com.messaging.company.entity.Company;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerSubscriptionCancellationServiceTests {

  private SubscriptionLifecycleService lifecycle;
  private CustomerSubscriptionCancellationService service;

  @BeforeEach
  void setUp() {
    lifecycle = mock(SubscriptionLifecycleService.class);
    service = new CustomerSubscriptionCancellationService(lifecycle);
  }

  @Test
  void openEndedFreeCustomerCancellationIsImmediateAndFinanciallySilent() {
    CompanySubscription free = subscription(BillingCycle.FREE, "0.00", null);
    when(lifecycle.current(10L)).thenReturn(free);
    CompanySubscription cancelled = subscription(BillingCycle.FREE, "0.00", null);
    cancelled.setStatus(SubscriptionStatus.CANCELLED);
    when(lifecycle.cancelImmediately(eq(10L), eq(99L), anyString(), any()))
        .thenReturn(cancelled);

    var response = service.cancel(10L, 99L, true);

    assertEquals(SubscriptionStatus.CANCELLED, response.status());
    verify(lifecycle).cancelImmediately(eq(10L), eq(99L), anyString(), any());
    verify(lifecycle, never()).scheduleCancellation(anyLong(), any());
  }

  @Test
  void paidCustomerImmediateCancellationRemainsForbidden() {
    CompanySubscription paid =
        subscription(
            BillingCycle.MONTHLY,
            "999.00",
            Instant.parse("2026-11-01T00:00:00Z"));
    when(lifecycle.current(10L)).thenReturn(paid);

    assertThrows(BadRequestException.class, () -> service.cancel(10L, 99L, false));

    verify(lifecycle, never()).cancelImmediately(anyLong(), any(), anyString(), any());
    verify(lifecycle, never()).scheduleCancellation(anyLong(), any());
  }

  @Test
  void paidCustomerPeriodEndCancellationIsUnchanged() {
    CompanySubscription paid =
        subscription(
            BillingCycle.MONTHLY,
            "999.00",
            Instant.parse("2026-11-01T00:00:00Z"));
    when(lifecycle.current(10L)).thenReturn(paid);
    when(lifecycle.scheduleCancellation(10L, 99L)).thenReturn(paid);

    service.cancel(10L, 99L, true);

    verify(lifecycle).scheduleCancellation(10L, 99L);
    verify(lifecycle, never()).cancelImmediately(anyLong(), any(), anyString(), any());
  }

  private CompanySubscription subscription(BillingCycle cycle, String amount, Instant end) {
    Company company = new Company();
    company.setId(10L);
    Plan plan = new Plan();
    plan.setId(20L);
    plan.setCode("PLAN");
    plan.setName("Plan");
    PlanPrice price = new PlanPrice();
    price.setId(21L);
    price.setPlan(plan);
    price.setBillingCycle(cycle);
    price.setAmount(new BigDecimal(amount));
    price.setCurrency("INR");

    CompanySubscription subscription = new CompanySubscription();
    subscription.setId(40L);
    subscription.setCompany(company);
    subscription.setPlan(plan);
    subscription.setPlanPrice(price);
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    subscription.setCurrentPeriodStart(Instant.parse("2026-10-01T00:00:00Z"));
    subscription.setCurrentPeriodEnd(end);
    return subscription;
  }
}
