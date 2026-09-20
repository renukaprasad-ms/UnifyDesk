package com.messaging.channel.microsoft;

import com.messaging.channel.dto.ChannelConnectionResponse;
import com.messaging.channel.microsoft.dto.MicrosoftAuthorizationResponse;
import com.messaging.channel.microsoft.dto.MicrosoftSendRequest;
import com.messaging.channel.microsoft.dto.MicrosoftSendResponse;
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

  @PostMapping("/{connectionId}/sync")
  public ResponseEntity<ApiResponse<Void>> sync(
      @PathVariable Long companyId, @PathVariable Long connectionId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_READ);
    connectionService.requestSync(companyId, connectionId);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(HttpStatus.ACCEPTED.value(), "Microsoft sync requested"));
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
      @PathVariable Long companyId, @PathVariable Long connectionId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.EMAIL_DISCONNECT);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.disconnect(companyId, connectionId),
            "Microsoft mailbox disconnected"));
  }
}
