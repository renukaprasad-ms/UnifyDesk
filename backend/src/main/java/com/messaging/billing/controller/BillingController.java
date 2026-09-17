package com.messaging.billing.controller;

import com.messaging.billing.dto.AssignSubscriptionRequest;
import com.messaging.billing.dto.CompanyPlanDetailsResponse;
import com.messaging.billing.dto.CompanySubscriptionResponse;
import com.messaging.billing.dto.PlanResponse;
import com.messaging.billing.service.BillingCatalogService;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        ApiResponse.success(HttpStatus.OK.value(), billingCatalogService.activePlans(), "Plans fetched"));
  }

  @PostMapping("/companies/{companyId}/subscription")
  public ResponseEntity<ApiResponse<CompanySubscriptionResponse>> assignSubscription(
      @PathVariable Long companyId, @Valid @RequestBody AssignSubscriptionRequest request) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(),
        companyId,
        CompanyPermissionKeys.BILLING_SUBSCRIPTION_MODIFY);
    CompanySubscriptionResponse response =
        billingCatalogService.assignSubscription(companyId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(), response, "Company subscription selected"));
  }

  @GetMapping("/companies/{companyId}/plan")
  public ResponseEntity<ApiResponse<CompanyPlanDetailsResponse>> companyPlan(
      @PathVariable Long companyId) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(),
        companyId,
        CompanyPermissionKeys.BILLING_SUBSCRIPTION_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            billingCatalogService.companyPlanDetails(companyId),
            "Company plan fetched"));
  }
}
