package com.messaging.ads.campaign.controller;

import com.messaging.ads.campaign.dto.LaunchRunResponse;
import com.messaging.ads.campaign.dto.StartCampaignLaunchRequest;
import com.messaging.ads.campaign.launch.CampaignLaunchOrchestrator;
import com.messaging.ads.campaign.launch.CampaignLaunchPlanningService;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/ads/reachlyo-campaigns/{campaignId}/launch-runs")
@RequiredArgsConstructor
public class ReachlyoCampaignLaunchController {

  private final CampaignLaunchPlanningService planningService;
  private final CampaignLaunchOrchestrator orchestrator;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @PostMapping("/prepare")
  public ResponseEntity<ApiResponse<LaunchRunResponse>> prepare(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody StartCampaignLaunchRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            planningService.prepare(companyId, campaignId, userId, request),
            "Campaign launch run prepared"));
  }

  @PostMapping("/{runId}/execute")
  public ResponseEntity<ApiResponse<LaunchRunResponse>> execute(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long runId) {
    require(companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            orchestrator.execute(companyId, campaignId, runId),
            "Campaign launch run executed"));
  }

  @PostMapping("/{runId}/retry")
  public ResponseEntity<ApiResponse<LaunchRunResponse>> retry(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long runId) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            orchestrator.execute(companyId, campaignId, runId),
            "Campaign launch run retried"));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<LaunchRunResponse>>> list(
      @PathVariable Long companyId, @PathVariable Long campaignId) {
    require(companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, planningService.list(companyId, campaignId), "Campaign launch runs fetched"));
  }

  @GetMapping("/{runId}")
  public ResponseEntity<ApiResponse<LaunchRunResponse>> get(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long runId) {
    require(companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            planningService.get(companyId, campaignId, runId),
            "Campaign launch run fetched"));
  }

  private Long require(Long companyId, String permission) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }
}
