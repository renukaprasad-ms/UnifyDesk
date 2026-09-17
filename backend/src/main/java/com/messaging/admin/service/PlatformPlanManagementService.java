package com.messaging.admin.service;

import com.messaging.admin.dto.PlatformPlanRequest;
import com.messaging.admin.dto.PlatformPlanResponse;
import com.messaging.admin.enums.PlatformPermission;
import com.messaging.billing.entity.EntitlementDefinition;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanEntitlement;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.enums.PlanPriceStatus;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.repository.PlanEntitlementRepository;
import com.messaging.billing.repository.PlanPriceRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.billing.service.EntitlementService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.security.service.CurrentUserService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformPlanManagementService {
  private final PlanRepository plans;
  private final PlanEntitlementRepository entitlements;
  private final PlanPriceRepository planPrices;
  private final EntitlementService entitlementService;
  private final CurrentUserService currentUser;
  private final PlatformAdminAuthorizationService auth;

  @Transactional(readOnly = true)
  public Page<PlatformPlanResponse> list(String search, Pageable pageable) {
    auth.requirePermission(currentUser.currentUserId(), PlatformPermission.PLAN_VIEW);
    Page<Plan> page =
        search == null || search.isBlank()
            ? plans.findAll(pageable)
            : plans.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(
                search.trim(), search.trim(), pageable);
    return page.map(this::response);
  }

  @Transactional(readOnly = true)
  public PlatformPlanResponse get(Long id) {
    auth.requirePermission(currentUser.currentUserId(), PlatformPermission.PLAN_VIEW);
    return response(find(id));
  }

  @Transactional
  public PlatformPlanResponse create(PlatformPlanRequest request) {
    auth.requirePermission(currentUser.currentUserId(), PlatformPermission.PLAN_CREATE);
    String code = request.code().trim().toUpperCase(Locale.ROOT);
    if (plans.existsByCodeAndVersion(code, request.version())) {
      throw new BadRequestException("Plan code/version already exists");
    }

    Plan plan = new Plan();
    apply(plan, request, code);
    plan.setStatus(request.status() == null ? PlanStatus.DRAFT : request.status());
    plan = plans.saveAndFlush(plan);
    syncPrimaryPrice(plan, request);
    replaceEntitlements(plan, request.entitlements());
    validatePublishable(plan);
    return response(plan);
  }

  @Transactional
  public PlatformPlanResponse update(Long id, PlatformPlanRequest request) {
    auth.requirePermission(currentUser.currentUserId(), PlatformPermission.PLAN_UPDATE);
    Plan plan = find(id);
    String code = request.code().trim().toUpperCase(Locale.ROOT);
    if ((!plan.getCode().equals(code) || plan.getVersion() != request.version())
        && plans.existsByCodeAndVersion(code, request.version())) {
      throw new BadRequestException("Plan code/version already exists");
    }

    apply(plan, request, code);
    if (request.status() != null) plan.setStatus(request.status());
    syncPrimaryPrice(plan, request);
    replaceEntitlements(plan, request.entitlements());
    validatePublishable(plan);
    return response(plan);
  }

  @Transactional
  public void archive(Long id) {
    auth.requirePermission(currentUser.currentUserId(), PlatformPermission.PLAN_DISABLE);
    Plan plan = find(id);
    plan.setStatus(PlanStatus.RETIRED);
  }

  private Plan find(Long id) {
    return plans.findById(id).orElseThrow(() -> new NotFoundException("Plan not found"));
  }

  private void apply(Plan plan, PlatformPlanRequest request, String code) {
    plan.setCode(code);
    plan.setName(request.name().trim());
    plan.setBillingCycle(request.billingCycle());
    plan.setBasePrice(request.basePrice());
    plan.setCurrency(request.currency().toUpperCase(Locale.ROOT));
    plan.setVersion(request.version());
  }

  private void syncPrimaryPrice(Plan plan, PlatformPlanRequest request) {
    List<PlanPrice> prices =
        planPrices.findByPlanIdAndBillingCycleAndStatus(
            plan.getId(), request.billingCycle(), PlanPriceStatus.ACTIVE);
    PlanPrice price;
    if (prices.isEmpty()) {
      price = new PlanPrice();
      price.setPlan(plan);
      price.setBillingCycle(request.billingCycle());
      price.setEffectiveFrom(Instant.now());
      price.setStatus(PlanPriceStatus.ACTIVE);
    } else {
      price = prices.getFirst();
    }
    price.setAmount(request.basePrice());
    price.setCurrency(request.currency().toUpperCase(Locale.ROOT));
    planPrices.save(price);
  }

  private void replaceEntitlements(Plan plan, Map<String, String> values) {
    for (PlanEntitlement entitlement : entitlements.findByPlanId(plan.getId())) {
      entitlements.delete(entitlement);
    }
    if (values == null) return;

    values.forEach(
        (key, value) -> {
          if (key == null || key.isBlank() || value == null) return;
          EntitlementDefinition definition = entitlementService.requireDefinition(key);
          PlanEntitlement entitlement = new PlanEntitlement();
          entitlement.setPlan(plan);
          entitlement.setKey(definition.getKey());
          entitlement.setValue(entitlementService.validateValue(definition, value));
          entitlements.save(entitlement);
        });
  }

  private void validatePublishable(Plan plan) {
    if (plan.getStatus() == PlanStatus.PUBLISHED
        && planPrices.findByPlanIdAndStatus(plan.getId(), PlanPriceStatus.ACTIVE).isEmpty()) {
      throw new BadRequestException("A published plan must have at least one active price");
    }
  }

  private PlatformPlanResponse response(Plan plan) {
    Map<String, String> map = new LinkedHashMap<>();
    for (PlanEntitlement entitlement : entitlements.findByPlanId(plan.getId())) {
      map.put(entitlement.getKey(), entitlement.getValue());
    }
    return new PlatformPlanResponse(
        plan.getId().toString(),
        plan.getCode(),
        plan.getName(),
        plan.getStatus(),
        plan.getBillingCycle(),
        plan.getBasePrice(),
        plan.getCurrency(),
        plan.getVersion(),
        map);
  }
}
