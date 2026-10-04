package com.messaging.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagedFinancialExposureGuardTests {

  private final CompanySubscriptionRepository subscriptions =
      mock(CompanySubscriptionRepository.class);
  private final ManagedFinancialExposureGuard guard =
      new ManagedFinancialExposureGuard(subscriptions);

  @Test
  void activeSubscriptionAllowsNewManagedExposure() {
    latest(SubscriptionStatus.ACTIVE);

    assertThat(guard.isAllowed(10L)).isTrue();
    guard.assertAllowed(10L);
  }

  @Test
  void pastDueAndSuspendedBlockNewManagedExposure() {
    latest(SubscriptionStatus.PAST_DUE);
    assertBlocked();

    latest(SubscriptionStatus.SUSPENDED);
    assertBlocked();
  }

  @Test
  void trialingAndTerminalStatesFailClosedForR1ManagedPayg() {
    latest(SubscriptionStatus.TRIALING);
    assertBlocked();

    latest(SubscriptionStatus.CANCELLED);
    assertBlocked();

    latest(SubscriptionStatus.EXPIRED);
    assertBlocked();
  }

  @Test
  void missingSubscriptionFailsClosed() {
    when(subscriptions.findFirstByCompanyIdOrderByStartedAtDescIdDesc(10L))
        .thenReturn(Optional.empty());

    assertBlocked();
  }

  private void latest(SubscriptionStatus status) {
    CompanySubscription subscription = new CompanySubscription();
    subscription.setStatus(status);
    when(subscriptions.findFirstByCompanyIdOrderByStartedAtDescIdDesc(10L))
        .thenReturn(Optional.of(subscription));
  }

  private void assertBlocked() {
    assertThat(guard.isAllowed(10L)).isFalse();
    assertThatThrownBy(() -> guard.assertAllowed(10L))
        .isInstanceOf(PlanEntitlementException.class)
        .hasMessage(ManagedFinancialExposureGuard.BLOCKED);
  }
}
