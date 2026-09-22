package com.messaging.emailtemplate.controller;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.emailtemplate.dto.*;
import com.messaging.emailtemplate.enums.*;
import com.messaging.emailtemplate.service.EmailTemplateService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/email-templates")
@RequiredArgsConstructor
public class EmailTemplateController {

  private final EmailTemplateService service;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping
  public ResponseEntity<ApiResponse<Page<EmailTemplateSummaryResponse>>> list(
      @PathVariable Long companyId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) EmailTemplateCategory category,
      @RequestParam(required = false) EmailTemplateStatus status,
      @RequestParam(defaultValue = "false") boolean includeArchived,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "24") int size) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_READ);
    int safeSize = Math.max(1, Math.min(100, size));
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.list(
                companyId,
                q,
                category,
                status,
                includeArchived,
                PageRequest.of(Math.max(0, page), safeSize)),
            "Email templates fetched"));
  }

  @GetMapping("/published")
  public ResponseEntity<ApiResponse<List<EmailTemplateSummaryResponse>>> published(
      @PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.published(companyId), "Published email templates fetched"));
  }

  @GetMapping("/{templateId}")
  public ResponseEntity<ApiResponse<EmailTemplateResponse>> get(
      @PathVariable Long companyId, @PathVariable Long templateId) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.get(companyId, templateId), "Email template fetched"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<EmailTemplateResponse>> create(
      @PathVariable Long companyId, @Valid @RequestBody SaveEmailTemplateRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.TEMPLATE_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, service.create(companyId, userId, request), "Email template created"));
  }

  @PostMapping("/from-catalog/{catalogId}")
  public ResponseEntity<ApiResponse<EmailTemplateResponse>> cloneFromCatalog(
      @PathVariable Long companyId,
      @PathVariable Long catalogId,
      @RequestBody(required = false) Map<String, String> body) {
    Long userId = require(companyId, CompanyPermissionKeys.TEMPLATE_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, service.cloneFromCatalog(companyId, catalogId, userId, body == null ? null : body.get("name")), "Catalog template copied"));
  }

  @PutMapping("/{templateId}")
  public ResponseEntity<ApiResponse<EmailTemplateResponse>> update(
      @PathVariable Long companyId,
      @PathVariable Long templateId,
      @Valid @RequestBody SaveEmailTemplateRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.TEMPLATE_MODIFY);
    return ResponseEntity.ok(ApiResponse.success(200, service.update(companyId, templateId, userId, request), "Email template draft saved"));
  }

  @PostMapping("/{templateId}/publish")
  public ResponseEntity<ApiResponse<EmailTemplateResponse>> publish(
      @PathVariable Long companyId, @PathVariable Long templateId) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_MODIFY);
    return ResponseEntity.ok(ApiResponse.success(200, service.publish(companyId, templateId), "Email template published"));
  }

  @GetMapping("/{templateId}/versions")
  public ResponseEntity<ApiResponse<List<EmailTemplateVersionResponse>>> versions(
      @PathVariable Long companyId, @PathVariable Long templateId) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.versions(companyId, templateId), "Email template versions fetched"));
  }

  @PostMapping("/preview")
  public ResponseEntity<ApiResponse<EmailTemplatePreviewResponse>> preview(
      @PathVariable Long companyId, @Valid @RequestBody SaveEmailTemplateRequest request) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.preview(request), "Email template preview compiled"));
  }

  @DeleteMapping("/{templateId}")
  public ResponseEntity<ApiResponse<Void>> archive(
      @PathVariable Long companyId, @PathVariable Long templateId) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_DELETE);
    service.archive(companyId, templateId);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Email template archived"));
  }

  private Long require(Long companyId, String permission) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }
}
