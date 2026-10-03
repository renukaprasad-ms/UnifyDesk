package com.messaging.billing.service;

import com.messaging.billing.bootstrap.SystemEntitlementCatalogInitializer;
import com.messaging.billing.dto.AdminGrantSubscriptionRequest;
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
import com.messaging.billing.enums.PaymentPolicy;
import com.messaging.billing.enums.EntitlementValueType;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.PlanVisibility;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.enums.SubscriptionTransitionType;
import com.messaging.billing.repository.CompanyRateOverrideRepository;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.EntitlementDefinitionRepository;
import com.messaging.billing.repository.PlanEntitlementRepository;
import com.messaging.billing.repository.PlanPriceRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.billing.repository.RateCardRepository;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingCatalogService {

  private final PlanRepository planRepository;
  private final PlanPriceRepository planPriceRepository;
  private final EntitlementDefinitionRepository entitlementDefinitionRepository;
  private final PlanEntitlementRepository entitlementRepository;
  private final RateCardRepository rateCardRepository;
  private final CompanyRateOverrideRepository companyRateOverrideRepository;
  private final CompanySubscriptionRepository subscriptionRepository;
  private final CompanyRepository companyRepository;
  private final SystemEntitlementCatalogInitializer entitlementCatalogInitializer;
  private final PlanEligibilityService planEligibilityService;
  private final SubscriptionLifecycleService subscriptionLifecycleService;

  @Transactional
  public PlanResponse createPlan(CreatePlanRequest request) {
    String code = normalizeCode(request.code());
    int version = request.version() <= 0 ? nextVersion(code) : request.version();
    if (planRepository.existsByCodeAndVersion(code, version)) {
      throw new ConflictException("Plan code/version already exists");
    }

    Plan plan = new Plan();
    plan.setCode(code);
    plan.setName(request.name().trim());
    plan.setDescription(normalizeOptional(request.description()));
    plan.setBillingCycle(request.billingCycle());
    plan.setBasePrice(request.basePrice());
    plan.setCurrency(normalizeCurrency(request.currency()));
    plan.setVersion(version);
    plan.setStatus(PlanStatus.DRAFT);
    plan.setVisibility(
        request.visibility() == null ? PlanVisibility.PUBLIC : request.visibility());
    planRepository.saveAndFlush(plan);

    PlanPrice price = new PlanPrice();
    price.setPlan(plan);
    price.setBillingCycle(request.billingCycle());
    price.setAmount(request.basePrice());
    price.setCurrency(normalizeCurrency(request.currency()));
    price.setEffectiveFrom(Instant.now());
    price.setStatus(RateCardStatus.ACTIVE);
    planPriceRepository.saveAndFlush(price);

    return toPlanResponse(plan);
  }

  @Transactional(readOnly = true)
  public List<PlanResponse> plans() {
    return planRepository.findAll().stream()
        .sorted(Comparator.comparing(Plan::getCode).thenComparing(Plan::getVersion))
        .map(this::toPlanResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<PlanResponse> activePlans() {
    return planEligibilityService.publicPlans().stream().map(this::toPlanResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<PlanResponse> eligiblePlans(Long companyId) {
    return planEligibilityService.eligiblePlans(companyId, Instant.now()).stream()
        .map(this::toPlanResponse)
        .toList();
  }

  @Transactional
  public PlanResponse updatePlan(Long planId, UpdatePlanRequest request) {
    Plan plan = getPlan(planId);

    if ((request.name() != null && !request.name().isBlank())
        || request.description() != null
        || request.billingCycle() != null
        || request.basePrice() != null
        || request.currency() != null
        || request.visibility() != null) {
      assertDraft(plan);
      if (request.name() != null && !request.name().isBlank()) plan.setName(request.name().trim());
      if (request.description() != null) {
        plan.setDescription(normalizeOptional(request.description()));
      }
      if (request.billingCycle() != null) plan.setBillingCycle(request.billingCycle());
      if (request.basePrice() != null) plan.setBasePrice(request.basePrice());
      if (request.currency() != null && !request.currency().isBlank()) {
        plan.setCurrency(normalizeCurrency(request.currency()));
      }
      if (request.visibility() != null) {
        plan.setVisibility(request.visibility());
      }
    }

    if (request.status() != null && request.status() != plan.getStatus()) {
      transitionPlan(plan, request.status());
    }

    return toPlanResponse(planRepository.saveAndFlush(plan));
  }

  @Transactional
  public List<EntitlementDefinitionResponse> entitlementDefinitions() {
    entitlementCatalogInitializer.ensureCatalog();
    return entitlementDefinitionRepository.findByActiveTrueOrderByCategoryAscNameAsc().stream()
        .map(this::toEntitlementDefinitionResponse)
        .toList();
  }

  @Transactional
  public EntitlementResponse upsertEntitlement(Long planId, UpsertEntitlementRequest request) {
    Plan plan = getPlan(planId);
    assertEntitlementsMutable(plan);
    String key = normalizeEntitlementKey(request.key());
    EntitlementDefinition definition =
        entitlementDefinitionRepository
            .findByKey(key)
            .filter(EntitlementDefinition::isActive)
            .orElseThrow(() -> new BadRequestException("Unknown entitlement key: " + key));
    String value = request.value().trim();
    validateEntitlementValue(definition, value);

    PlanEntitlement entitlement =
        entitlementRepository.findByPlanIdAndKey(planId, key).orElseGet(PlanEntitlement::new);
    entitlement.setPlan(plan);
    entitlement.setKey(key);
    entitlement.setValue(value);
    return toEntitlementResponse(entitlementRepository.saveAndFlush(entitlement));
  }

  @Transactional
  public void deleteEntitlement(Long entitlementId) {
    PlanEntitlement entitlement =
        entitlementRepository
            .findById(entitlementId)
            .orElseThrow(() -> new NotFoundException("Entitlement not found"));
    assertEntitlementsMutable(entitlement.getPlan());
    entitlementRepository.delete(entitlement);
  }

  @Transactional
  public PlanPriceResponse createPlanPrice(Long planId, CreatePlanPriceRequest request) {
    Plan plan = getPlan(planId);
    assertDraft(plan);
    validatePeriod(request.effectiveFrom(), request.effectiveTo());
    String currency = normalizeCurrency(request.currency());
    assertNoOverlappingPlanPrice(
        planId,
        request.billingCycle(),
        currency,
        request.effectiveFrom(),
        request.effectiveTo(),
        null);

    PlanPrice price = new PlanPrice();
    price.setPlan(plan);
    price.setBillingCycle(request.billingCycle());
    price.setAmount(request.amount());
    price.setCurrency(currency);
    price.setEffectiveFrom(request.effectiveFrom());
    price.setEffectiveTo(request.effectiveTo());
    price.setStatus(RateCardStatus.ACTIVE);
    return toPlanPriceResponse(planPriceRepository.saveAndFlush(price));
  }

  @Transactional(readOnly = true)
  public List<PlanPriceResponse> planPrices(Long planId) {
    getPlan(planId);
    return planPriceRepository.findByPlanIdOrderByEffectiveFromDesc(planId).stream()
        .map(this::toPlanPriceResponse)
        .toList();
  }

  @Transactional
  public PlanPriceResponse updatePlanPrice(Long planPriceId, UpdatePlanPriceRequest request) {
    PlanPrice price =
        planPriceRepository
            .findById(planPriceId)
            .orElseThrow(() -> new NotFoundException("Plan price not found"));
    assertDraft(price.getPlan());

    BigDecimal amount = request.amount() == null ? price.getAmount() : request.amount();
    String currency =
        request.currency() == null ? price.getCurrency() : normalizeCurrency(request.currency());
    Instant from =
        request.effectiveFrom() == null ? price.getEffectiveFrom() : request.effectiveFrom();
    Instant to = request.effectiveTo() == null ? price.getEffectiveTo() : request.effectiveTo();
    validatePeriod(from, to);
    assertNoOverlappingPlanPrice(
        price.getPlan().getId(), price.getBillingCycle(), currency, from, to, price.getId());

    price.setAmount(amount);
    price.setCurrency(currency);
    price.setEffectiveFrom(from);
    price.setEffectiveTo(to);
    if (request.status() != null) price.setStatus(request.status());
    return toPlanPriceResponse(planPriceRepository.saveAndFlush(price));
  }

  @Transactional
  public RateCardResponse createRateCard(CreateRateCardRequest request) {
    Plan plan = getPlan(parseId(request.planId(), "Plan not found"));
    assertDraft(plan);
    validateRateDimensions(request.channel(), request.provider(), request.billingCategory());
    validatePeriod(request.effectiveFrom(), request.effectiveTo());
    String country = normalizeCountry(request.country());
    String carrier = normalizeOptional(request.carrier());
    assertNoOverlappingRateCard(plan.getId(), request, country, carrier, null);

    RateCard rateCard = new RateCard();
    rateCard.setPlan(plan);
    rateCard.setChannel(request.channel());
    rateCard.setProvider(request.provider());
    rateCard.setCountry(country);
    rateCard.setCarrier(carrier);
    rateCard.setBillingCategory(request.billingCategory());
    rateCard.setDirection(request.direction());
    rateCard.setUnit(request.unit());
    rateCard.setPrice(request.price());
    rateCard.setCurrency(
        normalizeCurrency(request.currency() == null ? plan.getCurrency() : request.currency()));
    rateCard.setEffectiveFrom(request.effectiveFrom());
    rateCard.setEffectiveTo(request.effectiveTo());
    rateCard.setStatus(RateCardStatus.ACTIVE);
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
    assertDraft(rateCard.getPlan());

    Instant from =
        request.effectiveFrom() == null ? rateCard.getEffectiveFrom() : request.effectiveFrom();
    Instant to = request.effectiveTo() == null ? rateCard.getEffectiveTo() : request.effectiveTo();
    validatePeriod(from, to);
    assertNoOverlappingRateCard(rateCard, from, to);

    if (request.price() != null) rateCard.setPrice(request.price());
    rateCard.setEffectiveFrom(from);
    rateCard.setEffectiveTo(to);
    if (request.status() != null) rateCard.setStatus(request.status());
    return toRateCardResponse(rateCardRepository.saveAndFlush(rateCard));
  }

  @Transactional
  public CompanyRateOverrideResponse createCompanyRateOverride(
      Long companyId, Long createdByUserId, CreateCompanyRateOverrideRequest request) {
    Company company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new NotFoundException("Company not found"));
    validateRateDimensions(request.channel(), request.provider(), request.billingCategory());
    validatePeriod(request.effectiveFrom(), request.effectiveTo());
    String country = normalizeCountry(request.country());
    String carrier = normalizeOptional(request.carrier());
    assertNoOverlappingOverride(companyId, request, country, carrier, null);

    CompanyRateOverride override = new CompanyRateOverride();
    override.setCompany(company);
    override.setChannel(request.channel());
    override.setProvider(request.provider());
    override.setBillingCategory(request.billingCategory());
    override.setDirection(request.direction());
    override.setCountry(country);
    override.setCarrier(carrier);
    override.setUnit(request.unit());
    override.setPrice(request.price());
    override.setCurrency(normalizeCurrency(request.currency()));
    override.setEffectiveFrom(request.effectiveFrom());
    override.setEffectiveTo(request.effectiveTo());
    override.setStatus(RateCardStatus.ACTIVE);
    override.setCreatedByUserId(createdByUserId);
    override.setReason(normalizeOptional(request.reason()));
    return toCompanyRateOverrideResponse(companyRateOverrideRepository.saveAndFlush(override));
  }

  @Transactional(readOnly = true)
  public List<CompanyRateOverrideResponse> companyRateOverrides(Long companyId) {
    return companyRateOverrideRepository.findByCompanyIdOrderByEffectiveFromDesc(companyId).stream()
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

    Instant from =
        request.effectiveFrom() == null ? override.getEffectiveFrom() : request.effectiveFrom();
    Instant to = request.effectiveTo() == null ? override.getEffectiveTo() : request.effectiveTo();
    validatePeriod(from, to);
    assertNoOverlappingOverride(override, from, to);

    if (request.price() != null) override.setPrice(request.price());
    if (request.currency() != null) override.setCurrency(normalizeCurrency(request.currency()));
    override.setEffectiveFrom(from);
    override.setEffectiveTo(to);
    if (request.status() != null) override.setStatus(request.status());
    if (request.reason() != null) override.setReason(normalizeOptional(request.reason()));
    return toCompanyRateOverrideResponse(companyRateOverrideRepository.saveAndFlush(override));
  }

  @Transactional
  public CompanySubscriptionResponse assignSubscription(
      Long companyId, AssignSubscriptionRequest request) {
    Company company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new NotFoundException("Company not found"));
    Plan plan = getPlan(parseId(request.planId(), "Plan not found"));
    if (plan.getStatus() != PlanStatus.PUBLISHED) {
      throw new BadRequestException("Only published plans can be assigned");
    }
    planEligibilityService.assertCustomerEligible(companyId, plan, Instant.now());

    PlanPrice planPrice = null;
    if (request.planPriceId() != null && !request.planPriceId().isBlank()) {
      planPrice =
          planPriceRepository
              .findById(parseId(request.planPriceId(), "Plan price not found"))
              .orElseThrow(() -> new NotFoundException("Plan price not found"));
      if (!planPrice.getPlan().getId().equals(plan.getId())
          || planPrice.getStatus() != RateCardStatus.ACTIVE) {
        throw new BadRequestException("Plan price does not belong to the selected plan");
      }
    }
    if ((planPrice != null && planPrice.getAmount().compareTo(BigDecimal.ZERO) > 0)
        || plan.getBasePrice().compareTo(BigDecimal.ZERO) > 0) {
      throw new BadRequestException(
          "Paid subscriptions require provider confirmation or an explicit manual grant");
    }

    Instant now = Instant.now();
    CompanySubscription current = subscriptionLifecycleService.currentOptional(companyId).orElse(null);
    if (current != null) {
      if (current.getPlan().getId().equals(plan.getId())) {
        return toSubscriptionResponse(current);
      }
      throw new ConflictException(
          "A current subscription already exists; use the upgrade or downgrade contract");
    }

    CompanySubscription subscription = new CompanySubscription();
    subscription.setCompany(company);
    subscription.setPlan(plan);
    subscription.setPlanPrice(planPrice);
    subscription.setStatus(request.status() == null ? SubscriptionStatus.ACTIVE : request.status());
    subscription.setStartedAt(request.startedAt() == null ? now : request.startedAt());
    subscription.setCurrentPeriodStart(subscription.getStartedAt());
    subscription.setCurrentPeriodEnd(request.currentPeriodEnd());
    subscription.setTrialEndsAt(request.trialEndsAt());
    subscription.setEndsAt(request.endsAt());
    CompanySubscription saved = subscriptionRepository.saveAndFlush(subscription);
    subscriptionLifecycleService.recordCreated(
        saved, null, "Zero-cost subscription activated", SubscriptionTransitionType.CREATED);
    return toSubscriptionResponse(saved);
  }

  @Transactional
  public CompanySubscriptionResponse grantSubscription(
      Long companyId, Long actorUserId, AdminGrantSubscriptionRequest request) {
    if (request.paymentPolicy() == PaymentPolicy.CASHFREE) {
      throw new BadRequestException(
          "CASHFREE activation requires provider-confirmed payment truth");
    }
    Company company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new NotFoundException("Company not found"));
    Plan plan = getPlan(parseId(request.planId(), "Plan not found"));
    if (plan.getStatus() != PlanStatus.PUBLISHED) {
      throw new BadRequestException("Only published plans can be granted");
    }
    if (plan.getVisibility() == PlanVisibility.PRIVATE) {
      planEligibilityService.assertCustomerEligible(companyId, plan, Instant.now());
    }
    PlanPrice planPrice = null;
    if (request.planPriceId() != null && !request.planPriceId().isBlank()) {
      planPrice =
          planPriceRepository
              .findById(parseId(request.planPriceId(), "Plan price not found"))
              .orElseThrow(() -> new NotFoundException("Plan price not found"));
      if (!planPrice.getPlan().getId().equals(plan.getId())
          || planPrice.getStatus() != RateCardStatus.ACTIVE) {
        throw new BadRequestException("Plan price does not belong to the selected plan");
      }
    }
    if (plan.getBasePrice().compareTo(BigDecimal.ZERO) > 0 && planPrice == null) {
      throw new BadRequestException("Paid manual grants require an explicit plan price");
    }

    Instant now = Instant.now();
    if (subscriptionLifecycleService.currentOptional(companyId).isPresent()) {
      subscriptionLifecycleService.cancelImmediately(
          companyId,
          actorUserId,
          "Superseded by manual grant: " + request.reason().trim(),
          now);
    }
    CompanySubscription subscription = new CompanySubscription();
    subscription.setCompany(company);
    subscription.setPlan(plan);
    subscription.setPlanPrice(planPrice);
    SubscriptionStatus status =
        request.status() == null ? SubscriptionStatus.ACTIVE : request.status();
    if (status != SubscriptionStatus.ACTIVE && status != SubscriptionStatus.TRIALING) {
      throw new BadRequestException("Manual grants may start only ACTIVE or TRIALING");
    }
    subscription.setStatus(status);
    subscription.setStartedAt(request.startedAt() == null ? now : request.startedAt());
    subscription.setCurrentPeriodStart(subscription.getStartedAt());
    subscription.setCurrentPeriodEnd(request.currentPeriodEnd());
    subscription.setTrialEndsAt(request.trialEndsAt());
    subscription.setEndsAt(request.endsAt());
    subscription.setPaymentPolicy(request.paymentPolicy());
    subscription.setExternalContractReference(
        normalizeOptional(request.externalContractReference()));
    CompanySubscription saved = subscriptionRepository.saveAndFlush(subscription);
    subscriptionLifecycleService.recordCreated(
        saved,
        actorUserId,
        request.reason().trim(),
        SubscriptionTransitionType.ADMIN_GRANTED);
    return toSubscriptionResponse(saved);
  }

  @Transactional
  public CompanySubscriptionResponse selectFreeSubscription(
      Long companyId, AssignSubscriptionRequest request) {
    Plan plan = getPlan(parseId(request.planId(), "Plan not found"));
    if (plan.getStatus() != PlanStatus.PUBLISHED) {
      throw new BadRequestException("Plan is not available");
    }
    planEligibilityService.assertCustomerEligible(companyId, plan, Instant.now());

    PlanPrice freePrice =
        planPriceRepository
            .findByPlanIdAndStatusOrderByEffectiveFromDesc(plan.getId(), RateCardStatus.ACTIVE)
            .stream()
            .filter(price -> price.getAmount().compareTo(BigDecimal.ZERO) == 0)
            .findFirst()
            .orElseThrow(() -> new BadRequestException("Paid plans require checkout"));

    CompanySubscription current = currentEffectiveSubscription(companyId).orElse(null);
    if (current != null && current.getPlan().getId().equals(plan.getId())) {
      return toSubscriptionResponse(current);
    }

    AssignSubscriptionRequest resolvedRequest =
        new AssignSubscriptionRequest(
            request.planId(),
            freePrice.getId().toString(),
            request.status(),
            request.startedAt(),
            request.endsAt(),
            request.trialEndsAt(),
            request.currentPeriodEnd());
    return assignSubscription(companyId, resolvedRequest);
  }

  @Transactional
  public void cancelForCompanyDeletion(Long companyId) {
    subscriptionLifecycleService.cancelForCompanyDeletion(companyId, Instant.now());
  }

  @Transactional
  public CompanySubscriptionResponse cancelSubscription(Long companyId, boolean atPeriodEnd) {
    if (!atPeriodEnd) {
      throw new BadRequestException(
          "Customer immediate cancellation is not supported; cancellation is period-end only");
    }
    return toSubscriptionResponse(subscriptionLifecycleService.scheduleCancellation(companyId, null));
  }

  @Transactional
  public CompanySubscriptionResponse cancelImmediatelyByAdmin(
      Long companyId, Long actorUserId, String reason) {
    return toSubscriptionResponse(
        subscriptionLifecycleService.cancelImmediately(
            companyId, actorUserId, reason, Instant.now()));
  }

  @Transactional(readOnly = true)
  public CompanySubscriptionResponse activeSubscription(Long companyId) {
    return toSubscriptionResponse(effectiveSubscription(companyId));
  }

  @Transactional(readOnly = true)
  public CompanyPlanDetailsResponse companyPlanDetails(Long companyId) {
    CompanySubscription subscription = effectiveSubscription(companyId);
    return new CompanyPlanDetailsResponse(
        toSubscriptionResponse(subscription), toPlanResponse(subscription.getPlan()));
  }

  private CompanySubscription effectiveSubscription(Long companyId) {
    return subscriptionLifecycleService.current(companyId);
  }

  private java.util.Optional<CompanySubscription> currentEffectiveSubscription(Long companyId) {
    return subscriptionRepository
        .findFirstByCompanyIdAndStatusOrderByStartedAtDesc(companyId, SubscriptionStatus.ACTIVE)
        .or(
            () ->
                subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
                    companyId, SubscriptionStatus.TRIALING));
  }

  private void transitionPlan(Plan plan, PlanStatus target) {
    if (plan.getStatus() == PlanStatus.RETIRED) {
      throw new ConflictException("Retired plans cannot be changed");
    }
    if (target == PlanStatus.DRAFT && plan.getStatus() != PlanStatus.DRAFT) {
      throw new ConflictException("Published plans cannot return to draft");
    }
    if (target == PlanStatus.PUBLISHED) {
      validatePublishable(plan);
    }
    plan.setStatus(target);
  }

  private void validatePublishable(Plan plan) {
    boolean hasPrice =
        !planPriceRepository
            .findByPlanIdAndStatusOrderByEffectiveFromDesc(plan.getId(), RateCardStatus.ACTIVE)
            .isEmpty();
    if (!hasPrice) throw new BadRequestException("Plan requires at least one active price");
  }

  private void assertDraft(Plan plan) {
    if (plan.getStatus() != PlanStatus.DRAFT) {
      throw new ConflictException(
          "Published commercial terms are immutable; create a new plan version instead");
    }
  }

  private void assertEntitlementsMutable(Plan plan) {
    assertDraft(plan);
  }

  private void validateRateDimensions(
      ChannelType channel,
      com.messaging.channel.enums.ProviderType provider,
      BillingCategory category) {
    if (!provider.supports(channel)) {
      throw new BadRequestException(provider + " does not support channel " + channel);
    }
    boolean valid =
        switch (channel) {
          case EMAIL ->
              category == BillingCategory.EMAIL_OUTBOUND
                  || category == BillingCategory.EMAIL_INBOUND;
          case SMS ->
              category == BillingCategory.SMS_PROMOTIONAL
                  || category == BillingCategory.SMS_TRANSACTIONAL
                  || category == BillingCategory.SMS_OTP;
          case WHATSAPP ->
              category == BillingCategory.WHATSAPP_MARKETING
                  || category == BillingCategory.WHATSAPP_UTILITY
                  || category == BillingCategory.WHATSAPP_AUTHENTICATION
                  || category == BillingCategory.WHATSAPP_SERVICE;
        };
    if (!valid)
      throw new BadRequestException("Billing category does not belong to channel " + channel);
  }

  private void validatePeriod(Instant from, Instant to) {
    if (from != null && to != null && !to.isAfter(from)) {
      throw new BadRequestException("effectiveTo must be after effectiveFrom");
    }
  }

  private void assertNoOverlappingPlanPrice(
      Long planId,
      com.messaging.billing.enums.BillingCycle cycle,
      String currency,
      Instant from,
      Instant to,
      Long excludedId) {
    Instant start = from == null ? Instant.now() : from;
    boolean overlap =
        planPriceRepository
            .findByPlanIdAndStatusOrderByEffectiveFromDesc(planId, RateCardStatus.ACTIVE)
            .stream()
            .filter(existing -> excludedId == null || !existing.getId().equals(excludedId))
            .filter(existing -> existing.getBillingCycle() == cycle)
            .filter(existing -> existing.getCurrency().equalsIgnoreCase(currency))
            .anyMatch(
                existing ->
                    periodsOverlap(
                        start, to, existing.getEffectiveFrom(), existing.getEffectiveTo()));
    if (overlap) throw new ConflictException("An active plan price overlaps this effective period");
  }

  private void assertNoOverlappingRateCard(
      Long planId, CreateRateCardRequest request, String country, String carrier, Long excludedId) {
    Instant start = request.effectiveFrom() == null ? Instant.now() : request.effectiveFrom();
    boolean overlap =
        rateCardRepository.findByPlanIdAndStatus(planId, RateCardStatus.ACTIVE).stream()
            .filter(existing -> excludedId == null || !existing.getId().equals(excludedId))
            .filter(existing -> existing.getChannel() == request.channel())
            .filter(existing -> existing.getProvider() == request.provider())
            .filter(existing -> existing.getBillingCategory() == request.billingCategory())
            .filter(existing -> existing.getDirection() == request.direction())
            .filter(existing -> existing.getUnit() == request.unit())
            .filter(existing -> equalOptional(existing.getCountry(), country))
            .filter(existing -> equalOptional(existing.getCarrier(), carrier))
            .anyMatch(
                existing ->
                    periodsOverlap(
                        start,
                        request.effectiveTo(),
                        existing.getEffectiveFrom(),
                        existing.getEffectiveTo()));
    if (overlap) throw new ConflictException("An active rate card overlaps this pricing rule");
  }

  private void assertNoOverlappingRateCard(RateCard card, Instant from, Instant to) {
    boolean overlap =
        rateCardRepository
            .findByPlanIdAndStatus(card.getPlan().getId(), RateCardStatus.ACTIVE)
            .stream()
            .filter(existing -> !existing.getId().equals(card.getId()))
            .filter(existing -> existing.getChannel() == card.getChannel())
            .filter(existing -> existing.getProvider() == card.getProvider())
            .filter(existing -> existing.getBillingCategory() == card.getBillingCategory())
            .filter(existing -> existing.getDirection() == card.getDirection())
            .filter(existing -> existing.getUnit() == card.getUnit())
            .filter(existing -> equalOptional(existing.getCountry(), card.getCountry()))
            .filter(existing -> equalOptional(existing.getCarrier(), card.getCarrier()))
            .anyMatch(
                existing ->
                    periodsOverlap(
                        from, to, existing.getEffectiveFrom(), existing.getEffectiveTo()));
    if (overlap) throw new ConflictException("An active rate card overlaps this pricing rule");
  }

  private void assertNoOverlappingOverride(
      Long companyId,
      CreateCompanyRateOverrideRequest request,
      String country,
      String carrier,
      Long excludedId) {
    Instant start = request.effectiveFrom() == null ? Instant.now() : request.effectiveFrom();
    boolean overlap =
        companyRateOverrideRepository
            .findByCompanyIdAndStatus(companyId, RateCardStatus.ACTIVE)
            .stream()
            .filter(existing -> excludedId == null || !existing.getId().equals(excludedId))
            .filter(existing -> existing.getChannel() == request.channel())
            .filter(existing -> existing.getProvider() == request.provider())
            .filter(existing -> existing.getBillingCategory() == request.billingCategory())
            .filter(existing -> existing.getDirection() == request.direction())
            .filter(existing -> existing.getUnit() == request.unit())
            .filter(existing -> equalOptional(existing.getCountry(), country))
            .filter(existing -> equalOptional(existing.getCarrier(), carrier))
            .anyMatch(
                existing ->
                    periodsOverlap(
                        start,
                        request.effectiveTo(),
                        existing.getEffectiveFrom(),
                        existing.getEffectiveTo()));
    if (overlap)
      throw new ConflictException("An active company override overlaps this pricing rule");
  }

  private void assertNoOverlappingOverride(CompanyRateOverride rule, Instant from, Instant to) {
    boolean overlap =
        companyRateOverrideRepository
            .findByCompanyIdAndStatus(rule.getCompany().getId(), RateCardStatus.ACTIVE)
            .stream()
            .filter(existing -> !existing.getId().equals(rule.getId()))
            .filter(existing -> existing.getChannel() == rule.getChannel())
            .filter(existing -> existing.getProvider() == rule.getProvider())
            .filter(existing -> existing.getBillingCategory() == rule.getBillingCategory())
            .filter(existing -> existing.getDirection() == rule.getDirection())
            .filter(existing -> existing.getUnit() == rule.getUnit())
            .filter(existing -> equalOptional(existing.getCountry(), rule.getCountry()))
            .filter(existing -> equalOptional(existing.getCarrier(), rule.getCarrier()))
            .anyMatch(
                existing ->
                    periodsOverlap(
                        from, to, existing.getEffectiveFrom(), existing.getEffectiveTo()));
    if (overlap)
      throw new ConflictException("An active company override overlaps this pricing rule");
  }

  private boolean periodsOverlap(Instant aFrom, Instant aTo, Instant bFrom, Instant bTo) {
    boolean aBeforeBEnds = bTo == null || aFrom.isBefore(bTo);
    boolean bBeforeAEnds = aTo == null || bFrom.isBefore(aTo);
    return aBeforeBEnds && bBeforeAEnds;
  }

  private boolean equalOptional(String left, String right) {
    if (left == null && right == null) return true;
    return left != null && right != null && left.equalsIgnoreCase(right);
  }

  private void validateEntitlementValue(EntitlementDefinition definition, String value) {
    try {
      if (definition.getValueType() == EntitlementValueType.INTEGER) {
        if (!value.equalsIgnoreCase("unlimited")) {
          long parsed = Long.parseLong(value);
          if (parsed < -1) throw new NumberFormatException();
        }
      } else if (definition.getValueType() == EntitlementValueType.BOOLEAN) {
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
          throw new IllegalArgumentException();
        }
      } else if (definition.getValueType() == EntitlementValueType.DECIMAL) {
        if (new BigDecimal(value).compareTo(BigDecimal.ZERO) < 0) throw new NumberFormatException();
      }
    } catch (RuntimeException exception) {
      throw new BadRequestException(
          "Invalid "
              + definition.getValueType().name().toLowerCase(Locale.ROOT)
              + " value for "
              + definition.getKey());
    }
  }

  private int nextVersion(String code) {
    return planRepository.findAll().stream()
            .filter(plan -> plan.getCode().equalsIgnoreCase(code))
            .mapToInt(Plan::getVersion)
            .max()
            .orElse(0)
        + 1;
  }

  private Plan getPlan(Long planId) {
    return planRepository
        .findById(planId)
        .orElseThrow(() -> new NotFoundException("Plan not found"));
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
        plan.getDescription(),
        plan.getStatus(),
        plan.getVisibility(),
        plan.getBillingCycle(),
        plan.getBasePrice(),
        plan.getCurrency(),
        plan.getVersion(),
        planPriceRepository.findByPlanIdOrderByEffectiveFromDesc(plan.getId()).stream()
            .map(this::toPlanPriceResponse)
            .toList(),
        entitlementRepository.findByPlanId(plan.getId()).stream()
            .map(this::toEntitlementResponse)
            .toList());
  }

  private PlanPriceResponse toPlanPriceResponse(PlanPrice price) {
    return new PlanPriceResponse(
        price.getId().toString(),
        price.getPlan().getId().toString(),
        price.getBillingCycle(),
        price.getAmount(),
        price.getCurrency(),
        price.getEffectiveFrom(),
        price.getEffectiveTo(),
        price.getStatus());
  }

  private EntitlementDefinitionResponse toEntitlementDefinitionResponse(
      EntitlementDefinition definition) {
    return new EntitlementDefinitionResponse(
        definition.getId().toString(),
        definition.getKey(),
        definition.getName(),
        definition.getDescription(),
        definition.getValueType(),
        definition.getCategory(),
        definition.getDefaultValue(),
        definition.isActive());
  }

  private EntitlementResponse toEntitlementResponse(PlanEntitlement entitlement) {
    return new EntitlementResponse(
        entitlement.getId().toString(), entitlement.getKey(), entitlement.getValue());
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
        override.getCreatedByUserId() == null ? null : override.getCreatedByUserId().toString(),
        override.getReason());
  }

  private CompanySubscriptionResponse toSubscriptionResponse(CompanySubscription subscription) {
    Plan plan = subscription.getPlan();
    return new CompanySubscriptionResponse(
        subscription.getId().toString(),
        subscription.getCompany().getId().toString(),
        plan.getId().toString(),
        subscription.getPlanPrice() == null ? null : subscription.getPlanPrice().getId().toString(),
        plan.getCode(),
        plan.getName(),
        subscription.getStatus(),
        subscription.getStartedAt(),
        subscription.getEndsAt(),
        subscription.getCurrentPeriodStart(),
        subscription.getCurrentPeriodEnd(),
        subscription.getBillingAnchor(),
        subscription.getTrialEndsAt(),
        subscription.getGraceEndsAt(),
        subscription.isCancelAtPeriodEnd(),
        subscription.getCancelledAt(),
        subscription.getCancellationEffectiveAt(),
        subscription.getPaymentPolicy(),
        subscription.getExternalContractReference());
  }

  private String normalizeCode(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeEntitlementKey(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeCurrency(String value) {
    String currency = value.trim().toUpperCase(Locale.ROOT);
    if (!"INR".equals(currency)) {
      throw new BadRequestException("Billing R1 supports INR only");
    }
    return currency;
  }

  private String normalizeCountry(String value) {
    return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
