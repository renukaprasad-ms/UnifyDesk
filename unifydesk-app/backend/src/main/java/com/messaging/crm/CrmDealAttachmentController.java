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
 * Controller for managing CRM Deal attachments (proposals, contracts, documents). Base path:
 * /api/v1/companies/{companyId}/crm/deals/{dealId}/attachments
 */
@RestController
@RequestMapping("/api/v1/companies/{companyId}/crm/deals/{dealId}/attachments")
@RequiredArgsConstructor
public class CrmDealAttachmentController {

  private final CrmDealAttachmentService attachmentService;
  private final CompanyAuthorizationService authorization;
  private final CurrentUserService currentUser;

  private long requireRead(long companyId) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, CompanyPermissionKeys.CRM_DEAL_READ);
    return actor;
  }

  private long requireModify(long companyId) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, CompanyPermissionKeys.CRM_DEAL_MODIFY);
    return actor;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<CrmDealAttachmentService.DealAttachmentDto>>> list(
      @PathVariable long companyId, @PathVariable long dealId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(200, attachmentService.list(companyId, dealId), "Attachments fetched"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<CrmDealAttachmentService.DealAttachmentDto>> attach(
      @PathVariable long companyId,
      @PathVariable long dealId,
      @Valid @RequestBody CrmDealAttachmentService.AttachInput input) {
    long actor = requireModify(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                attachmentService.attach(companyId, dealId, input, actor),
                "Document attached to deal"));
  }

  @DeleteMapping("/{attachmentId}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable long companyId, @PathVariable long dealId, @PathVariable long attachmentId) {
    long actor = requireModify(companyId);
    attachmentService.delete(companyId, dealId, attachmentId, actor);
    return ResponseEntity.ok(ApiResponse.success(200, "Attachment removed"));
  }
}
