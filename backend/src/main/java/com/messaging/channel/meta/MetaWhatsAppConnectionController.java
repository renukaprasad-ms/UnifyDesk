package com.messaging.channel.meta;

import com.messaging.channel.meta.dto.CompleteMetaWhatsAppSignupRequest;
import com.messaging.channel.meta.dto.MetaWhatsAppConnectionResponse;
import com.messaging.channel.meta.dto.MetaWhatsAppSignupConfigResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/channels/whatsapp/meta")
@RequiredArgsConstructor
public class MetaWhatsAppConnectionController {

  private final MetaWhatsAppConnectionService connectionService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/signup-config")
  public ResponseEntity<ApiResponse<MetaWhatsAppSignupConfigResponse>> signupConfig(
      @PathVariable Long companyId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.WHATSAPP_CONNECT);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.signupConfig(companyId, userId),
            "Meta WhatsApp signup configured"));
  }

  @PostMapping("/complete")
  public ResponseEntity<ApiResponse<MetaWhatsAppConnectionResponse>> complete(
      @PathVariable Long companyId,
      @Valid @RequestBody CompleteMetaWhatsAppSignupRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.WHATSAPP_CONNECT);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.complete(companyId, userId, request),
            "WhatsApp connected"));
  }
}
