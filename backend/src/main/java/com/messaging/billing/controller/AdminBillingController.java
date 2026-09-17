package com.messaging.billing.controller;

import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.billing.dto.AssignSubscriptionRequest;
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
import com.messaging.billing.service.BillingCatalogService;
import com.messaging.common.response.ApiResponse;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/billing")
@RequiredArgsConstructor
public class AdminBillingController {

  private final BillingCatalogService billingCatalogService;
  private final PlatformAdminAuthorizationService adminAuthorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/plans")
  public ResponseEntity<ApiResponse<List<PlanResponse>>> plans() {
    requireSuperAdmin();
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), billingCatalogService.plans(), "Plans fetched"));
  }

  @PostMapping("/plans")
  public ResponseEntity<ApiResponse<PlanResponse>> createPlan(
      @Valid @RequestBody CreatePlanRequest request) {
    requireSuperAdmin();
    PlanResponse response = billingCatalogService.createPlan(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response, "Plan created"));
  }

  @PatchMapping("/plans/{planId}")
  public ResponseEntity<ApiResponse<PlanResponse>> updatePlan(
      @PathVariable Long planId, @Valid @RequestBody UpdatePlanRequest request) {
    requireSuperAdmin();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), billingCatalogService.updatePlan(planId, request), "Plan updated"));
  }

  @GetMapping("/plans/{planId}/prices")
  public ResponseEntity<ApiResponse<List<PlanPriceResponse>>> planPrices(@PathVariable Long planId) {
    requireSuperAdmin();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), billingCatalogService.planPrices(planId), "Plan prices fetched"));
  }

  @PostMapping("/plans/{planId}/prices")
  public ResponseEntity<ApiResponse<PlanPriceResponse>> createPlanPrice(
      @PathVariable Long planId, @Valid @RequestBody CreatePlanPriceRequest request) {
    requireSuperAdmin();
    PlanPriceResponse response = billingCatalogService.createPlanPrice(planId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response, "Plan price created"));
  }

  @PatchMapping("/plan-prices/{planPriceId}")
  public ResponseEntity<ApiResponse<PlanPriceResponse>> updatePlanPrice(
      @PathVariable Long planPriceId, @Valid @RequestBody UpdatePlanPriceRequest request) {
    requireSuperAdmin();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            billingCatalogService.updatePlanPrice(planPriceId, request),
            "Plan price updated"));
  }

  @GetMapping("/entitlement-definitions")
  public ResponseEntity<ApiResponse<List<EntitlementDefinitionResponse>>> entitlementDefinitions() {
    requireSuperAdmin();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            billingCatalogService.entitlementDefinitions(),
            "Entitlement definitions fetched"));
  }

  @PostMapping("/plans/{planId}/entitlements")
  public ResponseEntity<ApiResponse<EntitlementResponse>> upsertEntitlement(
      @PathVariable Long planId, @Valid @RequestBody UpsertEntitlementRequest request) {
    requireSuperAdmin();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            billingCatalogService.upsertEntitlement(planId, request),
            "Entitlement saved"));
  }

  @DeleteMapping("/entitlements/{entitlementId}")
  public ResponseEntity<ApiResponse<Void>> deleteEntitlement(@PathVariable Long entitlementId) {
    requireSuperAdmin();
    billingCatalogService.deleteEntitlement(entitlementId);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Entitlement deleted"));
  }

  @GetMapping("/rate-cards")
  public ResponseEntity<ApiResponse<List<RateCardResponse>>> rateCards(@RequestParam Long planId) {
    requireSuperAdmin();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), billingCatalogService.rateCards(planId), "Rate cards fetched"));
  }

  @PostMapping("/rate-cards")
  public ResponseEntity<ApiResponse<RateCardResponse>> createRateCard(
      @Valid @RequestBody CreateRateCardRequest request) {
    requireSuperAdmin();
    RateCardResponse response = billingCatalogService.createRateCard(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response, "Rate card created"));
  }

  @PatchMapping("/rate-cards/{rateCardId}")
  public ResponseEntity<ApiResponse<RateCardResponse>> updateRateCard(
      @PathVariable Long rateCardId, @Valid @RequestBody UpdateRateCardRequest request) {
    requireSuperAdmin();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            billingCatalogService.updateRateCard(rateCardId, request),
            "Rate card updated"));
  }

  @GetMapping("/companies/{companyId}/rate-overrides")
  public ResponseEntity<ApiResponse<List<CompanyRateOverrideResponse>>> companyRateOverrides(
      @PathVariable Long companyId) {
    requireSuperAdmin();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            billingCatalogService.companyRateOverrides(companyId),
            "Company rate overrides fetched"));
  }

  @PostMapping("/companies/{companyId}/rate-overrides")
  public ResponseEntity<ApiResponse<CompanyRateOverrideResponse>> createCompanyRateOverride(
      @PathVariable Long companyId,
      @Valid @RequestBody CreateCompanyRateOverrideRequest request) {
    requireSuperAdmin();
    CompanyRateOverrideResponse response =
        billingCatalogService.createCompanyRateOverride(
            companyId, currentUserService.currentUserId(), request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(), response, "Company rate override created"));
  }

  @PatchMapping("/rate-overrides/{overrideId}")
  public ResponseEntity<ApiResponse<CompanyRateOverrideResponse>> updateCompanyRateOverride(
      @PathVariable Long overrideId,
      @Valid @RequestBody UpdateCompanyRateOverrideRequest request) {
    requireSuperAdmin();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            billingCatalogService.updateCompanyRateOverride(overrideId, request),
            "Company rate override updated"));
  }

  @PostMapping("/companies/{companyId}/subscription")
  public ResponseEntity<ApiResponse<CompanySubscriptionResponse>> assignCompanySubscription(
      @PathVariable Long companyId, @Valid @RequestBody AssignSubscriptionRequest request) {
    requireSuperAdmin();
    CompanySubscriptionResponse response = billingCatalogService.assignSubscription(companyId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(), response, "Company subscription assigned"));
  }

  private void requireSuperAdmin() {
    adminAuthorizationService.requireSuperAdmin(currentUserService.currentUserId());
  }
}
