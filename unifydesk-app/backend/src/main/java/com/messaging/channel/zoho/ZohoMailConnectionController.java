package com.messaging.channel.zoho;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.channel.dto.ChannelConnectionResponse;
import com.messaging.channel.dto.DisconnectChannelRequest;
import com.messaging.channel.email.EmailChannelOperationsService;
import com.messaging.channel.email.EmailSyncRequest;
import com.messaging.channel.zoho.dto.ZohoMailAuthorizationResponse;
import com.messaging.channel.zoho.dto.ZohoMailSendRequest;
import com.messaging.channel.zoho.dto.ZohoMailSendResponse;
import com.messaging.channel.zoho.dto.ZohoMailSyncQueuedResponse;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/channels/email/zoho")
@RequiredArgsConstructor
public class ZohoMailConnectionController {

  private final ZohoMailConnectionService connectionService;
  private final EmailChannelOperationsService emailOperationsService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/authorize")
  public ResponseEntity<ApiResponse<ZohoMailAuthorizationResponse>> authorize(
      @PathVariable Long companyId, @RequestParam(required = false) String returnTo) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_CONNECT);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.authorization(companyId, userId, returnTo),
            "Zoho Mail authorization created"));
  }

  @GetMapping("/{connectionId}/sync/active")
  public ResponseEntity<ApiResponse<AsyncJobResponse>> activeSync(
      @PathVariable Long companyId, @PathVariable Long connectionId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            emailOperationsService.activeSync(companyId, connectionId),
            "Active Zoho Mail sync fetched"));
  }

  @PostMapping("/{connectionId}/sync")
  public ResponseEntity<ApiResponse<ZohoMailSyncQueuedResponse>> sync(
      @PathVariable Long companyId,
      @PathVariable Long connectionId,
      @Valid @RequestBody EmailSyncRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_READ);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                HttpStatus.ACCEPTED.value(),
                ZohoMailSyncQueuedResponse.from(
                    emailOperationsService.queueSync(companyId, userId, connectionId, request)),
                "Zoho Mail sync queued"));
  }

  @PostMapping("/{connectionId}/send")
  public ResponseEntity<ApiResponse<ZohoMailSendResponse>> send(
      @PathVariable Long companyId,
      @PathVariable Long connectionId,
      @Valid @RequestBody ZohoMailSendRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_SEND);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                HttpStatus.ACCEPTED.value(),
                ZohoMailSendResponse.from(
                    emailOperationsService.send(companyId, connectionId, request.toCommand())),
                "Email accepted by Zoho Mail"));
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
            emailOperationsService.disconnect(
                companyId,
                connectionId,
                userId,
                request == null ? null : request.resolvedDataPolicy()),
            "Zoho Mail mailbox disconnected"));
  }
}
