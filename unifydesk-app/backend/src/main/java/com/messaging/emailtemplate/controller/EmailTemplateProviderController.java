package com.messaging.emailtemplate.controller;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.emailtemplate.dto.EmailTemplateProviderBindingResponse;
import com.messaging.emailtemplate.service.EmailTemplateProviderBindingService;
import com.messaging.security.service.CurrentUserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}")
@RequiredArgsConstructor
public class EmailTemplateProviderController {

  private final EmailTemplateProviderBindingService service;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/email-template-provider-bindings")
  public ResponseEntity<ApiResponse<List<EmailTemplateProviderBindingResponse>>> list(
      @PathVariable Long companyId,
      @RequestParam(required = false) Long templateId,
      @RequestParam(required = false) Long connectionId) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_READ);
    if (templateId != null) {
      return ResponseEntity.ok(
          ApiResponse.success(
              200, service.listForTemplate(companyId, templateId), "Provider bindings fetched"));
    }
    if (connectionId != null) {
      return ResponseEntity.ok(
          ApiResponse.success(
              200,
              service.listForConnection(companyId, connectionId),
              "Provider bindings fetched"));
    }
    throw new BadRequestException("templateId or connectionId is required");
  }

  @PostMapping("/email-templates/{templateId}/providers/msg91/{connectionId}/sync")
  public ResponseEntity<ApiResponse<EmailTemplateProviderBindingResponse>> syncMsg91(
      @PathVariable Long companyId,
      @PathVariable Long templateId,
      @PathVariable Long connectionId) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.syncMsg91(companyId, templateId, connectionId),
            "MSG91 template sync completed"));
  }

  @PostMapping("/email-template-provider-bindings/{bindingId}/refresh")
  public ResponseEntity<ApiResponse<EmailTemplateProviderBindingResponse>> refresh(
      @PathVariable Long companyId, @PathVariable Long bindingId) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.refresh(companyId, bindingId), "Provider template status refreshed"));
  }

  private Long require(Long companyId, String permission) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }
}
