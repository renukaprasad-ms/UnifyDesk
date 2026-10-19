package com.messaging.crm;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for managing CRM custom field definitions. Base path:
 * /api/v1/companies/{companyId}/crm/custom-fields
 *
 * <p>GET /custom-fields?entityType=LEAD|DEAL|ACCOUNT — list definitions POST /custom-fields —
 * create a definition (admin) PATCH /custom-fields/{fieldId} — update label/options/order (admin)
 * DELETE /custom-fields/{fieldId} — delete and strip values (admin)
 */
@RestController
@RequestMapping("/api/v1/companies/{companyId}/crm/custom-fields")
@RequiredArgsConstructor
public class CrmCustomFieldController {

  private final CrmCustomFieldService service;
  private final CompanyAuthorizationService authorization;
  private final CurrentUserService currentUser;

  public record CreateBody(
      @NotNull CrmCustomFieldService.EntityType entityType,
      @NotBlank String fieldKey,
      @NotBlank String label,
      @NotNull CrmCustomFieldService.FieldType fieldType,
      List<String> options,
      boolean required,
      Integer displayOrder) {}

  public record UpdateBody(
      @NotBlank String label, List<String> options, boolean required, Integer displayOrder) {}

  private long requireRead(long companyId) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, CompanyPermissionKeys.CRM_FIELD_READ);
    return actor;
  }

  private long requireModify(long companyId) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, CompanyPermissionKeys.CRM_FIELD_MODIFY);
    return actor;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<CrmCustomFieldService.FieldDef>>> list(
      @PathVariable long companyId, @RequestParam CrmCustomFieldService.EntityType entityType) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.list(companyId, entityType), "Custom fields fetched"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<CrmCustomFieldService.FieldDef>> create(
      @PathVariable long companyId, @Valid @RequestBody CreateBody body) {
    long actor = requireModify(companyId);
    var input =
        new CrmCustomFieldService.CreateInput(
            body.entityType(),
            body.fieldKey(),
            body.label(),
            body.fieldType(),
            body.options(),
            body.required(),
            body.displayOrder());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201, service.create(companyId, actor, input), "Custom field created"));
  }

  @PatchMapping("/{fieldId}")
  public ResponseEntity<ApiResponse<CrmCustomFieldService.FieldDef>> update(
      @PathVariable long companyId,
      @PathVariable long fieldId,
      @Valid @RequestBody UpdateBody body) {
    requireModify(companyId);
    var input =
        new CrmCustomFieldService.UpdateInput(
            body.label(), body.options(), body.required(), body.displayOrder());
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.update(companyId, fieldId, input), "Custom field updated"));
  }

  @DeleteMapping("/{fieldId}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable long companyId, @PathVariable long fieldId) {
    requireModify(companyId);
    service.delete(companyId, fieldId);
    return ResponseEntity.ok(ApiResponse.success(200, "Custom field deleted"));
  }
}
