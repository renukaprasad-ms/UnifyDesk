package com.messaging.template.controller;

import com.messaging.channel.enums.ChannelType;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import com.messaging.template.dto.CreateMessageTemplateRequest;
import com.messaging.template.dto.MessageTemplateResponse;
import com.messaging.template.dto.UpdateMessageTemplateRequest;
import com.messaging.template.service.MessageTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/templates")
@RequiredArgsConstructor
public class MessageTemplateController {

  private final MessageTemplateService templateService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<MessageTemplateResponse>>> list(
      @PathVariable Long companyId,
      @RequestParam(required = false) ChannelType channelType,
      @RequestParam(defaultValue = "false") boolean includeArchived) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            templateService.list(companyId, channelType, includeArchived),
            "Templates fetched"));
  }

  @GetMapping("/active")
  public ResponseEntity<ApiResponse<List<MessageTemplateResponse>>> active(
      @PathVariable Long companyId, @RequestParam ChannelType channelType) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            templateService.active(companyId, channelType),
            "Active templates fetched"));
  }

  @GetMapping("/{templateId}")
  public ResponseEntity<ApiResponse<MessageTemplateResponse>> get(
      @PathVariable Long companyId, @PathVariable Long templateId) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, templateService.get(companyId, templateId), "Template fetched"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<MessageTemplateResponse>> create(
      @PathVariable Long companyId, @Valid @RequestBody CreateMessageTemplateRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.TEMPLATE_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                templateService.create(companyId, userId, request),
                "Template created"));
  }

  @PutMapping("/{templateId}")
  public ResponseEntity<ApiResponse<MessageTemplateResponse>> update(
      @PathVariable Long companyId,
      @PathVariable Long templateId,
      @Valid @RequestBody UpdateMessageTemplateRequest request) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            templateService.update(companyId, templateId, request),
            "Template updated"));
  }

  @PostMapping("/{templateId}/activate")
  public ResponseEntity<ApiResponse<MessageTemplateResponse>> activate(
      @PathVariable Long companyId, @PathVariable Long templateId) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, templateService.activate(companyId, templateId), "Template activated"));
  }

  @PostMapping("/{templateId}/draft")
  public ResponseEntity<ApiResponse<MessageTemplateResponse>> draft(
      @PathVariable Long companyId, @PathVariable Long templateId) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, templateService.moveToDraft(companyId, templateId), "Template moved to draft"));
  }

  @DeleteMapping("/{templateId}")
  public ResponseEntity<ApiResponse<Void>> archive(
      @PathVariable Long companyId, @PathVariable Long templateId) {
    require(companyId, CompanyPermissionKeys.TEMPLATE_DELETE);
    templateService.archive(companyId, templateId);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Template archived"));
  }

  private Long require(Long companyId, String permission) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }
}
