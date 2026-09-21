package com.messaging.channel.microsoft;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.channel.dto.ChannelConnectionResponse;
import com.messaging.channel.dto.DisconnectChannelRequest;
import com.messaging.channel.email.EmailSyncRequest;
import com.messaging.channel.microsoft.dto.MicrosoftAuthorizationResponse;
import com.messaging.channel.microsoft.dto.MicrosoftSendRequest;
import com.messaging.channel.microsoft.dto.MicrosoftSendResponse;
import com.messaging.channel.microsoft.dto.MicrosoftSyncQueuedResponse;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/channels/email/microsoft")
@RequiredArgsConstructor
public class MicrosoftConnectionController {

  private final MicrosoftConnectionService connectionService;
  private final MicrosoftSendService sendService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/authorize")
  public ResponseEntity<ApiResponse<MicrosoftAuthorizationResponse>> authorize(
      @PathVariable Long companyId, @RequestParam(required = false) String returnTo) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_CONNECT);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.authorization(companyId, userId, returnTo),
            "Microsoft authorization created"));
  }

  @GetMapping("/{connectionId}/sync/active")
  public ResponseEntity<ApiResponse<AsyncJobResponse>> activeSync(
      @PathVariable Long companyId, @PathVariable Long connectionId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.activeSync(companyId, connectionId),
            "Active Microsoft sync fetched"));
  }

  @PostMapping("/{connectionId}/sync")
  public ResponseEntity<ApiResponse<MicrosoftSyncQueuedResponse>> sync(
      @PathVariable Long companyId,
      @PathVariable Long connectionId,
      @Valid @RequestBody EmailSyncRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_READ);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                HttpStatus.ACCEPTED.value(),
                connectionService.requestSync(companyId, userId, connectionId, request),
                "Microsoft sync queued"));
  }

  @PostMapping("/{connectionId}/send")
  public ResponseEntity<ApiResponse<MicrosoftSendResponse>> send(
      @PathVariable Long companyId,
      @PathVariable Long connectionId,
      @Valid @RequestBody MicrosoftSendRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_SEND);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                HttpStatus.ACCEPTED.value(),
                sendService.send(companyId, connectionId, request),
                "Email accepted by Microsoft"));
  }

  @PostMapping("/{connectionId}/disconnect")
  public ResponseEntity<ApiResponse<ChannelConnectionResponse>> disconnect(
      @PathVariable Long companyId,
      @PathVariable Long connectionId,
      @RequestBody(required = false) DisconnectChannelRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.EMAIL_DISCONNECT);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.disconnect(
                companyId,
                connectionId,
                userId,
                request == null ? null : request.resolvedDataPolicy()),
            "Microsoft mailbox disconnected"));
  }
}
