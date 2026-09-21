package com.messaging.channel.imapsmtp;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.channel.email.EmailChannelOperationsService;
import com.messaging.channel.email.EmailSyncRequest;
import com.messaging.channel.imapsmtp.dto.ImapSmtpConnectRequest;
import com.messaging.channel.imapsmtp.dto.ImapSmtpConnectionResponse;
import com.messaging.channel.imapsmtp.dto.ImapSmtpSendRequest;
import com.messaging.channel.imapsmtp.dto.ImapSmtpSendResponse;
import com.messaging.channel.imapsmtp.dto.ImapSmtpSyncQueuedResponse;
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
@RequestMapping("/api/v1/companies/{companyId}/channels/email/imap-smtp")
@RequiredArgsConstructor
public class ImapSmtpConnectionController {

  private final ImapSmtpConnectionService connectionService;
  private final EmailChannelOperationsService emailOperationsService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @PostMapping("/connect")
  public ResponseEntity<ApiResponse<ImapSmtpConnectionResponse>> connect(
      @PathVariable Long companyId, @Valid @RequestBody ImapSmtpConnectRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_CONNECT);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                connectionService.connect(companyId, userId, request),
                "IMAP/SMTP mailbox connected"));
  }

  @GetMapping("/{connectionId}")
  public ResponseEntity<ApiResponse<ImapSmtpConnectionResponse>> get(
      @PathVariable Long companyId, @PathVariable Long connectionId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.get(companyId, connectionId),
            "IMAP/SMTP mailbox fetched"));
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
            "Active IMAP sync fetched"));
  }

  @PostMapping("/{connectionId}/sync")
  public ResponseEntity<ApiResponse<ImapSmtpSyncQueuedResponse>> sync(
      @PathVariable Long companyId,
      @PathVariable Long connectionId,
      @Valid @RequestBody EmailSyncRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_READ);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                HttpStatus.ACCEPTED.value(),
                ImapSmtpSyncQueuedResponse.from(
                    emailOperationsService.queueSync(companyId, userId, connectionId, request)),
                "IMAP sync queued"));
  }

  @PostMapping("/{connectionId}/send")
  public ResponseEntity<ApiResponse<ImapSmtpSendResponse>> send(
      @PathVariable Long companyId,
      @PathVariable Long connectionId,
      @Valid @RequestBody ImapSmtpSendRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_SEND);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                HttpStatus.ACCEPTED.value(),
                ImapSmtpSendResponse.from(
                emailOperationsService.send(companyId, connectionId, request.toCommand())),
                "Email sent"));
  }
}
