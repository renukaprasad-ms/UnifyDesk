package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.messaging.billing.entity.*;
import com.messaging.billing.enums.*;
import com.messaging.billing.repository.*;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.company.entity.Company;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PricingResolverHistoricalTests {

  @Test
  void lateUsageUsesPlanEffectiveAtOccurrenceInsteadOfCurrentPlan() {
    CompanyRateOverrideRepository overrides = mock(CompanyRateOverrideRepository.class);
    CompanySubscriptionRepository subscriptions = mock(CompanySubscriptionRepository.class);
    SubscriptionTransitionRepository transitions = mock(SubscriptionTransitionRepository.class);
    RateCardRepository rates = mock(RateCardRepository.class);
    PricingResolver resolver = new PricingResolver(overrides, subscriptions, transitions, rates);

    Company company = new Company();
    company.setId(1L);
    Plan oldPlan = new Plan();
    oldPlan.setId(10L);
    CompanySubscription subscription = new CompanySubscription();
    subscription.setId(20L);
    subscription.setCompany(company);
    subscription.setPlan(oldPlan);
    SubscriptionTransition transition = new SubscriptionTransition();
    transition.setId(30L);
    transition.setCompany(company);
    transition.setSubscription(subscription);
    transition.setToPlan(oldPlan);

    Instant occurredAt = Instant.parse("2026-09-10T00:00:00Z");
    when(transitions
            .findFirstByCompanyIdAndOccurredAtLessThanEqualAndToPlanIsNotNullOrderByOccurredAtDescIdDesc(
                1L, occurredAt))
        .thenReturn(Optional.of(transition));
    when(overrides.findByCompanyIdAndStatus(1L, RateCardStatus.ACTIVE)).thenReturn(List.of());

    RateCard rate = new RateCard();
    rate.setId(40L);
    rate.setPlan(oldPlan);
    rate.setChannel(ChannelType.EMAIL);
    rate.setProvider(ProviderType.AMAZON_SES);
    rate.setBillingCategory(BillingCategory.EMAIL_OUTBOUND);
    rate.setDirection(CommunicationDirection.OUTBOUND);
    rate.setUnit(UsageUnit.MESSAGE);
    rate.setPrice(new BigDecimal("0.300000"));
    rate.setCurrency("INR");
    rate.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
    when(rates.findByPlanIdAndStatus(10L, RateCardStatus.ACTIVE)).thenReturn(List.of(rate));

    var resolved =
        resolver.resolve(
            1L,
            ChannelType.EMAIL,
            ProviderType.AMAZON_SES,
            BillingCategory.EMAIL_OUTBOUND,
            CommunicationDirection.OUTBOUND,
            null,
            null,
            UsageUnit.MESSAGE,
            occurredAt);

    assertEquals("10", resolved.planId());
    assertEquals("20", resolved.subscriptionId());
    assertEquals(new BigDecimal("0.300000"), resolved.unitPrice());
    verify(rates).findByPlanIdAndStatus(10L, RateCardStatus.ACTIVE);
    verifyNoInteractions(subscriptions);
  }
}
