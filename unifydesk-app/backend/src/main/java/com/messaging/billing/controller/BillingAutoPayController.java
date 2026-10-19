package com.messaging.billing.controller;

import com.messaging.billing.dto.AutoPayDetailsResponse;
import com.messaging.billing.dto.AutoPaySetupRequest;
import com.messaging.billing.service.BillingAutoPayService;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/billing/autopay")
@RequiredArgsConstructor
public class BillingAutoPayController {

  private final BillingAutoPayService autoPayService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping
  public ResponseEntity<ApiResponse<AutoPayDetailsResponse>> get(@PathVariable Long companyId) {
    requireBillingRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), autoPayService.get(companyId), "AutoPay details fetched"));
  }

  @PostMapping("/setup")
  public ResponseEntity<ApiResponse<AutoPayDetailsResponse>> setup(
      @PathVariable Long companyId, @Valid @RequestBody(required = false) AutoPaySetupRequest request) {
    requireBillingModify(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                autoPayService.setup(companyId, request),
                "AutoPay mandate setup prepared"));
  }

  @PostMapping("/verify")
  public ResponseEntity<ApiResponse<AutoPayDetailsResponse>> verify(
      @PathVariable Long companyId,
      @RequestBody(required = false) com.messaging.billing.dto.AutoPayVerifyRequest request) {
    requireBillingModify(companyId);
    AutoPayDetailsResponse details = autoPayService.verify(companyId, request);
    String message =
        "ACTIVE".equalsIgnoreCase(details.status())
            ? "AutoPay mandate verified"
            : "AutoPay mandate has not yet been authorized";
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), details, message));
  }

  @DeleteMapping
  public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Long companyId) {
    requireBillingModify(companyId);
    autoPayService.cancel(companyId);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), null, "AutoPay mandate cancelled"));
  }

  private void requireBillingRead(Long companyId) {
    authorizationService.requirePermission(
        currentUserService.currentUserId(), companyId, CompanyPermissionKeys.BILLING_READ);
  }

  private void requireBillingModify(Long companyId) {
    authorizationService.requirePermission(
        currentUserService.currentUserId(),
        companyId,
        CompanyPermissionKeys.BILLING_SUBSCRIPTION_MODIFY);
  }
}
