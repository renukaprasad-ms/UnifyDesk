package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.messaging.billing.dto.ResolvedPrice;
import com.messaging.billing.entity.CompanyRateOverride;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.RateCard;
import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.PricingSource;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.billing.repository.CompanyRateOverrideRepository;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.RateCardRepository;
import com.messaging.common.exception.ConflictException;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PricingResolverTests {

  @Mock private CompanyRateOverrideRepository overrideRepository;
  @Mock private CompanySubscriptionRepository subscriptionRepository;
  @Mock private RateCardRepository rateCardRepository;

  @InjectMocks private PricingResolver pricingResolver;

  @Test
  void companyOverrideWinsOverPlanRate() {
    long companyId = 10L;
    Instant now = Instant.now();
    Plan plan = plan(20L);
    CompanySubscription subscription = subscription(plan);
    CompanyRateOverride override = override(30L, new BigDecimal("0.170000"), now);

    when(overrideRepository.findByCompanyIdAndStatus(companyId, RateCardStatus.ACTIVE))
        .thenReturn(List.of(override));
    when(subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.ACTIVE))
        .thenReturn(Optional.of(subscription));

    ResolvedPrice resolved = resolve(companyId, now);

    assertEquals(PricingSource.COMPANY_OVERRIDE, resolved.source());
    assertEquals(new BigDecimal("0.170000"), resolved.unitPrice());
    assertEquals("30", resolved.pricingRuleId());
  }

  @Test
  void legacyActiveNullStartCompanyOverrideFailsClosedWithoutNpe() {
    long companyId = 10L;
    Instant now = Instant.parse("2026-10-03T06:00:00Z");
    CompanyRateOverride override = override(30L, new BigDecimal("0.170000"), now);
    override.setEffectiveFrom(null);
    when(overrideRepository.findByCompanyIdAndStatus(companyId, RateCardStatus.ACTIVE))
        .thenReturn(List.of(override));

    assertThrows(ConflictException.class, () -> resolve(companyId, now));
  }

  @Test
  void legacyActiveNullStartRateCardFailsClosedWithoutNpe() {
    long companyId = 10L;
    Instant now = Instant.parse("2026-10-03T06:00:00Z");
    Plan plan = plan(20L);
    CompanySubscription subscription = subscription(plan);
    RateCard rateCard = rateCard(40L, plan, new BigDecimal("0.200000"), now);
    rateCard.setEffectiveFrom(null);

    when(overrideRepository.findByCompanyIdAndStatus(companyId, RateCardStatus.ACTIVE))
        .thenReturn(List.of());
    when(subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.ACTIVE))
        .thenReturn(Optional.of(subscription));
    when(rateCardRepository.findByPlanIdAndStatus(plan.getId(), RateCardStatus.ACTIVE))
        .thenReturn(List.of(rateCard));

    assertThrows(ConflictException.class, () -> resolve(companyId, now));
  }

  @Test
  void fallsBackToPlanRateWhenNoOverrideMatches() {
    long companyId = 10L;
    Instant now = Instant.now();
    Plan plan = plan(20L);
    CompanySubscription subscription = subscription(plan);
    RateCard rateCard = rateCard(40L, plan, new BigDecimal("0.200000"), now);

    when(overrideRepository.findByCompanyIdAndStatus(companyId, RateCardStatus.ACTIVE))
        .thenReturn(List.of());
    when(subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.ACTIVE))
        .thenReturn(Optional.of(subscription));
    when(rateCardRepository.findByPlanIdAndStatus(plan.getId(), RateCardStatus.ACTIVE))
        .thenReturn(List.of(rateCard));

    ResolvedPrice resolved = resolve(companyId, now);

    assertEquals(PricingSource.PLAN_RATE, resolved.source());
    assertEquals(new BigDecimal("0.200000"), resolved.unitPrice());
    assertEquals("40", resolved.pricingRuleId());
  }

  private ResolvedPrice resolve(long companyId, Instant at) {
    return pricingResolver.resolve(
        companyId,
        ChannelType.SMS,
        ProviderType.MSG91_SMS,
        BillingCategory.SMS_PROMOTIONAL,
        CommunicationDirection.OUTBOUND,
        "IN",
        null,
        UsageUnit.MESSAGE,
        at);
  }

  private Plan plan(Long id) {
    Plan plan = new Plan();
    plan.setId(id);
    return plan;
  }

  private CompanySubscription subscription(Plan plan) {
    CompanySubscription subscription = new CompanySubscription();
    subscription.setPlan(plan);
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    return subscription;
  }

  private CompanyRateOverride override(Long id, BigDecimal price, Instant at) {
    CompanyRateOverride override = new CompanyRateOverride();
    override.setId(id);
    override.setChannel(ChannelType.SMS);
    override.setProvider(ProviderType.MSG91_SMS);
    override.setBillingCategory(BillingCategory.SMS_PROMOTIONAL);
    override.setDirection(CommunicationDirection.OUTBOUND);
    override.setCountry("IN");
    override.setUnit(UsageUnit.MESSAGE);
    override.setPrice(price);
    override.setCurrency("INR");
    override.setStatus(RateCardStatus.ACTIVE);
    override.setEffectiveFrom(at.minusSeconds(60));
    return override;
  }

  private RateCard rateCard(Long id, Plan plan, BigDecimal price, Instant at) {
    RateCard rateCard = new RateCard();
    rateCard.setId(id);
    rateCard.setPlan(plan);
    rateCard.setChannel(ChannelType.SMS);
    rateCard.setProvider(ProviderType.MSG91_SMS);
    rateCard.setBillingCategory(BillingCategory.SMS_PROMOTIONAL);
    rateCard.setDirection(CommunicationDirection.OUTBOUND);
    rateCard.setCountry("IN");
    rateCard.setUnit(UsageUnit.MESSAGE);
    rateCard.setPrice(price);
    rateCard.setCurrency("INR");
    rateCard.setStatus(RateCardStatus.ACTIVE);
    rateCard.setEffectiveFrom(at.minusSeconds(60));
    return rateCard;
  }
}
