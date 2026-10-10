package com.messaging.crm;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/crm/leads")
@RequiredArgsConstructor
public class LeadController {
  private final LeadService service;
  private final CompanyAuthorizationService authorization;
  private final CurrentUserService currentUser;

  public record EnrollRequest(@NotBlank String contactId) {}
  public record MoveRequest(@NotNull LeadService.Stage stage, @NotNull Long version) {}

  @GetMapping
  public ResponseEntity<ApiResponse<LeadService.LeadPage>> list(
      @PathVariable long companyId,
      @RequestParam(required = false) LeadService.Stage stage,
      @RequestParam(required = false) Long before,
      @RequestParam(defaultValue = "50") int size) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.list(companyId, stage, before, size), "Leads fetched"));
  }

  @GetMapping("/summary")
  public ResponseEntity<ApiResponse<LeadService.LeadSummary>> summary(@PathVariable long companyId) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.summary(companyId), "Lead summary"));
  }

  @GetMapping("/{contactId}")
  public ResponseEntity<ApiResponse<LeadService.Lead>> get(
      @PathVariable long companyId, @PathVariable long contactId) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.get(companyId, contactId), "Lead fetched"));
  }

  @GetMapping("/{contactId}/history")
  public ResponseEntity<ApiResponse<java.util.List<LeadService.StageEvent>>> history(
      @PathVariable long companyId, @PathVariable long contactId) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.history(companyId, contactId), "Lead history"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<LeadService.Lead>> enroll(
      @PathVariable long companyId, @Valid @RequestBody EnrollRequest request) {
    long actor = require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    long contactId = parseId(request.contactId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, service.enroll(companyId, contactId, actor), "Lead created"));
  }

  @PatchMapping("/{contactId}/stage")
  public ResponseEntity<ApiResponse<LeadService.Lead>> move(
      @PathVariable long companyId, @PathVariable long contactId,
      @Valid @RequestBody MoveRequest request) {
    long actor = require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.move(companyId, contactId, request.stage(), request.version(), actor), "Lead updated"));
  }

  private long require(long companyId, String permission) {
    long userId = currentUser.currentUserId();
    authorization.requirePermission(userId, companyId, permission);
    return userId;
  }

  private static long parseId(String value) {
    if (!value.matches("[1-9][0-9]{0,18}")) {
      throw new com.messaging.common.exception.ApiException(
          HttpStatus.BAD_REQUEST, "CRM_INVALID_CONTACT_ID");
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new com.messaging.common.exception.ApiException(
          HttpStatus.BAD_REQUEST, "CRM_INVALID_CONTACT_ID");
    }
  }
}
