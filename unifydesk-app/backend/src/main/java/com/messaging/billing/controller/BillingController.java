package com.messaging.billing.controller;

import com.messaging.billing.dto.AssignSubscriptionRequest;
import com.messaging.billing.dto.CompanyPlanDetailsResponse;
import com.messaging.billing.dto.CompanySubscriptionResponse;
import com.messaging.billing.dto.PlanResponse;
import com.messaging.billing.dto.RequestUpgradeRequest;
import com.messaging.billing.dto.ScheduleDowngradeRequest;
import com.messaging.billing.dto.SubscriptionChangeResponse;
import com.messaging.billing.service.BillingCatalogService;
import com.messaging.billing.service.SubscriptionChangeService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.time.Instant;
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
  private final SubscriptionChangeService subscriptionChangeService;
  private final CompanyAuthorizationService companyAuthorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/plans")
  public ResponseEntity<ApiResponse<List<PlanResponse>>> plans() {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), billingCatalogService.activePlans(), "Plans fetched"));
  }

  @GetMapping("/companies/{companyId}/plans")
  public ResponseEntity<ApiResponse<List<PlanResponse>>> eligiblePlans(
      @PathVariable Long companyId) {
    requireSubscriptionRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            billingCatalogService.eligiblePlans(companyId),
            "Eligible plans fetched"));
  }

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

  @PostMapping("/companies/{companyId}/subscription/upgrade")
  public ResponseEntity<ApiResponse<SubscriptionChangeResponse>> requestUpgrade(
      @PathVariable Long companyId, @Valid @RequestBody RequestUpgradeRequest request) {
    requireSubscriptionModify(companyId);
    SubscriptionChangeResponse change =
        subscriptionChangeService.requestUpgrade(
            companyId,
            parseId(request.targetPlanId()),
            parseId(request.targetPlanPriceId()),
            request.idempotencyKey(),
            currentUserService.currentUserId(),
            Instant.now());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), change, "Upgrade quote created"));
  }

  @PostMapping("/companies/{companyId}/subscription/downgrade")
  public ResponseEntity<ApiResponse<SubscriptionChangeResponse>> scheduleDowngrade(
      @PathVariable Long companyId, @Valid @RequestBody ScheduleDowngradeRequest request) {
    requireSubscriptionModify(companyId);
    SubscriptionChangeResponse change =
        subscriptionChangeService.scheduleDowngrade(
            companyId,
            parseId(request.targetPlanId()),
            parseId(request.targetPlanPriceId()),
            request.idempotencyKey(),
            currentUserService.currentUserId(),
            Instant.now());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), change, "Downgrade scheduled"));
  }

  @PostMapping("/companies/{companyId}/subscription/cancel")
  public ResponseEntity<ApiResponse<CompanySubscriptionResponse>> cancelSubscription(
      @PathVariable Long companyId, @RequestParam(defaultValue = "true") boolean atPeriodEnd) {
    requireSubscriptionCancel(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            billingCatalogService.cancelSubscription(companyId, atPeriodEnd),
            "Subscription cancellation scheduled"));
  }

  @GetMapping("/companies/{companyId}/plan")
  public ResponseEntity<ApiResponse<CompanyPlanDetailsResponse>> companyPlan(
      @PathVariable Long companyId) {
    requireSubscriptionRead(companyId);
    CompanyPlanDetailsResponse details;
    try {
      details = billingCatalogService.companyPlanDetails(companyId);
    } catch (NotFoundException exception) {
      details = new CompanyPlanDetailsResponse(null, null);
    }
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), details, "Company plan fetched"));
  }

  private void requireSubscriptionRead(Long companyId) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(),
        companyId,
        CompanyPermissionKeys.BILLING_SUBSCRIPTION_READ);
  }

  private void requireSubscriptionModify(Long companyId) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(),
        companyId,
        CompanyPermissionKeys.BILLING_SUBSCRIPTION_MODIFY);
  }

  private void requireSubscriptionCancel(Long companyId) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(),
        companyId,
        CompanyPermissionKeys.BILLING_SUBSCRIPTION_CANCEL);
  }

  private Long parseId(String value) {
    try {
      return Long.valueOf(value);
    } catch (RuntimeException exception) {
      throw new BadRequestException("Invalid billing identifier");
    }
  }
}
