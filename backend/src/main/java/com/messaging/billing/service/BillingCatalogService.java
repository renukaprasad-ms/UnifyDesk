package com.messaging.billing.service;

import com.messaging.billing.dto.AssignSubscriptionRequest;
import com.messaging.billing.dto.CompanySubscriptionResponse;
import com.messaging.billing.dto.CompanyPlanDetailsResponse;
import com.messaging.billing.dto.CreatePlanRequest;
import com.messaging.billing.dto.CreateRateCardRequest;
import com.messaging.billing.dto.EntitlementResponse;
import com.messaging.billing.dto.PlanResponse;
import com.messaging.billing.dto.RateCardResponse;
import com.messaging.billing.dto.UpdatePlanRequest;
import com.messaging.billing.dto.UpdateRateCardRequest;
import com.messaging.billing.dto.UpsertEntitlementRequest;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanEntitlement;
import com.messaging.billing.entity.RateCard;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.PlanEntitlementRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.billing.repository.RateCardRepository;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingCatalogService {

  private final PlanRepository planRepository;
  private final PlanEntitlementRepository entitlementRepository;
  private final RateCardRepository rateCardRepository;
  private final CompanySubscriptionRepository subscriptionRepository;
  private final CompanyRepository companyRepository;

  @Transactional
  public PlanResponse createPlan(CreatePlanRequest request) {
    Plan plan = new Plan();
    plan.setCode(normalizeCode(request.code()));
    plan.setName(request.name().trim());
    plan.setBillingCycle(request.billingCycle());
    plan.setBasePrice(request.basePrice());
    plan.setCurrency(normalizeCurrency(request.currency()));
    plan.setVersion(request.version() <= 0 ? 1 : request.version());
    plan.setStatus(PlanStatus.ACTIVE);
    return toPlanResponse(planRepository.saveAndFlush(plan));
  }

  @Transactional(readOnly = true)
  public List<PlanResponse> plans() {
    return planRepository.findAll().stream().map(this::toPlanResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<PlanResponse> activePlans() {
    return planRepository.findByStatus(PlanStatus.ACTIVE).stream().map(this::toPlanResponse).toList();
  }

  @Transactional
  public PlanResponse updatePlan(Long planId, UpdatePlanRequest request) {
    Plan plan = getPlan(planId);
    if (request.name() != null && !request.name().isBlank()) {
      plan.setName(request.name().trim());
    }
    if (request.billingCycle() != null) {
      plan.setBillingCycle(request.billingCycle());
    }
    if (request.basePrice() != null) {
      plan.setBasePrice(request.basePrice());
    }
    if (request.currency() != null && !request.currency().isBlank()) {
      plan.setCurrency(normalizeCurrency(request.currency()));
    }
    if (request.status() != null) {
      plan.setStatus(request.status());
    }
    return toPlanResponse(planRepository.saveAndFlush(plan));
  }

  @Transactional
  public EntitlementResponse upsertEntitlement(Long planId, UpsertEntitlementRequest request) {
    Plan plan = getPlan(planId);
    String key = normalizeEntitlementKey(request.key());
    PlanEntitlement entitlement =
        entitlementRepository.findByPlanIdAndKey(planId, key).orElseGet(PlanEntitlement::new);
    entitlement.setPlan(plan);
    entitlement.setKey(key);
    entitlement.setValue(request.value().trim());
    return toEntitlementResponse(entitlementRepository.saveAndFlush(entitlement));
  }

  @Transactional
  public void deleteEntitlement(Long entitlementId) {
    entitlementRepository.deleteById(entitlementId);
  }

  @Transactional
  public RateCardResponse createRateCard(CreateRateCardRequest request) {
    Plan plan = getPlan(request.planId());
    RateCard rateCard = new RateCard();
    rateCard.setPlan(plan);
    rateCard.setChannel(request.channel());
    rateCard.setProvider(normalizeOptional(request.provider()));
    rateCard.setCountry(normalizeCountry(request.country()));
    rateCard.setCarrier(normalizeOptional(request.carrier()));
    rateCard.setMessageType(request.messageType());
    rateCard.setDirection(request.direction());
    rateCard.setUnit(request.unit());
    rateCard.setPrice(request.price());
    rateCard.setCurrency(normalizeCurrency(request.currency() == null ? plan.getCurrency() : request.currency()));
    rateCard.setEffectiveFrom(request.effectiveFrom());
    rateCard.setEffectiveTo(request.effectiveTo());
    rateCard.setStatus(RateCardStatus.ACTIVE);
    return toRateCardResponse(rateCardRepository.saveAndFlush(rateCard));
  }

  @Transactional(readOnly = true)
  public List<RateCardResponse> rateCards(Long planId) {
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
    if (request.price() != null) {
      rateCard.setPrice(request.price());
    }
    if (request.effectiveFrom() != null) {
      rateCard.setEffectiveFrom(request.effectiveFrom());
    }
    rateCard.setEffectiveTo(request.effectiveTo());
    if (request.status() != null) {
      rateCard.setStatus(request.status());
    }
    return toRateCardResponse(rateCardRepository.saveAndFlush(rateCard));
  }

  @Transactional
  public CompanySubscriptionResponse assignSubscription(
      Long companyId, AssignSubscriptionRequest request) {
    Company company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new NotFoundException("Company not found"));
    Plan plan = getPlan(parseId(request.planId(), "Plan not found"));
    CompanySubscription subscription = new CompanySubscription();
    subscription.setCompany(company);
    subscription.setPlan(plan);
    subscription.setStatus(request.status() == null ? SubscriptionStatus.ACTIVE : request.status());
    subscription.setStartedAt(request.startedAt() == null ? Instant.now() : request.startedAt());
    subscription.setEndsAt(request.endsAt());
    return toSubscriptionResponse(subscriptionRepository.saveAndFlush(subscription));
  }

  @Transactional(readOnly = true)
  public CompanySubscriptionResponse activeSubscription(Long companyId) {
    return subscriptionRepository
        .findFirstByCompanyIdAndStatusOrderByStartedAtDesc(companyId, SubscriptionStatus.ACTIVE)
        .map(this::toSubscriptionResponse)
        .orElseThrow(() -> new NotFoundException("Company subscription not found"));
  }

  @Transactional(readOnly = true)
  public CompanyPlanDetailsResponse companyPlanDetails(Long companyId) {
    CompanySubscription subscription =
        subscriptionRepository
            .findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
                companyId, SubscriptionStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("Company subscription not found"));
    return new CompanyPlanDetailsResponse(
        toSubscriptionResponse(subscription), toPlanResponse(subscription.getPlan()));
  }

  private Plan getPlan(Long planId) {
    return planRepository.findById(planId).orElseThrow(() -> new NotFoundException("Plan not found"));
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
        entitlementRepository.findByPlanId(plan.getId()).stream()
            .map(this::toEntitlementResponse)
            .toList());
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
        rateCard.getMessageType(),
        rateCard.getDirection(),
        rateCard.getUnit(),
        rateCard.getPrice(),
        rateCard.getCurrency(),
        rateCard.getEffectiveFrom(),
        rateCard.getEffectiveTo(),
        rateCard.getStatus());
  }

  private CompanySubscriptionResponse toSubscriptionResponse(CompanySubscription subscription) {
    Plan plan = subscription.getPlan();
    return new CompanySubscriptionResponse(
        subscription.getId().toString(),
        subscription.getCompany().getId().toString(),
        plan.getId().toString(),
        plan.getCode(),
        plan.getName(),
        subscription.getStatus(),
        subscription.getStartedAt(),
        subscription.getEndsAt());
  }

  private String normalizeCode(String value) {
    return value.trim().toUpperCase(java.util.Locale.ROOT);
  }

  private String normalizeEntitlementKey(String value) {
    return value.trim().toUpperCase(java.util.Locale.ROOT);
  }

  private String normalizeCurrency(String value) {
    return value.trim().toUpperCase(java.util.Locale.ROOT);
  }

  private String normalizeCountry(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toUpperCase(java.util.Locale.ROOT);
  }

  private String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
