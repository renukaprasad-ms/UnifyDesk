package com.messaging.billing.service;

import com.messaging.billing.dto.ResolvedPrice;
import com.messaging.billing.entity.CompanyRateOverride;
import com.messaging.billing.entity.CompanySubscription;
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
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PricingResolver {

  private final CompanyRateOverrideRepository overrideRepository;
  private final CompanySubscriptionRepository subscriptionRepository;
  private final RateCardRepository rateCardRepository;

  @Transactional(readOnly = true)
  public ResolvedPrice resolve(
      Long companyId,
      ChannelType channel,
      ProviderType provider,
      BillingCategory billingCategory,
      CommunicationDirection direction,
      String country,
      String carrier,
      UsageUnit unit,
      Instant occurredAt) {

    if (!provider.supports(channel)) {
      throw new IllegalArgumentException(provider + " does not support channel " + channel);
    }

    Instant at = occurredAt == null ? Instant.now() : occurredAt;
    String normalizedCountry = normalizeCountry(country);
    String normalizedCarrier = normalizeOptional(carrier);

    List<CompanyRateOverride> overrides =
        overrideRepository.findByCompanyIdAndStatus(companyId, RateCardStatus.ACTIVE).stream()
            .filter(rule -> matches(
                rule.getChannel(), rule.getProvider(), rule.getBillingCategory(),
                rule.getDirection(), rule.getCountry(), rule.getCarrier(), rule.getUnit(),
                rule.getEffectiveFrom(), rule.getEffectiveTo(),
                channel, provider, billingCategory, direction, normalizedCountry,
                normalizedCarrier, unit, at))
            .sorted(Comparator.comparingInt(
                    (CompanyRateOverride rule) -> specificity(rule.getCountry(), rule.getCarrier(), normalizedCountry, normalizedCarrier))
                .reversed()
                .thenComparing(CompanyRateOverride::getEffectiveFrom, Comparator.reverseOrder()))
            .toList();

    CompanySubscription subscription = effectiveSubscription(companyId);

    if (!overrides.isEmpty()) {
      CompanyRateOverride rule = overrides.getFirst();
      return new ResolvedPrice(
          rule.getPrice(),
          rule.getCurrency(),
          PricingSource.COMPANY_OVERRIDE,
          rule.getId().toString(),
          subscription.getPlan().getId().toString());
    }

    List<RateCard> planRates =
        rateCardRepository.findByPlanIdAndStatus(subscription.getPlan().getId(), RateCardStatus.ACTIVE).stream()
            .filter(rule -> matches(
                rule.getChannel(), rule.getProvider(), rule.getBillingCategory(),
                rule.getDirection(), rule.getCountry(), rule.getCarrier(), rule.getUnit(),
                rule.getEffectiveFrom(), rule.getEffectiveTo(),
                channel, provider, billingCategory, direction, normalizedCountry,
                normalizedCarrier, unit, at))
            .sorted(Comparator.comparingInt(
                    (RateCard rule) -> specificity(rule.getCountry(), rule.getCarrier(), normalizedCountry, normalizedCarrier))
                .reversed()
                .thenComparing(RateCard::getEffectiveFrom, Comparator.reverseOrder()))
            .toList();

    if (planRates.isEmpty()) {
      throw new NotFoundException(
          "No pricing rule configured for " + provider + " / " + billingCategory);
    }

    RateCard rule = planRates.getFirst();
    return new ResolvedPrice(
        rule.getPrice(),
        rule.getCurrency(),
        PricingSource.PLAN_RATE,
        rule.getId().toString(),
        subscription.getPlan().getId().toString());
  }

  private CompanySubscription effectiveSubscription(Long companyId) {
    return subscriptionRepository
        .findFirstByCompanyIdAndStatusOrderByStartedAtDesc(companyId, SubscriptionStatus.ACTIVE)
        .or(() -> subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.TRIALING))
        .orElseThrow(() -> new NotFoundException("Company subscription not found"));
  }

  private boolean matches(
      ChannelType ruleChannel,
      ProviderType ruleProvider,
      BillingCategory ruleCategory,
      CommunicationDirection ruleDirection,
      String ruleCountry,
      String ruleCarrier,
      UsageUnit ruleUnit,
      Instant from,
      Instant to,
      ChannelType channel,
      ProviderType provider,
      BillingCategory category,
      CommunicationDirection direction,
      String country,
      String carrier,
      UsageUnit unit,
      Instant at) {
    return ruleChannel == channel
        && ruleProvider == provider
        && ruleCategory == category
        && ruleDirection == direction
        && ruleUnit == unit
        && (ruleCountry == null || ruleCountry.equalsIgnoreCase(country))
        && (ruleCarrier == null || ruleCarrier.equalsIgnoreCase(carrier))
        && !from.isAfter(at)
        && (to == null || to.isAfter(at));
  }

  private int specificity(String ruleCountry, String ruleCarrier, String country, String carrier) {
    int score = 0;
    if (ruleCountry != null && ruleCountry.equalsIgnoreCase(country)) score += 1;
    if (ruleCarrier != null && ruleCarrier.equalsIgnoreCase(carrier)) score += 2;
    return score;
  }

  private String normalizeCountry(String value) {
    return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
