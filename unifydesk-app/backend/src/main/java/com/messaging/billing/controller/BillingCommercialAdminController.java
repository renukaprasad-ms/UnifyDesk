package com.messaging.billing.controller;

import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.billing.dto.AdminGrantSubscriptionRequest;
import com.messaging.billing.dto.CompanySubscriptionResponse;
import com.messaging.billing.dto.ImmediateCancellationRequest;
import com.messaging.billing.dto.PlanAssignmentRequest;
import com.messaging.billing.dto.PlanAssignmentResponse;
import com.messaging.billing.dto.RevokePlanAssignmentRequest;
import com.messaging.billing.service.BillingCatalogService;
import com.messaging.billing.service.PlanAssignmentService;
import com.messaging.common.response.ApiResponse;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/billing")
@RequiredArgsConstructor
public class BillingCommercialAdminController {

  private final BillingCatalogService billingCatalogService;
  private final PlanAssignmentService planAssignmentService;
  private final PlatformAdminAuthorizationService adminAuthorizationService;
  private final CurrentUserService currentUserService;

  @PostMapping("/plans/{planId}/company-assignments/{companyId}")
  public ResponseEntity<ApiResponse<PlanAssignmentResponse>> assignPrivatePlan(
      @PathVariable Long planId,
      @PathVariable Long companyId,
      @Valid @RequestBody PlanAssignmentRequest request) {
    requireSuperAdmin();
    PlanAssignmentResponse response =
        planAssignmentService.assign(planId, companyId, currentUserService.currentUserId(), request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response, "Private plan assigned"));
  }

  @GetMapping("/plans/{planId}/company-assignments")
  public ResponseEntity<ApiResponse<List<PlanAssignmentResponse>>> planAssignments(
      @PathVariable Long planId) {
    requireSuperAdmin();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            planAssignmentService.assignments(planId),
            "Plan assignments fetched"));
  }

  @PostMapping("/plan-company-assignments/{assignmentId}/revoke")
  public ResponseEntity<ApiResponse<PlanAssignmentResponse>> revokePrivatePlan(
      @PathVariable Long assignmentId,
      @Valid @RequestBody RevokePlanAssignmentRequest request) {
    requireSuperAdmin();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            planAssignmentService.revoke(
                assignmentId, currentUserService.currentUserId(), request.reason()),
            "Private plan assignment revoked"));
  }

  @PostMapping("/companies/{companyId}/subscription/grant")
  public ResponseEntity<ApiResponse<CompanySubscriptionResponse>> grantSubscription(
      @PathVariable Long companyId,
      @Valid @RequestBody AdminGrantSubscriptionRequest request) {
    requireSuperAdmin();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                billingCatalogService.grantSubscription(
                    companyId, currentUserService.currentUserId(), request),
                "Subscription grant applied"));
  }

  @PostMapping("/companies/{companyId}/subscription/cancel-immediately")
  public ResponseEntity<ApiResponse<CompanySubscriptionResponse>> cancelImmediately(
      @PathVariable Long companyId,
      @Valid @RequestBody ImmediateCancellationRequest request) {
    requireSuperAdmin();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            billingCatalogService.cancelImmediatelyByAdmin(
                companyId, currentUserService.currentUserId(), request.reason()),
            "Subscription cancelled immediately"));
  }

  private void requireSuperAdmin() {
    adminAuthorizationService.requireSuperAdmin(currentUserService.currentUserId());
  }
}
