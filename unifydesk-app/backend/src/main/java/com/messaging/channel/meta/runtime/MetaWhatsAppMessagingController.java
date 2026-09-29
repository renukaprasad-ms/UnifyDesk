package com.messaging.channel.meta.runtime;

import com.messaging.channel.meta.runtime.dto.MetaWhatsAppComposerResponse;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppSendRequest;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppSendResponse;
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
@RequestMapping(
    "/api/v1/companies/{companyId}/inbox/conversations/{conversationId}/whatsapp")
@RequiredArgsConstructor
public class MetaWhatsAppMessagingController {
  private final MetaWhatsAppMessageService messageService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @PostMapping("/messages")
  public ResponseEntity<ApiResponse<MetaWhatsAppSendResponse>> send(
      @PathVariable Long companyId,
      @PathVariable Long conversationId,
      @Valid @RequestBody MetaWhatsAppSendRequest request) {
    long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.WHATSAPP_SEND);
    MetaWhatsAppSendResponse response = messageService.send(companyId, conversationId, request);
    HttpStatus status =
        "UNKNOWN".equals(response.status()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
    return ResponseEntity.status(status)
        .body(
            ApiResponse.success(
                status.value(), response, "WhatsApp message request processed"));
  }

  @GetMapping("/composer")
  public ResponseEntity<ApiResponse<MetaWhatsAppComposerResponse>> composer(
      @PathVariable Long companyId, @PathVariable Long conversationId) {
    long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.WHATSAPP_SEND);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            messageService.composer(companyId, conversationId),
            "WhatsApp composer state fetched"));
  }
}
