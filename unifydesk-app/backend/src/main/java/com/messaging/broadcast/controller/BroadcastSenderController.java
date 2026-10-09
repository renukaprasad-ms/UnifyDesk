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
  private final com.messaging.broadcast.service.BroadcastSenderTestService senderTestService;
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

  @PostMapping("/sendgrid")
  public ResponseEntity<ApiResponse<BroadcastSenderResponse>> connectSendGrid(
      @PathVariable Long companyId, @Valid @RequestBody ConnectSendGridRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                senderService.connectSendGrid(companyId, userId, request),
                "SendGrid sender connected"));
  }

  @PostMapping("/unifydesk-email")
  public ResponseEntity<ApiResponse<BroadcastSenderResponse>> connectUnifyDeskEmail(
      @PathVariable Long companyId, @Valid @RequestBody ConnectUnifyDeskEmailRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(
            201, senderService.connectUnifyDeskEmail(companyId, userId, request),
            "UnifyDesk Email sender connected"));
  }

  @PostMapping("/azure-email")
  public ResponseEntity<ApiResponse<BroadcastSenderResponse>> connectAzureEmail(
      @PathVariable Long companyId, @Valid @RequestBody ConnectAzureEmailRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                senderService.connectAzureEmail(companyId, userId, request),
                "Azure Email sender connected"));
  }

  @PostMapping({"/msg91", "/msg91-sms"})
  public ResponseEntity<ApiResponse<BroadcastSenderResponse>> connectMsg91Sms(
      @PathVariable Long companyId, @Valid @RequestBody ConnectMsg91Request request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                senderService.connectMsg91Sms(companyId, userId, request),
                "MSG91 SMS sender connected"));
  }

  @PostMapping("/msg91-email")
  public ResponseEntity<ApiResponse<BroadcastSenderResponse>> connectMsg91Email(
      @PathVariable Long companyId, @Valid @RequestBody ConnectMsg91EmailRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                senderService.connectMsg91Email(companyId, userId, request),
                "MSG91 Email sender connected"));
  }

  @PostMapping("/{senderId}/test-email")
  public ResponseEntity<ApiResponse<BroadcastTestEmailResponse>> sendTestEmail(
      @PathVariable Long companyId,
      @PathVariable Long senderId,
      @Valid @RequestBody SendBroadcastTestEmailRequest request) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            senderTestService.sendTestEmail(companyId, senderId, request),
            "Test email accepted by provider"));
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
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    senderService.disable(companyId, senderId, userId);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Broadcast sender disconnected"));
  }

  private Long require(Long companyId, String permission) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }
}
