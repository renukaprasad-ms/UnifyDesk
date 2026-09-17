package com.messaging.billing.service;

import com.messaging.billing.dto.AssignSubscriptionRequest;
import com.messaging.billing.dto.CompanyPlanDetailsResponse;
import com.messaging.billing.dto.CompanyRateOverrideResponse;
import com.messaging.billing.dto.CompanySubscriptionResponse;
import com.messaging.billing.dto.CreateCompanyRateOverrideRequest;
import com.messaging.billing.dto.CreatePlanPriceRequest;
import com.messaging.billing.dto.CreatePlanRequest;
import com.messaging.billing.dto.CreateRateCardRequest;
import com.messaging.billing.dto.EntitlementDefinitionResponse;
import com.messaging.billing.dto.EntitlementResponse;
import com.messaging.billing.dto.PlanPriceResponse;
import com.messaging.billing.dto.PlanResponse;
import com.messaging.billing.dto.RateCardResponse;
import com.messaging.billing.dto.UpdateCompanyRateOverrideRequest;
import com.messaging.billing.dto.UpdatePlanPriceRequest;
import com.messaging.billing.dto.UpdatePlanRequest;
import com.messaging.billing.dto.UpdateRateCardRequest;
import com.messaging.billing.dto.UpsertEntitlementRequest;
import com.messaging.billing.entity.CompanyRateOverride;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.EntitlementDefinition;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanEntitlement;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.entity.RateCard;
import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.PlanPriceStatus;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.repository.CompanyRateOverrideRepository;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.EntitlementDefinitionRepository;
import com.messaging.billing.repository.PlanEntitlementRepository;
import com.messaging.billing.repository.PlanPriceRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.billing.repository.RateCardRepository;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingCatalogService {

  private static final EnumSet<SubscriptionStatus> CURRENT_SUBSCRIPTION_STATUSES =
      EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING, SubscriptionStatus.PAST_DUE);

  private final PlanRepository planRepository;
  private final PlanEntitlementRepository entitlementRepository;
  private final EntitlementDefinitionRepository entitlementDefinitionRepository;
  private final PlanPriceRepository planPriceRepository;
  private final RateCardRepository rateCardRepository;
  private final CompanyRateOverrideRepository companyRateOverrideRepository;
  private final CompanySubscriptionRepository subscriptionRepository;
  private final CompanyRepository companyRepository;
  private final EntitlementService entitlementService;

  @Transactional
  public PlanResponse createPlan(CreatePlanRequest request) {
    Plan plan = new Plan();
    plan.setCode(normalizeCode(request.code()));
    plan.setName(request.name().trim());
    plan.setBillingCycle(request.billingCycle());
    plan.setBasePrice(request.basePrice());
    plan.setCurrency(normalizeCurrency(request.currency()));
    plan.setVersion(request.version() <= 0 ? 1 : request.version());
    plan.setStatus(PlanStatus.DRAFT);
    plan = planRepository.saveAndFlush(plan);

    PlanPrice price = new PlanPrice();
    price.setPlan(plan);
    price.setBillingCycle(request.billingCycle());
    price.setAmount(request.basePrice());
    price.setCurrency(normalizeCurrency(request.currency()));
    price.setEffectiveFrom(Instant.now());
    price.setStatus(PlanPriceStatus.ACTIVE);
    planPriceRepository.saveAndFlush(price);
    return toPlanResponse(plan);
  }

  @Transactional(readOnly = true)
  public List<PlanResponse> plans() {
    return planRepository.findAll().stream().map(this::toPlanResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<PlanResponse> activePlans() {
    return planRepository.findByStatus(PlanStatus.PUBLISHED).stream()
        .map(this::toPlanResponse)
        .toList();
  }

  @Transactional
  public PlanResponse updatePlan(Long planId, UpdatePlanRequest request) {
    Plan plan = getPlan(planId);
    if (request.name() != null && !request.name().isBlank()) plan.setName(request.name().trim());
    if (request.billingCycle() != null) plan.setBillingCycle(request.billingCycle());
    if (request.basePrice() != null) plan.setBasePrice(request.basePrice());
    if (request.currency() != null && !request.currency().isBlank()) {
      plan.setCurrency(normalizeCurrency(request.currency()));
    }
    if (request.status() != null) {
      if (request.status() == PlanStatus.PUBLISHED
          && planPriceRepository.findByPlanIdAndStatus(planId, PlanPriceStatus.ACTIVE).isEmpty()) {
        throw new BadRequestException("A plan must have at least one active price before publishing");
      }
      plan.setStatus(request.status());
    }
    return toPlanResponse(planRepository.saveAndFlush(plan));
  }

  @Transactional(readOnly = true)
  public List<PlanPriceResponse> planPrices(Long planId) {
    getPlan(planId);
    return planPriceRepository.findByPlanIdOrderByEffectiveFromDesc(planId).stream()
        .map(this::toPlanPriceResponse)
        .toList();
  }

  @Transactional
  public PlanPriceResponse createPlanPrice(Long planId, CreatePlanPriceRequest request) {
    Plan plan = getPlan(planId);
    Instant from = request.effectiveFrom() == null ? Instant.now() : request.effectiveFrom();
    validateWindow(from, request.effectiveTo());
    assertNoPlanPriceOverlap(planId, request.billingCycle(), from, request.effectiveTo(), null);

    PlanPrice price = new PlanPrice();
    price.setPlan(plan);
    price.setBillingCycle(request.billingCycle());
    price.setAmount(request.amount());
    price.setCurrency(normalizeCurrency(request.currency()));
    price.setEffectiveFrom(from);
    price.setEffectiveTo(request.effectiveTo());
    price.setStatus(PlanPriceStatus.ACTIVE);
    return toPlanPriceResponse(planPriceRepository.saveAndFlush(price));
  }

  @Transactional
  public PlanPriceResponse updatePlanPrice(Long planPriceId, UpdatePlanPriceRequest request) {
    PlanPrice price =
        planPriceRepository
            .findById(planPriceId)
            .orElseThrow(() -> new NotFoundException("Plan price not found"));
    Instant from = request.effectiveFrom() == null ? price.getEffectiveFrom() : request.effectiveFrom();
    Instant to = request.effectiveTo();
    if (request.effectiveTo() == null && price.getEffectiveTo() != null) to = price.getEffectiveTo();
    validateWindow(from, to);

    if (request.amount() != null) price.setAmount(request.amount());
    if (request.currency() != null && !request.currency().isBlank()) {
      price.setCurrency(normalizeCurrency(request.currency()));
    }
    if (request.effectiveFrom() != null) price.setEffectiveFrom(request.effectiveFrom());
    if (request.effectiveTo() != null) price.setEffectiveTo(request.effectiveTo());
    if (request.status() != null) price.setStatus(request.status());

    if (price.getStatus() == PlanPriceStatus.ACTIVE) {
      assertNoPlanPriceOverlap(
          price.getPlan().getId(), price.getBillingCycle(), price.getEffectiveFrom(), price.getEffectiveTo(), price.getId());
    }
    return toPlanPriceResponse(planPriceRepository.saveAndFlush(price));
  }

  @Transactional(readOnly = true)
  public List<EntitlementDefinitionResponse> entitlementDefinitions() {
    return entitlementDefinitionRepository.findByActiveTrueOrderByCategoryAscLabelAsc().stream()
        .map(this::toEntitlementDefinitionResponse)
        .toList();
  }

  @Transactional
  public EntitlementResponse upsertEntitlement(Long planId, UpsertEntitlementRequest request) {
    Plan plan = getPlan(planId);
    EntitlementDefinition definition = entitlementService.requireDefinition(request.key());
    String key = definition.getKey();
    String validatedValue = entitlementService.validateValue(definition, request.value());

    PlanEntitlement entitlement =
        entitlementRepository.findByPlanIdAndKey(planId, key).orElseGet(PlanEntitlement::new);
    entitlement.setPlan(plan);
    entitlement.setKey(key);
    entitlement.setValue(validatedValue);
    return toEntitlementResponse(entitlementRepository.saveAndFlush(entitlement));
  }

  @Transactional
  public void deleteEntitlement(Long entitlementId) {
    if (!entitlementRepository.existsById(entitlementId)) {
      throw new NotFoundException("Entitlement not found");
    }
    entitlementRepository.deleteById(entitlementId);
  }

  @Transactional
  public RateCardResponse createRateCard(CreateRateCardRequest request) {
    Plan plan = getPlan(request.planId());
    validatePricingDimensions(request.channel(), request.provider(), request.billingCategory());
    Instant from = request.effectiveFrom() == null ? Instant.now() : request.effectiveFrom();
    validateWindow(from, request.effectiveTo());

    RateCard rateCard = new RateCard();
    rateCard.setPlan(plan);
    rateCard.setChannel(request.channel());
    rateCard.setProvider(request.provider());
    rateCard.setCountry(normalizeCountry(request.country()));
    rateCard.setCarrier(normalizeOptional(request.carrier()));
    rateCard.setBillingCategory(request.billingCategory());
    rateCard.setDirection(request.direction());
    rateCard.setUnit(request.unit());
    rateCard.setPrice(request.price());
    rateCard.setCurrency(
        normalizeCurrency(request.currency() == null ? plan.getCurrency() : request.currency()));
    rateCard.setEffectiveFrom(from);
    rateCard.setEffectiveTo(request.effectiveTo());
    rateCard.setStatus(RateCardStatus.ACTIVE);
    assertNoRateCardOverlap(rateCard, null);
    return toRateCardResponse(rateCardRepository.saveAndFlush(rateCard));
  }

  @Transactional(readOnly = true)
  public List<RateCardResponse> rateCards(Long planId) {
    getPlan(planId);
    return rateCardRepository.findByPlanIdAndStatus(planId, RateCardStatus.ACTIVE).stream()
        .map(this::toRateCardResponse)
        .toList();
  }

  @Transactional
  public RateCardResponse updateRateCard(Long rateCardId, UpdateRateCardRequest request) {
    RateCard rateCard =
        rateCardRepository
            .findById(rateCardId)
            .orElseThrow(() -> new NotFoundException("Rate card not found"));
    if (request.price() != null) rateCard.setPrice(request.price());
    if (request.effectiveFrom() != null) rateCard.setEffectiveFrom(request.effectiveFrom());
    if (request.effectiveTo() != null) rateCard.setEffectiveTo(request.effectiveTo());
    if (request.status() != null) rateCard.setStatus(request.status());
    validateWindow(rateCard.getEffectiveFrom(), rateCard.getEffectiveTo());
    if (rateCard.getStatus() == RateCardStatus.ACTIVE) assertNoRateCardOverlap(rateCard, rateCard.getId());
    return toRateCardResponse(rateCardRepository.saveAndFlush(rateCard));
  }

  @Transactional
  public CompanyRateOverrideResponse createCompanyRateOverride(
      Long companyId, Long createdByUserId, CreateCompanyRateOverrideRequest request) {
    Company company = getCompany(companyId);
    validatePricingDimensions(request.channel(), request.provider(), request.billingCategory());
    Instant from = request.effectiveFrom() == null ? Instant.now() : request.effectiveFrom();
    validateWindow(from, request.effectiveTo());

    CompanyRateOverride override = new CompanyRateOverride();
    override.setCompany(company);
    override.setChannel(request.channel());
    override.setProvider(request.provider());
    override.setBillingCategory(request.billingCategory());
    override.setDirection(request.direction());
    override.setCountry(normalizeCountry(request.country()));
    override.setCarrier(normalizeOptional(request.carrier()));
    override.setUnit(request.unit());
    override.setPrice(request.price());
    override.setCurrency(normalizeCurrency(request.currency()));
    override.setEffectiveFrom(from);
    override.setEffectiveTo(request.effectiveTo());
    override.setStatus(RateCardStatus.ACTIVE);
    override.setCreatedByUserId(createdByUserId);
    override.setReason(normalizeOptional(request.reason()));
    assertNoCompanyOverrideOverlap(override, null);
    return toCompanyRateOverrideResponse(companyRateOverrideRepository.saveAndFlush(override));
  }

  @Transactional(readOnly = true)
  public List<CompanyRateOverrideResponse> companyRateOverrides(Long companyId) {
    getCompany(companyId);
    return companyRateOverrideRepository.findByCompanyIdAndStatus(companyId, RateCardStatus.ACTIVE).stream()
        .map(this::toCompanyRateOverrideResponse)
        .toList();
  }

  @Transactional
  public CompanyRateOverrideResponse updateCompanyRateOverride(
      Long overrideId, UpdateCompanyRateOverrideRequest request) {
    CompanyRateOverride override =
        companyRateOverrideRepository
            .findById(overrideId)
            .orElseThrow(() -> new NotFoundException("Company rate override not found"));
    if (request.price() != null) override.setPrice(request.price());
    if (request.currency() != null && !request.currency().isBlank()) {
      override.setCurrency(normalizeCurrency(request.currency()));
    }
    if (request.effectiveFrom() != null) override.setEffectiveFrom(request.effectiveFrom());
    if (request.effectiveTo() != null) override.setEffectiveTo(request.effectiveTo());
    if (request.status() != null) override.setStatus(request.status());
    if (request.reason() != null) override.setReason(normalizeOptional(request.reason()));
    validateWindow(override.getEffectiveFrom(), override.getEffectiveTo());
    if (override.getStatus() == RateCardStatus.ACTIVE) {
      assertNoCompanyOverrideOverlap(override, override.getId());
    }
    return toCompanyRateOverrideResponse(companyRateOverrideRepository.saveAndFlush(override));
  }

  @Transactional
  public CompanySubscriptionResponse assignSubscription(
      Long companyId, AssignSubscriptionRequest request) {
    return assignSubscriptionInternal(companyId, request, true);
  }

  @Transactional
  public CompanySubscriptionResponse selectFreeSubscription(
      Long companyId, AssignSubscriptionRequest request) {
    return assignSubscriptionInternal(companyId, request, false);
  }

  @Transactional(readOnly = true)
  public CompanySubscriptionResponse activeSubscription(Long companyId) {
    return currentSubscription(companyId)
        .map(this::toSubscriptionResponse)
        .orElseThrow(() -> new NotFoundException("Company subscription not found"));
  }

  @Transactional(readOnly = true)
  public CompanyPlanDetailsResponse companyPlanDetails(Long companyId) {
    CompanySubscription subscription =
        currentSubscription(companyId)
            .orElseThrow(() -> new NotFoundException("Company subscription not found"));
    return new CompanyPlanDetailsResponse(
        toSubscriptionResponse(subscription), toPlanResponse(subscription.getPlan()));
  }

  private CompanySubscriptionResponse assignSubscriptionInternal(
      Long companyId, AssignSubscriptionRequest request, boolean allowPaid) {
    Company company = getCompany(companyId);
    Plan plan = getPlan(parseId(request.planId(), "Plan not found"));
    if (plan.getStatus() != PlanStatus.PUBLISHED && !allowPaid) {
      throw new BadRequestException("Only published plans can be selected");
    }
    PlanPrice planPrice = resolvePlanPrice(plan, request.planPriceId());
    if (!allowPaid && planPrice.getAmount().signum() > 0) {
      throw new BadRequestException("Paid plans require a checkout flow");
    }

    Instant now = Instant.now();
    subscriptionRepository.findByCompanyIdOrderByStartedAtDesc(companyId).stream()
        .filter(existing -> CURRENT_SUBSCRIPTION_STATUSES.contains(existing.getStatus()))
        .forEach(
            existing -> {
              existing.setStatus(SubscriptionStatus.CANCELLED);
              existing.setCancelledAt(now);
              existing.setEndsAt(now);
            });

    CompanySubscription subscription = new CompanySubscription();
    subscription.setCompany(company);
    subscription.setPlan(plan);
    subscription.setPlanPrice(planPrice);
    subscription.setStatus(request.status() == null ? SubscriptionStatus.ACTIVE : request.status());
    subscription.setStartedAt(request.startedAt() == null ? now : request.startedAt());
    subscription.setEndsAt(request.endsAt());
    subscription.setCurrentPeriodStart(
        request.currentPeriodStart() == null ? subscription.getStartedAt() : request.currentPeriodStart());
    subscription.setCurrentPeriodEnd(request.currentPeriodEnd());
    subscription.setTrialEndsAt(request.trialEndsAt());
    subscription.setCancelAtPeriodEnd(Boolean.TRUE.equals(request.cancelAtPeriodEnd()));
    return toSubscriptionResponse(subscriptionRepository.saveAndFlush(subscription));
  }

  private PlanPrice resolvePlanPrice(Plan plan, String planPriceId) {
    if (planPriceId != null && !planPriceId.isBlank()) {
      PlanPrice price =
          planPriceRepository
              .findById(parseId(planPriceId, "Plan price not found"))
              .orElseThrow(() -> new NotFoundException("Plan price not found"));
      if (!price.getPlan().getId().equals(plan.getId()) || price.getStatus() != PlanPriceStatus.ACTIVE) {
        throw new BadRequestException("Plan price does not belong to the selected plan");
      }
      return price;
    }

    List<PlanPrice> prices = planPriceRepository.findByPlanIdAndStatus(plan.getId(), PlanPriceStatus.ACTIVE);
    if (prices.isEmpty()) throw new BadRequestException("Plan does not have an active price");
    if (prices.size() > 1) throw new BadRequestException("planPriceId is required for this plan");
    return prices.getFirst();
  }

  private java.util.Optional<CompanySubscription> currentSubscription(Long companyId) {
    return subscriptionRepository.findFirstByCompanyIdAndStatusInOrderByStartedAtDesc(
        companyId, CURRENT_SUBSCRIPTION_STATUSES);
  }

  private void assertNoPlanPriceOverlap(
      Long planId, BillingCycle cycle, Instant from, Instant to, Long excludeId) {
    boolean overlaps =
        planPriceRepository.findByPlanIdAndBillingCycleAndStatus(planId, cycle, PlanPriceStatus.ACTIVE).stream()
            .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
            .anyMatch(existing -> overlaps(from, to, existing.getEffectiveFrom(), existing.getEffectiveTo()));
    if (overlaps) throw new BadRequestException("An active plan price already overlaps this period");
  }

  private void assertNoRateCardOverlap(RateCard candidate, Long excludeId) {
    boolean overlaps =
        rateCardRepository.findByPlanIdAndStatus(candidate.getPlan().getId(), RateCardStatus.ACTIVE).stream()
            .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
            .filter(existing -> sameRateDimensions(existing, candidate))
            .anyMatch(existing -> overlaps(candidate.getEffectiveFrom(), candidate.getEffectiveTo(), existing.getEffectiveFrom(), existing.getEffectiveTo()));
    if (overlaps) throw new BadRequestException("An active rate card already overlaps these pricing dimensions");
  }

  private void assertNoCompanyOverrideOverlap(CompanyRateOverride candidate, Long excludeId) {
    boolean overlaps =
        companyRateOverrideRepository.findByCompanyIdAndStatus(candidate.getCompany().getId(), RateCardStatus.ACTIVE).stream()
            .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
            .filter(existing -> sameOverrideDimensions(existing, candidate))
            .anyMatch(existing -> overlaps(candidate.getEffectiveFrom(), candidate.getEffectiveTo(), existing.getEffectiveFrom(), existing.getEffectiveTo()));
    if (overlaps) throw new BadRequestException("An active company rate override already overlaps these pricing dimensions");
  }

  private boolean sameRateDimensions(RateCard left, RateCard right) {
    return left.getChannel() == right.getChannel()
        && left.getProvider() == right.getProvider()
        && left.getBillingCategory() == right.getBillingCategory()
        && left.getDirection() == right.getDirection()
        && left.getUnit() == right.getUnit()
        && Objects.equals(left.getCountry(), right.getCountry())
        && Objects.equals(left.getCarrier(), right.getCarrier());
  }

  private boolean sameOverrideDimensions(CompanyRateOverride left, CompanyRateOverride right) {
    return left.getChannel() == right.getChannel()
        && left.getProvider() == right.getProvider()
        && left.getBillingCategory() == right.getBillingCategory()
        && left.getDirection() == right.getDirection()
        && left.getUnit() == right.getUnit()
        && Objects.equals(left.getCountry(), right.getCountry())
        && Objects.equals(left.getCarrier(), right.getCarrier());
  }

  private boolean overlaps(Instant leftFrom, Instant leftTo, Instant rightFrom, Instant rightTo) {
    return (leftTo == null || rightFrom.isBefore(leftTo))
        && (rightTo == null || leftFrom.isBefore(rightTo));
  }

  private void validateWindow(Instant from, Instant to) {
    if (from == null) throw new BadRequestException("effectiveFrom is required");
    if (to != null && !to.isAfter(from)) {
      throw new BadRequestException("effectiveTo must be after effectiveFrom");
    }
  }

  private void validatePricingDimensions(
      ChannelType channel,
      com.messaging.channel.enums.ProviderType provider,
      BillingCategory category) {
    if (provider != null && !provider.supports(channel)) {
      throw new BadRequestException("Provider does not support selected channel");
    }
    boolean valid =
        switch (channel) {
          case EMAIL -> category == BillingCategory.EMAIL_OUTBOUND || category == BillingCategory.EMAIL_INBOUND;
          case SMS -> category == BillingCategory.SMS_PROMOTIONAL
              || category == BillingCategory.SMS_TRANSACTIONAL
              || category == BillingCategory.SMS_OTP;
          case WHATSAPP -> category == BillingCategory.WHATSAPP_MARKETING
              || category == BillingCategory.WHATSAPP_UTILITY
              || category == BillingCategory.WHATSAPP_AUTHENTICATION
              || category == BillingCategory.WHATSAPP_SERVICE;
        };
    if (!valid) throw new BadRequestException("Billing category does not belong to selected channel");
  }

  private Plan getPlan(Long planId) {
    return planRepository.findById(planId).orElseThrow(() -> new NotFoundException("Plan not found"));
  }

  private Company getCompany(Long companyId) {
    return companyRepository.findById(companyId).orElseThrow(() -> new NotFoundException("Company not found"));
  }

  private Long parseId(String value, String message) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new NotFoundException(message);
    }
  }

  private PlanResponse toPlanResponse(Plan plan) {
    return new PlanResponse(
        plan.getId().toString(),
        plan.getCode(),
        plan.getName(),
        plan.getStatus(),
        plan.getBillingCycle(),
        plan.getBasePrice(),
        plan.getCurrency(),
        plan.getVersion(),
        entitlementRepository.findByPlanId(plan.getId()).stream().map(this::toEntitlementResponse).toList(),
        planPriceRepository.findByPlanIdOrderByEffectiveFromDesc(plan.getId()).stream().map(this::toPlanPriceResponse).toList());
  }

  private EntitlementResponse toEntitlementResponse(PlanEntitlement entitlement) {
    return new EntitlementResponse(entitlement.getId().toString(), entitlement.getKey(), entitlement.getValue());
  }

  private EntitlementDefinitionResponse toEntitlementDefinitionResponse(EntitlementDefinition definition) {
    return new EntitlementDefinitionResponse(
        definition.getKey(),
        definition.getLabel(),
        definition.getDescription(),
        definition.getValueType(),
        definition.getCategory(),
        definition.getDefaultValue(),
        definition.isAllowUnlimited());
  }

  private PlanPriceResponse toPlanPriceResponse(PlanPrice price) {
    return new PlanPriceResponse(
        price.getId().toString(),
        price.getBillingCycle(),
        price.getAmount(),
        price.getCurrency(),
        price.getEffectiveFrom(),
        price.getEffectiveTo(),
        price.getStatus());
  }

  private RateCardResponse toRateCardResponse(RateCard rateCard) {
    return new RateCardResponse(
        rateCard.getId().toString(),
        rateCard.getPlan().getId().toString(),
        rateCard.getChannel(),
        rateCard.getProvider(),
        rateCard.getCountry(),
        rateCard.getCarrier(),
        rateCard.getBillingCategory(),
        rateCard.getDirection(),
        rateCard.getUnit(),
        rateCard.getPrice(),
        rateCard.getCurrency(),
        rateCard.getEffectiveFrom(),
        rateCard.getEffectiveTo(),
        rateCard.getStatus());
  }

  private CompanyRateOverrideResponse toCompanyRateOverrideResponse(CompanyRateOverride override) {
    return new CompanyRateOverrideResponse(
        override.getId().toString(),
        override.getCompany().getId().toString(),
        override.getChannel(),
        override.getProvider(),
        override.getBillingCategory(),
        override.getDirection(),
        override.getCountry(),
        override.getCarrier(),
        override.getUnit(),
        override.getPrice(),
        override.getCurrency(),
        override.getEffectiveFrom(),
        override.getEffectiveTo(),
        override.getStatus(),
        override.getReason());
  }

  private CompanySubscriptionResponse toSubscriptionResponse(CompanySubscription subscription) {
    Plan plan = subscription.getPlan();
    return new CompanySubscriptionResponse(
        subscription.getId().toString(),
        subscription.getCompany().getId().toString(),
        plan.getId().toString(),
        plan.getCode(),
        plan.getName(),
        subscription.getPlanPrice() == null ? null : subscription.getPlanPrice().getId().toString(),
        subscription.getStatus(),
        subscription.getStartedAt(),
        subscription.getEndsAt(),
        subscription.getCurrentPeriodStart(),
        subscription.getCurrentPeriodEnd(),
        subscription.getTrialEndsAt(),
        subscription.isCancelAtPeriodEnd(),
        subscription.getCancelledAt());
  }

  private String normalizeCode(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeCurrency(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeCountry(String value) {
    return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
