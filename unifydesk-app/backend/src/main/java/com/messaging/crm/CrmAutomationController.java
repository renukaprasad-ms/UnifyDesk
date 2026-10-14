package com.messaging.crm;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/crm/automation")
@RequiredArgsConstructor
public class CrmAutomationController {

  private final CrmAutomationService automationService;
  private final CompanyAuthorizationService authorization;
  private final CurrentUserService currentUser;

  @GetMapping("/rules")
  public ResponseEntity<ApiResponse<List<CrmAutomationService.AutomationRule>>> listRules(
      @PathVariable long companyId) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, automationService.listRules(companyId), "Automation rules fetched"));
  }

  @PostMapping("/rules")
  public ResponseEntity<ApiResponse<CrmAutomationService.AutomationRule>> createRule(
      @PathVariable long companyId,
      @RequestBody CrmAutomationService.SaveRuleInput input) {
    long userId = require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(200, automationService.createRule(companyId, userId, input), "Automation rule created"));
  }

  @PutMapping("/rules/{ruleId}")
  public ResponseEntity<ApiResponse<CrmAutomationService.AutomationRule>> updateRule(
      @PathVariable long companyId,
      @PathVariable long ruleId,
      @RequestBody CrmAutomationService.SaveRuleInput input) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(200, automationService.updateRule(companyId, ruleId, input), "Automation rule updated"));
  }

  @PatchMapping("/rules/{ruleId}/toggle")
  public ResponseEntity<ApiResponse<CrmAutomationService.AutomationRule>> toggleRule(
      @PathVariable long companyId,
      @PathVariable long ruleId,
      @RequestBody Map<String, Boolean> body) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    boolean enabled = body != null && Boolean.TRUE.equals(body.get("isEnabled"));
    return ResponseEntity.ok(
        ApiResponse.success(200, automationService.toggleRule(companyId, ruleId, enabled), "Rule toggle updated"));
  }

  @DeleteMapping("/rules/{ruleId}")
  public ResponseEntity<ApiResponse<Map<String, Object>>> deleteRule(
      @PathVariable long companyId,
      @PathVariable long ruleId) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    automationService.deleteRule(companyId, ruleId);
    return ResponseEntity.ok(
        ApiResponse.success(200, Map.of("success", true, "ruleId", String.valueOf(ruleId)), "Rule deleted"));
  }

  @GetMapping("/logs")
  public ResponseEntity<ApiResponse<List<CrmAutomationService.AutomationLog>>> listLogs(
      @PathVariable long companyId,
      @RequestParam(required = false) Long ruleId,
      @RequestParam(defaultValue = "50") int limit) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, automationService.listLogs(companyId, ruleId, limit), "Automation logs fetched"));
  }

  private long require(long companyId, String permission) {
    long userId = currentUser.currentUserId();
    authorization.requirePermission(userId, companyId, permission);
    return userId;
  }
}
