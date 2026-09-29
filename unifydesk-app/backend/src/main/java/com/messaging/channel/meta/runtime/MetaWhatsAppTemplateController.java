package com.messaging.channel.meta.runtime;

import com.messaging.channel.meta.runtime.dto.MetaWhatsAppTemplateResponse;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppTemplateSyncRequest;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppTemplateSyncResponse;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/channels/whatsapp/meta")
@RequiredArgsConstructor
public class MetaWhatsAppTemplateController {
  private final MetaWhatsAppTemplateService templateService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/templates")
  public ResponseEntity<ApiResponse<List<MetaWhatsAppTemplateResponse>>> templates(
      @org.springframework.web.bind.annotation.PathVariable Long companyId,
      @RequestParam String connectionId) {
    long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.WHATSAPP_TEMPLATE_READ);
    long parsed = parseId(connectionId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            templateService.list(companyId, parsed),
            "WhatsApp templates fetched"));
  }

  @PostMapping("/templates/sync")
  public ResponseEntity<ApiResponse<MetaWhatsAppTemplateSyncResponse>> sync(
      @org.springframework.web.bind.annotation.PathVariable Long companyId,
      @Valid @RequestBody MetaWhatsAppTemplateSyncRequest request) {
    long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.WHATSAPP_TEMPLATE_MODIFY);
    long parsed = parseId(request.connectionId());
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            templateService.sync(companyId, parsed),
            "WhatsApp templates synchronized"));
  }

  private long parseId(String value) {
    try {
      return Long.parseLong(value);
    } catch (Exception exception) {
      throw new BadRequestException("Invalid connectionId");
    }
  }
}
