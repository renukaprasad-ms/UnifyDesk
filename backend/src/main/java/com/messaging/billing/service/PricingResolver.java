package com.messaging.billing.service;

import com.messaging.billing.entity.CompanyRateOverride;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.RateCard;
import com.messaging.billing.enums.PricingSource;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.repository.CompanyRateOverrideRepository;
import com.messaging.billing.repository.RateCardRepository;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PricingResolver {

  private final EntitlementService entitlementService;
  private final CompanyRateOverrideRepository overrideRepository;
  private final RateCardRepository rateCardRepository;

  @Transactional(readOnly = true)
  public ResolvedPrice resolve(PricingRequest request) {
    validateRequest(request);
    Instant at = request.occurredAt() == null ? Instant.now() : request.occurredAt();

    CompanySubscription subscription =
        entitlementService
            .currentSubscription(request.companyId())
            .orElseThrow(() -> new BadRequestException("Company does not have an active subscription"));

    Optional<CompanyRateOverride> override =
        overrideRepository.findByCompanyIdAndStatus(request.companyId(), RateCardStatus.ACTIVE).stream()
            .filter(rate -> matches(rate, request, at))
            .max(Comparator.comparingInt(rate -> specificity(rate.getCountry(), rate.getCarrier())));
    if (override.isPresent()) {
      CompanyRateOverride rate = override.get();
      return new ResolvedPrice(
          subscription.getPlan().getId().toString(),
          PricingSource.COMPANY_OVERRIDE,
          rate.getId().toString(),
          rate.getPrice(),
          rate.getCurrency());
    }

    Optional<RateCard> planRate =
        rateCardRepository.findByPlanIdAndStatus(subscription.getPlan().getId(), RateCardStatus.ACTIVE).stream()
            .filter(rate -> matches(rate, request, at))
            .max(Comparator.comparingInt(rate -> specificity(rate.getProvider() != null, rate.getCountry(), rate.getCarrier())));
    if (planRate.isPresent()) {
      RateCard rate = planRate.get();
      return new ResolvedPrice(
          subscription.getPlan().getId().toString(),
          PricingSource.PLAN_RATE,
          rate.getId().toString(),
          rate.getPrice(),
          rate.getCurrency());
    }

    throw new BadRequestException(
        "No pricing rule configured for " + request.provider() + " / " + request.billingCategory());
  }

  private boolean matches(CompanyRateOverride rate, PricingRequest request, Instant at) {
    return rate.getChannel() == request.channel()
        && rate.getProvider() == request.provider()
        && rate.getBillingCategory() == request.billingCategory()
        && rate.getDirection() == request.direction()
        && rate.getUnit() == request.unit()
        && dimensionMatches(rate.getCountry(), request.country())
        && dimensionMatches(rate.getCarrier(), request.carrier())
        && effective(rate.getEffectiveFrom(), rate.getEffectiveTo(), at);
  }

  private boolean matches(RateCard rate, PricingRequest request, Instant at) {
    return rate.getChannel() == request.channel()
        && (rate.getProvider() == null || rate.getProvider() == request.provider())
        && rate.getBillingCategory() == request.billingCategory()
        && rate.getDirection() == request.direction()
        && rate.getUnit() == request.unit()
        && dimensionMatches(rate.getCountry(), request.country())
        && dimensionMatches(rate.getCarrier(), request.carrier())
        && effective(rate.getEffectiveFrom(), rate.getEffectiveTo(), at);
  }

  private boolean dimensionMatches(String configured, String actual) {
    if (configured == null || configured.isBlank()) return true;
    return actual != null && configured.equalsIgnoreCase(actual.trim());
  }

  private boolean effective(Instant from, Instant to, Instant at) {
    return !at.isBefore(from) && (to == null || at.isBefore(to));
  }

  private int specificity(String country, String carrier) {
    return specificity(true, country, carrier);
  }

  private int specificity(boolean exactProvider, String country, String carrier) {
    int score = exactProvider ? 4 : 0;
    if (country != null && !country.isBlank()) score += 2;
    if (carrier != null && !carrier.isBlank()) score += 1;
    return score;
  }

  private void validateRequest(PricingRequest request) {
    Objects.requireNonNull(request, "Pricing request is required");
    if (request.companyId() == null
        || request.channel() == null
        || request.provider() == null
        || request.billingCategory() == null
        || request.direction() == null
        || request.unit() == null) {
      throw new BadRequestException("Pricing request is incomplete");
    }
    if (!request.provider().supports(request.channel())) {
      throw new BadRequestException("Provider does not support selected channel");
    }
    if (request.country() != null && !request.country().isBlank() && request.country().trim().length() != 2) {
      throw new BadRequestException("Country must be a 2-letter code");
    }
  }
}
