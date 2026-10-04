package com.messaging.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.SubscriptionTransitionRepository;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ManagedFinancialExposureGuardTests {

  private final SubscriptionLifecycleService lifecycle = mock(SubscriptionLifecycleService.class);
  private final ManagedFinancialExposureGuard guard = new ManagedFinancialExposureGuard(lifecycle);

  @Test
  void activeSubscriptionAllowsNewManagedExposure() {
    current(SubscriptionStatus.ACTIVE);

    assertThat(guard.isAllowed(10L)).isTrue();
    guard.assertAllowed(10L);
  }

  @Test
  void trialingSubscriptionPreservesExistingManagedExposurePolicy() {
    current(SubscriptionStatus.TRIALING);

    assertThat(guard.isAllowed(10L)).isTrue();
    guard.assertAllowed(10L);
  }

  @Test
  void pastDueAndSuspendedBlockNewManagedExposure() {
    current(SubscriptionStatus.PAST_DUE);
    assertBlocked();

    current(SubscriptionStatus.SUSPENDED);
    assertBlocked();
  }

  @Test
  void terminalStatesFailClosedIfCanonicalSelectorEverReturnsThem() {
    current(SubscriptionStatus.CANCELLED);
    assertBlocked();

    current(SubscriptionStatus.EXPIRED);
    assertBlocked();
  }

  @Test
  void noCurrentSubscriptionFailsClosed() {
    when(lifecycle.currentOptional(10L)).thenReturn(Optional.empty());

    assertBlocked();
  }

  @Test
  void canonicalSelectorExcludesTerminalHistoryAndKeepsCurrentValidSubscription() {
    CompanySubscriptionRepository subscriptions = mock(CompanySubscriptionRepository.class);
    SubscriptionLifecycleService canonicalLifecycle =
        new SubscriptionLifecycleService(
            subscriptions,
            mock(SubscriptionTransitionRepository.class),
            mock(BillingPeriodService.class),
            mock(InvoiceService.class));
    ManagedFinancialExposureGuard canonicalGuard =
        new ManagedFinancialExposureGuard(canonicalLifecycle);

    CompanySubscription active = subscription(SubscriptionStatus.ACTIVE);
    when(subscriptions.findFirstByCompanyIdAndStatusInOrderByStartedAtDesc(eq(10L), anyCollection()))
        .thenReturn(Optional.of(active));

    assertThat(canonicalGuard.isAllowed(10L)).isTrue();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<SubscriptionStatus>> statuses =
        ArgumentCaptor.forClass(Collection.class);
    verify(subscriptions)
        .findFirstByCompanyIdAndStatusInOrderByStartedAtDesc(eq(10L), statuses.capture());
    assertThat(statuses.getValue())
        .containsExactly(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.TRIALING,
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.SUSPENDED)
        .doesNotContain(SubscriptionStatus.CANCELLED, SubscriptionStatus.EXPIRED);
  }

  private void current(SubscriptionStatus status) {
    when(lifecycle.currentOptional(10L)).thenReturn(Optional.of(subscription(status)));
  }

  private CompanySubscription subscription(SubscriptionStatus status) {
    CompanySubscription subscription = new CompanySubscription();
    subscription.setStatus(status);
    return subscription;
  }

  private void assertBlocked() {
    assertThat(guard.isAllowed(10L)).isFalse();
    assertThatThrownBy(() -> guard.assertAllowed(10L))
        .isInstanceOf(PlanEntitlementException.class)
        .hasMessage(ManagedFinancialExposureGuard.BLOCKED);
  }
}
