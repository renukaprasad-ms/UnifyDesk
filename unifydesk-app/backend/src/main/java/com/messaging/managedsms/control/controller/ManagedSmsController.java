package com.messaging.managedsms.control.controller;

import com.messaging.broadcast.dto.BroadcastSenderResponse;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.*;
import com.messaging.managedsms.control.service.*;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({
  "/api/v1/companies/{companyId}/managed-sms",
  "/api/companies/{companyId}/managed-sms"
})
@RequiredArgsConstructor
public class ManagedSmsController {

  private final ManagedSmsStatusService statusService;
  private final ManagedSmsProfileService profileService;
  private final ManagedSmsHeaderService headerService;
  private final ManagedSmsTemplateService templateService;
  private final ManagedSmsSenderService senderService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/status")
  public ResponseEntity<ApiResponse<StatusResponse>> status(@PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, statusService.get(companyId), "Managed SMS status fetched"));
  }

  @PostMapping("/dlt-profile")
  public ResponseEntity<ApiResponse<DltProfileResponse>> createProfile(
      @PathVariable Long companyId, @Valid @RequestBody DltProfileRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                profileService.create(companyId, userId, request),
                "Managed SMS DLT profile created"));
  }

  @GetMapping("/dlt-profile")
  public ResponseEntity<ApiResponse<DltProfileResponse>> profile(@PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, profileService.get(companyId), "Managed SMS DLT profile fetched"));
  }

  @PatchMapping("/dlt-profile/{profileId}")
  public ResponseEntity<ApiResponse<DltProfileResponse>> patchProfile(
      @PathVariable Long companyId,
      @PathVariable Long profileId,
      @Valid @RequestBody DltProfilePatchRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            profileService.patch(companyId, profileId, userId, request),
            "Managed SMS DLT profile updated"));
  }

  @PostMapping("/dlt-profile/{profileId}/submit")
  public ResponseEntity<ApiResponse<DltProfileResponse>> submitProfile(
      @PathVariable Long companyId, @PathVariable Long profileId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            profileService.submit(companyId, profileId),
            "Managed SMS DLT profile submitted"));
  }

  @PostMapping("/dlt-profile/{profileId}/refresh")
  public ResponseEntity<ApiResponse<DltProfileResponse>> refreshProfile(
      @PathVariable Long companyId, @PathVariable Long profileId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.accepted()
        .body(
            ApiResponse.success(
                202,
                profileService.refresh(companyId, profileId),
                "Managed SMS DLT refresh queued"));
  }

  @GetMapping("/headers")
  public ResponseEntity<ApiResponse<List<HeaderResponse>>> headers(@PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, headerService.list(companyId), "Managed SMS Headers fetched"));
  }

  @PostMapping("/headers")
  public ResponseEntity<ApiResponse<HeaderResponse>> createHeader(
      @PathVariable Long companyId, @Valid @RequestBody HeaderRequest request) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201, headerService.create(companyId, request), "Managed SMS Header created"));
  }

  @GetMapping("/headers/{headerId}")
  public ResponseEntity<ApiResponse<HeaderResponse>> header(
      @PathVariable Long companyId, @PathVariable Long headerId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, headerService.get(companyId, headerId), "Managed SMS Header fetched"));
  }

  @PostMapping("/headers/{headerId}/submit")
  public ResponseEntity<ApiResponse<HeaderResponse>> submitHeader(
      @PathVariable Long companyId, @PathVariable Long headerId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, headerService.submit(companyId, headerId), "Managed SMS Header submitted"));
  }

  @PostMapping("/headers/{headerId}/refresh")
  public ResponseEntity<ApiResponse<HeaderResponse>> refreshHeader(
      @PathVariable Long companyId, @PathVariable Long headerId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.accepted()
        .body(
            ApiResponse.success(
                202, headerService.refresh(companyId, headerId), "Managed SMS Header refresh queued"));
  }

  @DeleteMapping("/headers/{headerId}")
  public ResponseEntity<ApiResponse<HeaderResponse>> archiveHeader(
      @PathVariable Long companyId, @PathVariable Long headerId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, headerService.archive(companyId, headerId), "Managed SMS Header archived"));
  }

  @GetMapping("/templates")
  public ResponseEntity<ApiResponse<List<TemplateResponse>>> templates(@PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, templateService.list(companyId), "Managed SMS templates fetched"));
  }

  @PostMapping("/templates")
  public ResponseEntity<ApiResponse<TemplateResponse>> createTemplate(
      @PathVariable Long companyId, @Valid @RequestBody TemplateRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                templateService.create(companyId, userId, request),
                "Managed SMS template created"));
  }

  @GetMapping("/templates/{templateId}")
  public ResponseEntity<ApiResponse<TemplateResponse>> template(
      @PathVariable Long companyId, @PathVariable Long templateId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, templateService.get(companyId, templateId), "Managed SMS template fetched"));
  }

  @PostMapping("/templates/{templateId}/versions")
  public ResponseEntity<ApiResponse<TemplateResponse>> createTemplateVersion(
      @PathVariable Long companyId,
      @PathVariable Long templateId,
      @Valid @RequestBody TemplateVersionRequest request) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                templateService.createVersion(companyId, templateId, request),
                "Managed SMS template version created"));
  }

  @PostMapping("/templates/{templateId}/versions/{versionId}/submit")
  public ResponseEntity<ApiResponse<TemplateResponse>> submitTemplateVersion(
      @PathVariable Long companyId,
      @PathVariable Long templateId,
      @PathVariable Long versionId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            templateService.submit(companyId, templateId, versionId),
            "Managed SMS template submitted"));
  }

  @PostMapping("/templates/{templateId}/versions/{versionId}/refresh")
  public ResponseEntity<ApiResponse<TemplateResponse>> refreshTemplateVersion(
      @PathVariable Long companyId,
      @PathVariable Long templateId,
      @PathVariable Long versionId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.accepted()
        .body(
            ApiResponse.success(
                202,
                templateService.refresh(companyId, templateId, versionId),
                "Managed SMS template refresh queued"));
  }

  @PostMapping("/templates/{templateId}/versions/{versionId}/archive")
  public ResponseEntity<ApiResponse<TemplateResponse>> archiveTemplateVersion(
      @PathVariable Long companyId,
      @PathVariable Long templateId,
      @PathVariable Long versionId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            templateService.archive(companyId, templateId, versionId),
            "Managed SMS template version archived"));
  }

  @PostMapping("/templates/{templateId}/preview")
  public ResponseEntity<ApiResponse<PreviewResponse>> preview(
      @PathVariable Long companyId,
      @PathVariable Long templateId,
      @Valid @RequestBody PreviewRequest request) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            templateService.preview(companyId, templateId, request),
            "Managed SMS template preview generated"));
  }

  @GetMapping("/senders")
  public ResponseEntity<ApiResponse<List<BroadcastSenderResponse>>> senders(
      @PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, senderService.list(companyId), "Managed SMS senders fetched"));
  }

  @PostMapping("/senders")
  public ResponseEntity<ApiResponse<BroadcastSenderResponse>> createSender(
      @PathVariable Long companyId, @Valid @RequestBody CreateSenderRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                senderService.create(companyId, userId, request),
                "Managed SMS sender created"));
  }

  private Long require(Long companyId, String permission) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }
}
