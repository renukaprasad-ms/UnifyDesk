package com.messaging.channel.gmail;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.channel.dto.ChannelConnectionResponse;
import com.messaging.channel.dto.DisconnectChannelRequest;
import com.messaging.channel.email.EmailSyncRequest;
import com.messaging.channel.gmail.dto.GmailAuthorizationResponse;
import com.messaging.channel.gmail.dto.GmailSendRequest;
import com.messaging.channel.gmail.dto.GmailSendResponse;
import com.messaging.channel.gmail.dto.GmailSyncQueuedResponse;
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
@RequestMapping("/api/v1/companies/{companyId}/channels/email/gmail")
@RequiredArgsConstructor
public class GmailConnectionController {

  private final GmailConnectionService gmailConnectionService;
  private final GmailSendService gmailSendService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/authorize")
  public ResponseEntity<ApiResponse<GmailAuthorizationResponse>> authorize(
      @PathVariable Long companyId, @RequestParam(required = false) String returnTo) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_CONNECT);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            gmailConnectionService.authorization(companyId, userId, returnTo),
            "Gmail authorization created"));
  }

  @GetMapping("/{connectionId}/sync/active")
  public ResponseEntity<ApiResponse<AsyncJobResponse>> activeSync(
      @PathVariable Long companyId, @PathVariable Long connectionId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_READ);

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            gmailConnectionService.activeSync(companyId, connectionId),
            "Active Gmail sync fetched"));
  }

  @PostMapping("/{connectionId}/sync")
  public ResponseEntity<ApiResponse<GmailSyncQueuedResponse>> sync(
      @PathVariable Long companyId,
      @PathVariable Long connectionId,
      @Valid @RequestBody EmailSyncRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_READ);

    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                HttpStatus.ACCEPTED.value(),
                gmailConnectionService.queueSync(companyId, userId, connectionId, request),
                "Gmail sync queued"));
  }

  @PostMapping("/{connectionId}/send")
  public ResponseEntity<ApiResponse<GmailSendResponse>> send(
      @PathVariable Long companyId,
      @PathVariable Long connectionId,
      @Valid @RequestBody GmailSendRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_SEND);

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            gmailSendService.send(companyId, connectionId, request),
            "Email sent"));
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
            gmailConnectionService.disconnect(
                companyId,
                connectionId,
                userId,
                request == null ? null : request.resolvedDataPolicy()),
            "Gmail disconnected"));
  }
}
