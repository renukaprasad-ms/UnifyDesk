package com.messaging.crm;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/crm")
@RequiredArgsConstructor
public class CrmSalesController {
  private final CrmDealsService deals;
  private final CrmActivityService activity;
  private final CrmReportsService reports;
  private final CompanyAuthorizationService authorization;
  private final CurrentUserService currentUser;

  public record Edit(@NotNull Long version, @NotNull CrmDealsService.Input fields) {}

  public record Move(@NotNull Long version, @NotBlank String stage) {}

  public record Note(Long contactId, @NotBlank String body) {}

  private long require(long company, String... permissions) {
    long actor = currentUser.currentUserId();
    for (String permission : permissions)
      authorization.requirePermission(actor, company, permission);
    return actor;
  }

  private long dealRead(long company) {
    return require(
        company,
        CompanyPermissionKeys.CRM_DEAL_READ,
        CompanyPermissionKeys.CONTACT_READ,
        CompanyPermissionKeys.CRM_ACCOUNT_READ);
  }

  @GetMapping("/deals")
  public ResponseEntity<ApiResponse<CrmDealsService.Page>> deals(
      @PathVariable long companyId,
      @RequestParam(required = false) Long pipelineId,
      @RequestParam(required = false) String stage,
      @RequestParam(required = false) Long before,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "50") int size) {
    dealRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, deals.list(companyId, pipelineId, stage, before, q, size), "Deals fetched"));
  }

  @GetMapping("/deals/{dealId}")
  public ResponseEntity<ApiResponse<CrmDealsService.Deal>> deal(
      @PathVariable long companyId, @PathVariable long dealId) {
    dealRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(200, deals.get(companyId, dealId), "Deal fetched"));
  }

  @GetMapping("/deals/{dealId}/history")
  public ResponseEntity<ApiResponse<java.util.List<CrmDealsService.Event>>> history(
      @PathVariable long companyId, @PathVariable long dealId) {
    dealRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(200, deals.history(companyId, dealId), "Deal history fetched"));
  }

  @PostMapping("/deals")
  public ResponseEntity<ApiResponse<CrmDealsService.Deal>> create(
      @PathVariable long companyId, @RequestBody CrmDealsService.Input input) {
    dealRead(companyId);
    long actor = require(companyId, CompanyPermissionKeys.CRM_DEAL_MODIFY);
    return ResponseEntity.status(201)
        .body(ApiResponse.success(201, deals.create(companyId, input, actor), "Deal created"));
  }

  @PatchMapping("/deals/{dealId}")
  public ResponseEntity<ApiResponse<CrmDealsService.Deal>> edit(
      @PathVariable long companyId, @PathVariable long dealId, @Valid @RequestBody Edit input) {
    dealRead(companyId);
    long actor = require(companyId, CompanyPermissionKeys.CRM_DEAL_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            deals.update(companyId, dealId, input.version(), input.fields(), actor),
            "Deal updated"));
  }

  @PatchMapping("/deals/{dealId}/stage")
  public ResponseEntity<ApiResponse<CrmDealsService.Deal>> move(
      @PathVariable long companyId, @PathVariable long dealId, @Valid @RequestBody Move input) {
    dealRead(companyId);
    long actor = require(companyId, CompanyPermissionKeys.CRM_DEAL_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            deals.move(companyId, dealId, input.version(), input.stage(), actor),
            "Deal stage updated"));
  }

  @GetMapping("/activities")
  public ResponseEntity<ApiResponse<CrmActivityService.Page>> activities(
      @PathVariable long companyId,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) String person,
      @RequestParam(required = false) Long contactId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int size) {
    long actor =
        require(
            companyId, CompanyPermissionKeys.CRM_ACTIVITY_READ, CompanyPermissionKeys.CONTACT_READ);
    var access =
        new CrmActivityService.Access(
            authorization.hasPermission(actor, companyId, CompanyPermissionKeys.CRM_LEAD_READ),
            authorization.hasPermission(actor, companyId, CompanyPermissionKeys.CRM_TASK_READ),
            authorization.hasPermission(actor, companyId, CompanyPermissionKeys.CRM_DEAL_READ)
                && authorization.hasPermission(
                    actor, companyId, CompanyPermissionKeys.CRM_ACCOUNT_READ),
            authorization.hasPermission(actor, companyId, CompanyPermissionKeys.EMAIL_READ),
            authorization.hasPermission(actor, companyId, CompanyPermissionKeys.WHATSAPP_READ),
            authorization.hasPermission(actor, companyId, CompanyPermissionKeys.BROADCAST_READ));
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            activity.page(companyId, access, source, person, contactId, from, to, cursor, size),
            "CRM activities fetched"));
  }

  @PostMapping("/activities/notes")
  public ResponseEntity<ApiResponse<Void>> note(
      @PathVariable long companyId, @Valid @RequestBody Note input) {
    long actor =
        require(
            companyId,
            CompanyPermissionKeys.CRM_ACTIVITY_MODIFY,
            CompanyPermissionKeys.CONTACT_READ);
    activity.note(companyId, input.contactId(), input.body(), actor);
    return ResponseEntity.status(201).body(ApiResponse.success(201, "Internal note added"));
  }

  @GetMapping("/reports")
  public ResponseEntity<ApiResponse<CrmReportsService.Report>> report(
      @PathVariable long companyId, @RequestParam(defaultValue = "30") int days) {
    require(
        companyId,
        CompanyPermissionKeys.CRM_REPORT_READ,
        CompanyPermissionKeys.CRM_DEAL_READ,
        CompanyPermissionKeys.CRM_LEAD_READ,
        CompanyPermissionKeys.CRM_TASK_READ,
        CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, reports.report(companyId, days), "CRM report generated"));
  }
}
