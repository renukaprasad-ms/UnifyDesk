package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.SubscriptionTransition;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.enums.SubscriptionTransitionType;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.SubscriptionTransitionRepository;
import com.messaging.company.entity.Company;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionLifecycleServiceTests {

  @Mock private CompanySubscriptionRepository subscriptionRepository;
  @Mock private SubscriptionTransitionRepository transitionRepository;

  @InjectMocks private SubscriptionLifecycleService service;

  @Test
  void pastDueSuspensionUsesClaimedRowsAndReplayConverges() {
    Instant now = Instant.parse("2026-10-10T00:00:00Z");
    CompanySubscription subscription = subscription(10L, SubscriptionStatus.PAST_DUE);
    when(subscriptionRepository.findPastDueForUpdate(now, 100))
        .thenReturn(List.of(subscription), List.of());
    when(subscriptionRepository.findExpiredTrialsForUpdate(now, 100))
        .thenReturn(List.of(), List.of());
    when(subscriptionRepository.findDueCancellationsForUpdate(now, 100))
        .thenReturn(List.of(), List.of());

    assertEquals(1, service.applyDueLifecycle(now));
    assertEquals(SubscriptionStatus.SUSPENDED, subscription.getStatus());
    assertEquals(0, service.applyDueLifecycle(now));

    verify(transitionRepository, times(1)).save(any(SubscriptionTransition.class));
  }

  @Test
  void recoveryIsAuditedAsSystemProviderTransition() {
    Instant now = Instant.parse("2026-10-10T00:00:00Z");
    CompanySubscription subscription = subscription(10L, SubscriptionStatus.SUSPENDED);
    when(subscriptionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(subscription));
    when(subscriptionRepository.saveAndFlush(subscription)).thenReturn(subscription);

    service.recover(10L, now);

    assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
    ArgumentCaptor<SubscriptionTransition> captor =
        ArgumentCaptor.forClass(SubscriptionTransition.class);
    verify(transitionRepository).save(captor.capture());
    assertEquals(SubscriptionTransitionType.RECOVERED, captor.getValue().getTransitionType());
    assertEquals(null, captor.getValue().getActorUserId());
  }

  @Test
  void customerCancellationRetainsAuthenticatedActor() {
    Instant end = Instant.parse("2026-11-01T00:00:00Z");
    CompanySubscription subscription = subscription(10L, SubscriptionStatus.ACTIVE);
    subscription.setCurrentPeriodEnd(end);
    when(subscriptionRepository.findCurrentForUpdate(1L)).thenReturn(Optional.of(subscription));
    when(subscriptionRepository.saveAndFlush(subscription)).thenReturn(subscription);

    service.scheduleCancellation(1L, 77L);

    ArgumentCaptor<SubscriptionTransition> captor =
        ArgumentCaptor.forClass(SubscriptionTransition.class);
    verify(transitionRepository).save(captor.capture());
    assertEquals(77L, captor.getValue().getActorUserId());
    assertEquals(
        SubscriptionTransitionType.CANCELLATION_SCHEDULED,
        captor.getValue().getTransitionType());
    assertEquals(end, subscription.getCancellationEffectiveAt());
  }

  @Test
  void cancelAtPeriodEndExpiresOnlyAtBoundary() {
    Instant boundary = Instant.parse("2026-11-01T00:00:00Z");
    CompanySubscription subscription = subscription(10L, SubscriptionStatus.ACTIVE);
    subscription.setCancelAtPeriodEnd(true);
    subscription.setCurrentPeriodEnd(boundary);
    when(subscriptionRepository.findPastDueForUpdate(boundary, 100)).thenReturn(List.of());
    when(subscriptionRepository.findExpiredTrialsForUpdate(boundary, 100)).thenReturn(List.of());
    when(subscriptionRepository.findDueCancellationsForUpdate(boundary, 100))
        .thenReturn(List.of(subscription));

    assertEquals(1, service.applyDueLifecycle(boundary));
    assertEquals(SubscriptionStatus.EXPIRED, subscription.getStatus());
    assertEquals(boundary, subscription.getEndsAt());
  }

  private CompanySubscription subscription(Long id, SubscriptionStatus status) {
    Company company = new Company();
    company.setId(1L);
    Plan plan = new Plan();
    plan.setId(2L);
    CompanySubscription subscription = new CompanySubscription();
    subscription.setId(id);
    subscription.setCompany(company);
    subscription.setPlan(plan);
    subscription.setStatus(status);
    subscription.setCurrentPeriodStart(Instant.parse("2026-10-01T00:00:00Z"));
    return subscription;
  }
}
