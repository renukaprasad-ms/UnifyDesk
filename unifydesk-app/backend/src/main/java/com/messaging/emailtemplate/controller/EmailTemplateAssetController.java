package com.messaging.emailtemplate.controller;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.emailtemplate.dto.*;
import com.messaging.emailtemplate.service.EmailTemplateAssetService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class EmailTemplateAssetController {

  private final EmailTemplateAssetService service;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @PostMapping("/api/v1/companies/{companyId}/email-template-assets")
  public ResponseEntity<ApiResponse<EmailTemplateAssetResponse>> register(
      @PathVariable Long companyId, @Valid @RequestBody CreateEmailTemplateAssetRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.TEMPLATE_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, service.register(companyId, request.mediaId()), "Email asset published"));
  }

  @GetMapping("/api/v1/email-assets/{token}")
  public ResponseEntity<Void> publicAsset(@PathVariable String token) {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(service.resolve(token)))
        .cacheControl(CacheControl.noCache())
        .build();
  }
}
