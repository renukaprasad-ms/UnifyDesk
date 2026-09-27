package com.messaging.managedsms.control.controller;

import com.messaging.common.response.ApiResponse;
import com.messaging.managedsms.control.admin.ManagedSmsAdminService;
import com.messaging.managedsms.control.dto.ManagedSmsAdminDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/platform/managed-sms", "/api/platform/managed-sms"})
@RequiredArgsConstructor
public class ManagedSmsAdminController {

  private final ManagedSmsAdminService service;

  @GetMapping("/review-queue")
  public ResponseEntity<ApiResponse<ReviewQueueResponse>> queue() {
    return ResponseEntity.ok(
        ApiResponse.success(200, service.queue(), "Managed SMS review queue fetched"));
  }

  @PostMapping("/dlt-profiles/{profileId}/decision")
  public ResponseEntity<ApiResponse<DecisionResponse>> decideProfile(
      @PathVariable Long profileId, @Valid @RequestBody DecisionRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.decideProfile(profileId, request), "Managed SMS DLT decision applied"));
  }

  @PostMapping("/headers/{headerId}/decision")
  public ResponseEntity<ApiResponse<DecisionResponse>> decideHeader(
      @PathVariable Long headerId, @Valid @RequestBody DecisionRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.decideHeader(headerId, request), "Managed SMS Header decision applied"));
  }

  @PostMapping("/templates/{versionId}/decision")
  public ResponseEntity<ApiResponse<DecisionResponse>> decideTemplate(
      @PathVariable Long versionId, @Valid @RequestBody DecisionRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.decideTemplate(versionId, request),
            "Managed SMS template decision applied"));
  }

  @PostMapping("/tenants/{tenantId}/suspend")
  public ResponseEntity<ApiResponse<DecisionResponse>> suspend(
      @PathVariable Long tenantId, @Valid @RequestBody TenantActionRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.suspendTenant(tenantId, request), "Managed SMS tenant suspended"));
  }

  @PostMapping("/tenants/{tenantId}/resume")
  public ResponseEntity<ApiResponse<DecisionResponse>> resume(
      @PathVariable Long tenantId, @Valid @RequestBody TenantActionRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.resumeTenant(tenantId, request), "Managed SMS tenant resume queued"));
  }
}
