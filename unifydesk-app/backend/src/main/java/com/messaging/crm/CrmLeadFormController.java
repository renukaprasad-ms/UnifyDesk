package com.messaging.crm;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for managing CRM Web-to-Lead Forms. Base path: /api/v1/companies/{companyId}/crm/forms
 */
@RestController
@RequestMapping("/api/v1/companies/{companyId}/crm/forms")
@RequiredArgsConstructor
public class CrmLeadFormController {

  private final CrmLeadFormService service;
  private final CompanyAuthorizationService authorization;
  private final CurrentUserService currentUser;

  private long requireRead(long companyId) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, CompanyPermissionKeys.CRM_LEAD_READ);
    return actor;
  }

  private long requireModify(long companyId) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    return actor;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<CrmLeadFormService.LeadFormDto>>> listForms(
      @PathVariable long companyId) {
    requireRead(companyId);
    return ResponseEntity.ok(ApiResponse.success(200, service.list(companyId), "Forms fetched"));
  }

  @GetMapping("/{formId}")
  public ResponseEntity<ApiResponse<CrmLeadFormService.LeadFormDto>> getForm(
      @PathVariable long companyId, @PathVariable long formId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.get(companyId, formId), "Form fetched"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<CrmLeadFormService.LeadFormDto>> createForm(
      @PathVariable long companyId, @Valid @RequestBody CrmLeadFormService.CreateFormInput input) {
    long actor = requireModify(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, service.create(companyId, input, actor), "Form created"));
  }

  @PatchMapping("/{formId}")
  public ResponseEntity<ApiResponse<CrmLeadFormService.LeadFormDto>> updateForm(
      @PathVariable long companyId,
      @PathVariable long formId,
      @Valid @RequestBody CrmLeadFormService.UpdateFormInput input) {
    long actor = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.update(companyId, formId, input, actor), "Form updated"));
  }

  @DeleteMapping("/{formId}")
  public ResponseEntity<ApiResponse<Void>> deleteForm(
      @PathVariable long companyId, @PathVariable long formId) {
    long actor = requireModify(companyId);
    service.delete(companyId, formId, actor);
    return ResponseEntity.ok(ApiResponse.success(200, "Form deleted"));
  }

  @GetMapping("/{formId}/submissions")
  public ResponseEntity<ApiResponse<List<CrmLeadFormService.FormSubmissionDto>>> listSubmissions(
      @PathVariable long companyId,
      @PathVariable long formId,
      @RequestParam(defaultValue = "50") int limit) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.submissions(companyId, formId, limit), "Submissions fetched"));
  }
}
