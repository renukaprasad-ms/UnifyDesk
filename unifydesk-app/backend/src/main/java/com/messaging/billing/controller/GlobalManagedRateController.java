package com.messaging.billing.controller;

import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.billing.dto.GlobalManagedRateResponse;
import com.messaging.billing.dto.ReviseGlobalManagedRateRequest;
import com.messaging.billing.service.GlobalManagedRateService;
import com.messaging.common.response.ApiResponse;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Super-admin-only published global pricing, independent of subscription plans. */
@RestController
@RequestMapping("/api/v1/admin/billing/global-managed-rates")
@RequiredArgsConstructor
public class GlobalManagedRateController {
  private final GlobalManagedRateService rates;
  private final PlatformAdminAuthorizationService adminAuthorization;
  private final CurrentUserService currentUserService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<GlobalManagedRateResponse>>> history() {
    adminAuthorization.requireSuperAdmin(currentUserService.currentUserId());
    return ResponseEntity.ok(ApiResponse.success(
        HttpStatus.OK.value(), rates.history(), "Global managed rate history fetched"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<GlobalManagedRateResponse>> revise(
      @Valid @RequestBody ReviseGlobalManagedRateRequest request) {
    Long actorId = currentUserService.currentUserId();
    adminAuthorization.requireSuperAdmin(actorId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(),
            rates.revise(request, actorId), "Global managed rate revision created"));
  }
}
