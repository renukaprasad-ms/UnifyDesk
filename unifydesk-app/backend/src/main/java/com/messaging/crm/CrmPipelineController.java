package com.messaging.crm;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/crm/pipelines")
@RequiredArgsConstructor
public class CrmPipelineController {

  private final CrmPipelineService pipelineService;
  private final CompanyAuthorizationService authorization;
  private final CurrentUserService currentUser;

  @GetMapping
  public ResponseEntity<ApiResponse<List<CrmPipelineService.PipelineDto>>> list(
      @PathVariable long companyId) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, CompanyPermissionKeys.CRM_DEAL_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, pipelineService.list(companyId), "Pipelines fetched successfully"));
  }

  @GetMapping("/{pipelineId}")
  public ResponseEntity<ApiResponse<CrmPipelineService.PipelineDto>> get(
      @PathVariable long companyId, @PathVariable long pipelineId) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, CompanyPermissionKeys.CRM_DEAL_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, pipelineService.get(companyId, pipelineId), "Pipeline fetched successfully"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<CrmPipelineService.PipelineDto>> create(
      @PathVariable long companyId,
      @Valid @RequestBody CrmPipelineService.CreatePipelineInput input) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, CompanyPermissionKeys.CRM_DEAL_MODIFY);
    return ResponseEntity.status(201)
        .body(ApiResponse.success(201, pipelineService.create(companyId, input, actor), "Pipeline created successfully"));
  }

  @PatchMapping("/{pipelineId}")
  public ResponseEntity<ApiResponse<CrmPipelineService.PipelineDto>> update(
      @PathVariable long companyId,
      @PathVariable long pipelineId,
      @Valid @RequestBody CrmPipelineService.UpdatePipelineInput input) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, CompanyPermissionKeys.CRM_DEAL_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(200, pipelineService.update(companyId, pipelineId, input, actor), "Pipeline updated successfully"));
  }

  @DeleteMapping("/{pipelineId}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable long companyId, @PathVariable long pipelineId) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, CompanyPermissionKeys.CRM_DEAL_MODIFY);
    pipelineService.delete(companyId, pipelineId, actor);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Pipeline deleted successfully"));
  }
}
