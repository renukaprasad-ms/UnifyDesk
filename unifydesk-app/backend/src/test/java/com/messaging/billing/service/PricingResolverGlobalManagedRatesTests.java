package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.dto.GlobalManagedRateResponse;
import com.messaging.billing.entity.CompanyRateOverride;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.RateCard;
import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.BillingSku;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.PricingSource;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.billing.repository.CompanyRateOverrideRepository;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.RateCardRepository;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PricingResolverGlobalManagedRatesTests {
  private static final Instant AT = Instant.parse("2026-10-10T12:00:00Z");
  private final CompanyRateOverrideRepository overrides =
      mock(CompanyRateOverrideRepository.class);
  private final CompanySubscriptionRepository subscriptions =
      mock(CompanySubscriptionRepository.class);
  private final RateCardRepository planRates = mock(RateCardRepository.class);
  private final GlobalManagedRateService globalRates = mock(GlobalManagedRateService.class);
  private PricingResolver resolver;

  @BeforeEach
  void setUp() {
    Plan plan = new Plan();
    plan.setId(10L);
    CompanySubscription subscription = new CompanySubscription();
    subscription.setId(20L);
    subscription.setPlan(plan);
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    when(subscriptions.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(1L, SubscriptionStatus.ACTIVE))
        .thenReturn(Optional.of(subscription));
    when(overrides.findByCompanyIdAndStatus(1L, RateCardStatus.ACTIVE))
        .thenReturn(List.of());
    when(planRates.findByPlanIdAndStatus(10L, RateCardStatus.ACTIVE))
        .thenReturn(List.of());
    resolver = new PricingResolver(overrides, subscriptions, null, planRates, globalRates);
  }

  @Test
  void globalRateUsedOnlyWhenPlanAndCompanyHaveNoMatchingRule() {
    when(globalRates.effectiveRate(BillingSku.MANAGED_EMAIL_OUTBOUND, AT))
        .thenReturn(Optional.of(global(BillingSku.MANAGED_EMAIL_OUTBOUND, "0.012000")));
    var price = email();
    assertEquals(new BigDecimal("0.012000"), price.unitPrice());
    assertEquals(PricingSource.GLOBAL_RATE, price.source());
    assertEquals("900", price.pricingRuleId());
    assertEquals("10", price.planId());
    assertEquals("20", price.subscriptionId());
  }

  @Test
  void companyOverrideKeepsPriorityOverGlobalAndPlan() {
    CompanyRateOverride override = new CompanyRateOverride();
    override.setId(111L);
    override.setChannel(ChannelType.EMAIL);
    override.setProvider(ProviderType.AMAZON_SES);
    override.setBillingCategory(BillingCategory.EMAIL_OUTBOUND);
    override.setDirection(CommunicationDirection.OUTBOUND);
    override.setUnit(UsageUnit.MESSAGE);
    override.setEffectiveFrom(AT.minusSeconds(10));
    override.setPrice(new BigDecimal("0.008000"));
    override.setCurrency("INR");
    when(overrides.findByCompanyIdAndStatus(1L, RateCardStatus.ACTIVE))
        .thenReturn(List.of(override));

    var price = email();
    assertEquals(PricingSource.COMPANY_OVERRIDE, price.source());
    assertEquals(new BigDecimal("0.008000"), price.unitPrice());
    verifyNoInteractions(globalRates);
  }

  @Test
  void planRateKeepsPriorityOverGlobal() {
    Plan plan = new Plan();
    plan.setId(10L);
    RateCard rate = new RateCard();
    rate.setId(110L);
    rate.setPlan(plan);
    rate.setChannel(ChannelType.EMAIL);
    rate.setProvider(ProviderType.AMAZON_SES);
    rate.setBillingCategory(BillingCategory.EMAIL_OUTBOUND);
    rate.setDirection(CommunicationDirection.OUTBOUND);
    rate.setUnit(UsageUnit.MESSAGE);
    rate.setEffectiveFrom(AT.minusSeconds(10));
    rate.setPrice(new BigDecimal("0.020000"));
    rate.setCurrency("INR");
    when(planRates.findByPlanIdAndStatus(10L, RateCardStatus.ACTIVE))
        .thenReturn(List.of(rate));

    var price = email();
    assertEquals(PricingSource.PLAN_RATE, price.source());
    assertEquals(new BigDecimal("0.020000"), price.unitPrice());
    verifyNoInteractions(globalRates);
  }

  @Test
  void globalSmsRateResolvesPerSegmentButNotPerMessage() {
    when(globalRates.effectiveRate(BillingSku.MANAGED_SMS_SEGMENT, AT))
        .thenReturn(Optional.of(global(BillingSku.MANAGED_SMS_SEGMENT, "0.250000")));
    var sms = resolver.resolve(1L, ChannelType.SMS, ProviderType.MSG91_SMS,
        BillingCategory.SMS_TRANSACTIONAL, CommunicationDirection.OUTBOUND,
        "IN", null, UsageUnit.SEGMENT, AT);
    assertEquals(new BigDecimal("0.250000"), sms.unitPrice());
    assertEquals(PricingSource.GLOBAL_RATE, sms.source());
    assertNull(GlobalManagedRateService.skuFor(ChannelType.SMS,
        BillingCategory.SMS_TRANSACTIONAL, CommunicationDirection.OUTBOUND, UsageUnit.MESSAGE));
    assertNull(GlobalManagedRateService.skuFor(ChannelType.EMAIL,
        BillingCategory.EMAIL_INBOUND, CommunicationDirection.INBOUND, UsageUnit.MESSAGE));
    assertNull(GlobalManagedRateService.skuFor(ChannelType.WHATSAPP,
        BillingCategory.WHATSAPP_MARKETING, CommunicationDirection.OUTBOUND, UsageUnit.MESSAGE));
  }

  private com.messaging.billing.dto.ResolvedPrice email() {
    return resolver.resolve(1L, ChannelType.EMAIL, ProviderType.AMAZON_SES,
        BillingCategory.EMAIL_OUTBOUND, CommunicationDirection.OUTBOUND,
        null, null, UsageUnit.MESSAGE, AT);
  }

  private GlobalManagedRateResponse global(BillingSku sku, String amount) {
    return new GlobalManagedRateResponse("900", sku, new BigDecimal(amount), "INR",
        AT.minusSeconds(60), null, null, "test");
  }
}
