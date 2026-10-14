package com.messaging.crm;

import com.messaging.channel.meta.runtime.dto.MetaWhatsAppTemplateResponse;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/crm/whatsapp")
@RequiredArgsConstructor
public class CrmWhatsAppController {

  private final CrmWhatsAppService service;
  private final CompanyAuthorizationService authorization;
  private final CurrentUserService currentUser;

  @GetMapping("/context")
  public ResponseEntity<ApiResponse<CrmWhatsAppService.WhatsAppContext>> context(
      @PathVariable long companyId,
      @RequestParam(required = false) Long contactId) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, CompanyPermissionKeys.WHATSAPP_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.context(companyId, contactId), "CRM WhatsApp context fetched"));
  }

  @GetMapping("/templates")
  public ResponseEntity<ApiResponse<List<MetaWhatsAppTemplateResponse>>> templates(
      @PathVariable long companyId,
      @RequestParam(required = false) Long connectionId) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, CompanyPermissionKeys.WHATSAPP_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.templates(companyId, connectionId), "WhatsApp templates fetched"));
  }

  @PostMapping("/send")
  public ResponseEntity<ApiResponse<CrmWhatsAppService.SendResult>> send(
      @PathVariable long companyId,
      @Valid @RequestBody CrmWhatsAppService.SendInput input) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, CompanyPermissionKeys.WHATSAPP_SEND);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.send(companyId, input, actor), "WhatsApp message sent successfully"));
  }
}
