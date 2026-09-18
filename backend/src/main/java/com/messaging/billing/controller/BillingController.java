package com.messaging.billing.controller;

import com.messaging.billing.dto.AssignSubscriptionRequest;
import com.messaging.billing.dto.CompanyPlanDetailsResponse;
import com.messaging.billing.dto.CompanySubscriptionResponse;
import com.messaging.billing.dto.PlanResponse;
import com.messaging.billing.service.BillingCatalogService;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BillingController {

  private final BillingCatalogService billingCatalogService;
  private final CompanyAuthorizationService companyAuthorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/plans")
  public ResponseEntity<ApiResponse<List<PlanResponse>>> plans() {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), billingCatalogService.activePlans(), "Plans fetched"));
  }

  /*
   * Until checkout/payment is implemented, customers may only activate a zero-cost plan.
   * Paid subscriptions can be assigned by Super Admin or later by the payment webhook flow.
   */
  @PostMapping("/companies/{companyId}/subscription")
  public ResponseEntity<ApiResponse<CompanySubscriptionResponse>> selectSubscription(
      @PathVariable Long companyId, @Valid @RequestBody AssignSubscriptionRequest request) {
    requireSubscriptionModify(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                billingCatalogService.selectFreeSubscription(companyId, request),
                "Company subscription selected"));
  }

  @PostMapping("/companies/{companyId}/subscription/cancel")
  public ResponseEntity<ApiResponse<CompanySubscriptionResponse>> cancelSubscription(
      @PathVariable Long companyId, @RequestParam(defaultValue = "true") boolean atPeriodEnd) {
    requireSubscriptionModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            billingCatalogService.cancelSubscription(companyId, atPeriodEnd),
            "Subscription cancellation updated"));
  }

  @GetMapping("/companies/{companyId}/plan")
  public ResponseEntity<ApiResponse<CompanyPlanDetailsResponse>> companyPlan(
      @PathVariable Long companyId) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(),
        companyId,
        CompanyPermissionKeys.BILLING_SUBSCRIPTION_READ);

    CompanyPlanDetailsResponse details;
    try {
      details = billingCatalogService.companyPlanDetails(companyId);
    } catch (NotFoundException exception) {
      details = new CompanyPlanDetailsResponse(null, null);
    }

    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), details, "Company plan fetched"));
  }

  private void requireSubscriptionModify(Long companyId) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(),
        companyId,
        CompanyPermissionKeys.BILLING_SUBSCRIPTION_MODIFY);
  }
}
