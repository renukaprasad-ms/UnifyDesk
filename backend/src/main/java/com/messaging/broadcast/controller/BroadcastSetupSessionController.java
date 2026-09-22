package com.messaging.broadcast.controller;

import com.messaging.broadcast.dto.BroadcastSetupSessionResponse;
import com.messaging.broadcast.dto.UpsertBroadcastSetupSessionRequest;
import com.messaging.broadcast.service.BroadcastSetupSessionService;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/broadcast/setup-sessions")
@RequiredArgsConstructor
public class BroadcastSetupSessionController {

  private final BroadcastSetupSessionService setupSessionService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/active")
  public ResponseEntity<ApiResponse<BroadcastSetupSessionResponse>> active(
      @PathVariable Long companyId, @RequestParam ChannelType channelType) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            setupSessionService.active(companyId, channelType).orElse(null),
            "Broadcast setup session fetched"));
  }

  @PutMapping("/active")
  public ResponseEntity<ApiResponse<BroadcastSetupSessionResponse>> upsert(
      @PathVariable Long companyId,
      @Valid @RequestBody UpsertBroadcastSetupSessionRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            setupSessionService.upsert(companyId, userId, request),
            "Broadcast setup progress saved"));
  }

  private Long require(Long companyId, String permission) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }
}
