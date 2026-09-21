package com.messaging.broadcast.controller;

import com.messaging.broadcast.dto.*;
import com.messaging.broadcast.service.BroadcastSenderService;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/broadcast/senders")
@RequiredArgsConstructor
public class BroadcastSenderController {

  private final BroadcastSenderService senderService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<BroadcastSenderResponse>>> list(
      @PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, senderService.list(companyId), "Broadcast senders fetched"));
  }

  @PostMapping("/amazon-ses")
  public ResponseEntity<ApiResponse<BroadcastSenderResponse>> connectAmazonSes(
      @PathVariable Long companyId, @Valid @RequestBody ConnectAmazonSesRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                senderService.connectAmazonSes(companyId, userId, request),
                "Amazon SES sender connected"));
  }

  @PostMapping("/{senderId}/refresh")
  public ResponseEntity<ApiResponse<BroadcastSenderResponse>> refresh(
      @PathVariable Long companyId, @PathVariable Long senderId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, senderService.refresh(companyId, senderId), "Broadcast sender refreshed"));
  }

  @DeleteMapping("/{senderId}")
  public ResponseEntity<ApiResponse<Void>> disable(
      @PathVariable Long companyId, @PathVariable Long senderId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    senderService.disable(companyId, senderId);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Broadcast sender disconnected"));
  }

  private Long require(Long companyId, String permission) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }
}
