package com.messaging.channel.gmail;

import com.messaging.channel.dto.ChannelConnectionResponse;
import com.messaging.channel.gmail.dto.GmailAuthorizationResponse;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/channels/email/gmail")
@RequiredArgsConstructor
public class GmailConnectionController {

  private final GmailConnectionService gmailConnectionService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/authorize")
  public ResponseEntity<ApiResponse<GmailAuthorizationResponse>> authorize(
      @PathVariable Long companyId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_CONNECT);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            gmailConnectionService.authorization(companyId, userId),
            "Gmail authorization created"));
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
            gmailConnectionService.disconnect(companyId, connectionId),
            "Gmail disconnected"));
  }
}
