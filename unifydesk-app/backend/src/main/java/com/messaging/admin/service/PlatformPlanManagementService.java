package com.messaging.admin.service;

import com.messaging.admin.dto.PlatformPlanRequest;
import com.messaging.admin.dto.PlatformPlanResponse;
import com.messaging.admin.enums.PlatformPermission;
import com.messaging.billing.dto.CreatePlanRequest;
import com.messaging.billing.dto.UpdatePlanRequest;
import com.messaging.billing.dto.UpsertEntitlementRequest;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanEntitlement;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.repository.PlanEntitlementRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.billing.service.BillingCatalogService;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.security.service.CurrentUserService;
import java.util.LinkedHashMap;
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
  private final BillingCatalogService billingCatalogService;
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
    var created =
        billingCatalogService.createPlan(
            new CreatePlanRequest(
                request.code(),
                request.name(),
                null,
                request.billingCycle(),
                request.basePrice(),
                request.currency(),
                request.version(),
                null));
    Long planId = Long.parseLong(created.planId());
    upsertEntitlements(planId, request.entitlements());
    return response(find(planId));
  }

  @Transactional
  public PlatformPlanResponse update(Long id, PlatformPlanRequest request) {
    auth.requirePermission(currentUser.currentUserId(), PlatformPermission.PLAN_UPDATE);
    Plan plan = find(id);
    String code = request.code().trim().toUpperCase(Locale.ROOT);
    if (!plan.getCode().equals(code) || plan.getVersion() != request.version()) {
      throw new ConflictException(
          "Plan code and version are immutable; create a new plan version instead");
    }

    boolean cycleChanged = plan.getBillingCycle() != request.billingCycle();
    boolean priceChanged = plan.getBasePrice().compareTo(request.basePrice()) != 0;
    boolean currencyChanged = !plan.getCurrency().equalsIgnoreCase(request.currency());

    billingCatalogService.updatePlan(
        id,
        new UpdatePlanRequest(
            request.name(),
            null,
            cycleChanged ? request.billingCycle() : null,
            priceChanged ? request.basePrice() : null,
            currencyChanged ? request.currency() : null,
            null,
            null));

    Map<String, String> existing = entitlementMap(plan.getId());
    Map<String, String> requested = normalizeEntitlements(request.entitlements());
    if (!existing.equals(requested)) {
      replaceEntitlements(id, requested);
    }
    return response(find(id));
  }

  @Transactional
  public PlatformPlanResponse publish(Long id) {
    auth.requirePermission(currentUser.currentUserId(), PlatformPermission.PLAN_UPDATE);
    billingCatalogService.updatePlan(
        id, new UpdatePlanRequest(null, null, null, null, null, PlanStatus.PUBLISHED, null));
    return response(find(id));
  }

  @Transactional
  public void archive(Long id) {
    auth.requirePermission(currentUser.currentUserId(), PlatformPermission.PLAN_DISABLE);
    billingCatalogService.updatePlan(
        id, new UpdatePlanRequest(null, null, null, null, null, PlanStatus.RETIRED, null));
  }

  private Plan find(Long id) {
    return plans.findById(id).orElseThrow(() -> new NotFoundException("Plan not found"));
  }

  private void replaceEntitlements(Long planId, Map<String, String> values) {
    for (PlanEntitlement entitlement : entitlements.findByPlanId(planId)) {
      billingCatalogService.deleteEntitlement(entitlement.getId());
    }
    upsertEntitlements(planId, values);
  }

  private void upsertEntitlements(Long planId, Map<String, String> values) {
    if (values == null) return;
    values.forEach(
        (key, value) ->
            billingCatalogService.upsertEntitlement(
                planId, new UpsertEntitlementRequest(key, value)));
  }

  private Map<String, String> normalizeEntitlements(Map<String, String> values) {
    Map<String, String> normalized = new LinkedHashMap<>();
    if (values == null) return normalized;
    values.forEach(
        (key, value) -> {
          if (key == null || key.isBlank() || value == null) return;
          normalized.put(key.trim().toUpperCase(Locale.ROOT), value.trim());
        });
    return normalized;
  }

  private Map<String, String> entitlementMap(Long planId) {
    Map<String, String> map = new LinkedHashMap<>();
    for (PlanEntitlement entitlement : entitlements.findByPlanId(planId)) {
      map.put(entitlement.getKey(), entitlement.getValue());
    }
    return map;
  }

  private PlatformPlanResponse response(Plan plan) {
    return new PlatformPlanResponse(
        plan.getId().toString(),
        plan.getCode(),
        plan.getName(),
        plan.getStatus(),
        plan.getBillingCycle(),
        plan.getBasePrice(),
        plan.getCurrency(),
        plan.getVersion(),
        entitlementMap(plan.getId()));
  }
}
