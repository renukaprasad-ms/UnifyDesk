package com.messaging.managedemail.control.controller;

import com.messaging.admin.enums.PlatformPermission;
import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.common.response.ApiResponse;
import com.messaging.managedemail.control.dto.ManagedEmailAdminDtos.DomainDecisionRequest;
import com.messaging.managedemail.control.dto.ManagedEmailAdminDtos.HostedDomainReviewItem;
import com.messaging.managedemail.control.dto.ManagedEmailDtos.DomainResponse;
import com.messaging.managedemail.control.service.ManagedEmailDomainService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/platform/managed-email", "/api/platform/managed-email"})
@RequiredArgsConstructor
public class PlatformManagedEmailReviewController {

  private final ManagedEmailDomainService domainService;
  private final CurrentUserService currentUserService;
  private final PlatformAdminAuthorizationService authorizationService;

  @GetMapping("/review-queue")
  public ResponseEntity<ApiResponse<List<HostedDomainReviewItem>>> reviewQueue() {
    Long adminUserId = currentUserService.currentUserId();
    authorizationService.requirePermission(adminUserId, PlatformPermission.COMPANY_VIEW);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            domainService.listPendingHostedRequests(),
            "Managed Email review queue fetched"));
  }

  @PostMapping("/requests/{domainId}/decision")
  public ResponseEntity<ApiResponse<DomainResponse>> decide(
      @PathVariable Long domainId, @Valid @RequestBody DomainDecisionRequest request) {
    Long adminUserId = currentUserService.currentUserId();
    authorizationService.requirePermission(adminUserId, PlatformPermission.COMPANY_UPDATE);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            domainService.decideHostedRequest(domainId, adminUserId, request),
            "Managed Email domain request decision applied"));
  }
}
