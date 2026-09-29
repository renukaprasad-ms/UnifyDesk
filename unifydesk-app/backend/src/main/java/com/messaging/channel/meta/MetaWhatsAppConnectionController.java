package com.messaging.channel.meta;

import com.messaging.channel.meta.dto.CompleteMetaWhatsAppSignupRequest;
import com.messaging.channel.meta.dto.MetaWhatsAppConnectionHealthResponse;
import com.messaging.channel.meta.dto.MetaWhatsAppConnectionResponse;
import com.messaging.channel.meta.dto.MetaWhatsAppDisconnectRequest;
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

  @PostMapping("/signup-session")
  public ResponseEntity<ApiResponse<MetaWhatsAppSignupConfigResponse>> signupSession(
      @PathVariable Long companyId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.WHATSAPP_CONNECT);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.signupSession(companyId, userId),
            "Meta WhatsApp signup session created"));
  }

  @PostMapping("/reauthorize-session")
  public ResponseEntity<ApiResponse<MetaWhatsAppSignupConfigResponse>> reauthorizeSession(
      @PathVariable Long companyId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.WHATSAPP_CONNECT);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.reauthorizeSession(companyId, userId),
            "Meta WhatsApp reauthorization session created"));
  }

  @PostMapping("/complete")
  public ResponseEntity<ApiResponse<MetaWhatsAppConnectionResponse>> complete(
      @PathVariable Long companyId, @Valid @RequestBody CompleteMetaWhatsAppSignupRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.WHATSAPP_CONNECT);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.complete(companyId, userId, request),
            "Meta WhatsApp connection updated"));
  }

  @GetMapping("/connection")
  public ResponseEntity<ApiResponse<MetaWhatsAppConnectionHealthResponse>> connection(
      @PathVariable Long companyId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.WHATSAPP_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.getConnection(companyId),
            "Meta WhatsApp connection loaded"));
  }

  @PostMapping("/reconcile")
  public ResponseEntity<ApiResponse<MetaWhatsAppConnectionHealthResponse>> reconcile(
      @PathVariable Long companyId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.WHATSAPP_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.reconcile(companyId),
            "Meta WhatsApp connection reconciled"));
  }

  @PostMapping("/disconnect")
  public ResponseEntity<ApiResponse<MetaWhatsAppConnectionHealthResponse>> disconnect(
      @PathVariable Long companyId,
      @RequestBody(required = false) MetaWhatsAppDisconnectRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.WHATSAPP_DISCONNECT);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.disconnect(
                companyId, userId, request == null ? null : request.dataPolicy()),
            "Meta WhatsApp disconnected"));
  }
}
