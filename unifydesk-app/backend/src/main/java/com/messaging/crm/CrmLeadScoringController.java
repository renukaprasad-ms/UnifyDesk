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

@RestController
@RequestMapping("/api/v1/companies/{companyId}/crm/scoring")
@RequiredArgsConstructor
public class CrmLeadScoringController {

  private final CrmLeadScoringService service;
  private final CompanyAuthorizationService authorization;
  private final CurrentUserService currentUser;

  @GetMapping("/settings")
  public ResponseEntity<ApiResponse<CrmLeadScoringService.ScoreSettingsDto>> getSettings(
      @PathVariable long companyId) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.getSettings(companyId), "Score settings fetched"));
  }

  @PutMapping("/settings")
  public ResponseEntity<ApiResponse<CrmLeadScoringService.ScoreSettingsDto>> updateSettings(
      @PathVariable long companyId,
      @Valid @RequestBody CrmLeadScoringService.SaveSettingsInput input) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    return ResponseEntity.ok(ApiResponse.success(200, service.updateSettings(companyId, input), "Score settings updated"));
  }

  @GetMapping("/rules")
  public ResponseEntity<ApiResponse<List<CrmLeadScoringService.ScoreRuleDto>>> getRules(
      @PathVariable long companyId) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.getRules(companyId), "Score rules fetched"));
  }

  @PostMapping("/rules")
  public ResponseEntity<ApiResponse<CrmLeadScoringService.ScoreRuleDto>> createRule(
      @PathVariable long companyId,
      @Valid @RequestBody CrmLeadScoringService.SaveRuleInput input) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, service.createRule(companyId, input), "Score rule created"));
  }

  @PutMapping("/rules/{ruleId}")
  public ResponseEntity<ApiResponse<CrmLeadScoringService.ScoreRuleDto>> updateRule(
      @PathVariable long companyId,
      @PathVariable long ruleId,
      @Valid @RequestBody CrmLeadScoringService.SaveRuleInput input) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    return ResponseEntity.ok(ApiResponse.success(200, service.updateRule(companyId, ruleId, input), "Score rule updated"));
  }

  @PatchMapping("/rules/{ruleId}/toggle")
  public ResponseEntity<ApiResponse<CrmLeadScoringService.ScoreRuleDto>> toggleRule(
      @PathVariable long companyId,
      @PathVariable long ruleId,
      @RequestParam boolean enabled) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    return ResponseEntity.ok(ApiResponse.success(200, service.toggleRule(companyId, ruleId, enabled), "Score rule toggled"));
  }

  @DeleteMapping("/rules/{ruleId}")
  public ResponseEntity<ApiResponse<Void>> deleteRule(
      @PathVariable long companyId,
      @PathVariable long ruleId) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    service.deleteRule(companyId, ruleId);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Score rule deleted"));
  }

  @PostMapping("/recalculate")
  public ResponseEntity<ApiResponse<Integer>> recalculateAll(@PathVariable long companyId) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    int count = service.recalculateAll(companyId);
    return ResponseEntity.ok(ApiResponse.success(200, count, "Recalculated scores for " + count + " leads"));
  }

  @GetMapping("/leads/{contactId}")
  public ResponseEntity<ApiResponse<CrmLeadScoringService.LeadScoreResult>> getLeadScore(
      @PathVariable long companyId,
      @PathVariable long contactId) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.calculateScore(companyId, contactId), "Lead score breakdown fetched"));
  }

  private void require(long companyId, String permission) {
    long userId = currentUser.currentUserId();
    authorization.requirePermission(userId, companyId, permission);
  }
}
