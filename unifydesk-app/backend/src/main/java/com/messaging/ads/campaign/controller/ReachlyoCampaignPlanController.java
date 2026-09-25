package com.messaging.ads.campaign.controller;

import com.messaging.ads.campaign.compiler.ProviderCampaignPlan;
import com.messaging.ads.campaign.dto.CompileReachlyoCampaignRequest;
import com.messaging.ads.campaign.service.CampaignCompilationService;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/ads/reachlyo-campaigns/{campaignId}")
@RequiredArgsConstructor
public class ReachlyoCampaignPlanController {

  private final CampaignCompilationService compilationService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @PostMapping("/channels/{channelId}/compile")
  public ResponseEntity<ApiResponse<ProviderCampaignPlan>> compile(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long channelId,
      @Valid @RequestBody CompileReachlyoCampaignRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            compilationService.compile(companyId, campaignId, channelId, request),
            "Provider campaign plan compiled"));
  }
}
