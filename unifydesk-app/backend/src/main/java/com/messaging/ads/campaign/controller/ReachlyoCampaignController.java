package com.messaging.ads.campaign.controller;

import com.messaging.ads.campaign.dto.CreateCampaignChannelRequest;
import com.messaging.ads.campaign.dto.CreateReachlyoCampaignRequest;
import com.messaging.ads.campaign.dto.ReachlyoCampaignResponse;
import com.messaging.ads.campaign.dto.ReachlyoCampaignRecommendationsResponse;
import com.messaging.ads.campaign.dto.ReachlyoBudgetRecommendationResponse;
import com.messaging.ads.campaign.dto.ReachlyoCampaignAnalyticsResponse;
import com.messaging.ads.campaign.dto.ReachlyoCampaignAnalyticsSyncResponse;
import com.messaging.ads.campaign.dto.ReachlyoCampaignSummaryResponse;
import com.messaging.ads.campaign.dto.UpdateCampaignChannelRequest;
import com.messaging.ads.campaign.dto.UpdateReachlyoCampaignRequest;
import com.messaging.ads.campaign.dto.UpsertBudgetPlanRequest;
import com.messaging.ads.campaign.service.ReachlyoCampaignService;
import com.messaging.ads.campaign.service.ReachlyoCampaignRecommendationService;
import com.messaging.ads.campaign.service.ReachlyoBudgetRecommendationService;
import com.messaging.ads.campaign.service.ReachlyoCampaignAnalyticsService;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/ads/reachlyo-campaigns")
@RequiredArgsConstructor
public class ReachlyoCampaignController {

  private final ReachlyoCampaignService campaignService;
  private final ReachlyoCampaignAnalyticsService campaignAnalyticsService;
  private final ReachlyoCampaignRecommendationService campaignRecommendationService;
  private final ReachlyoBudgetRecommendationService budgetRecommendationService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping
  public ResponseEntity<ApiResponse<Page<ReachlyoCampaignSummaryResponse>>> list(
      @PathVariable Long companyId,
      @PageableDefault(size = 50, sort = "updatedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    require(companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, campaignService.list(companyId, pageable), "Reachlyo campaigns fetched"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ReachlyoCampaignResponse>> create(
      @PathVariable Long companyId,
      @Valid @RequestBody CreateReachlyoCampaignRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                campaignService.create(companyId, userId, request),
                "Reachlyo campaign created"));
  }

  @GetMapping("/{campaignId}")
  public ResponseEntity<ApiResponse<ReachlyoCampaignResponse>> get(
      @PathVariable Long companyId, @PathVariable Long campaignId) {
    require(companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, campaignService.get(companyId, campaignId), "Reachlyo campaign fetched"));
  }

  @PatchMapping("/{campaignId}")
  public ResponseEntity<ApiResponse<ReachlyoCampaignResponse>> update(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody UpdateReachlyoCampaignRequest request) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            campaignService.update(companyId, campaignId, request),
            "Reachlyo campaign updated"));
  }

  @GetMapping("/{campaignId}/analytics")
  public ResponseEntity<ApiResponse<ReachlyoCampaignAnalyticsResponse>> analytics(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    require(companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            campaignAnalyticsService.analytics(companyId, campaignId, from, to),
            "Unified campaign analytics fetched"));
  }

  @GetMapping("/{campaignId}/recommendations")
  public ResponseEntity<ApiResponse<ReachlyoCampaignRecommendationsResponse>> recommendations(
      @PathVariable Long companyId,
      @PathVariable Long campaignId) {
    require(companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            campaignRecommendationService.recommendations(companyId, campaignId),
            "Unified campaign recommendations fetched"));
  }

  @GetMapping("/{campaignId}/budget-recommendations")
  public ResponseEntity<ApiResponse<ReachlyoBudgetRecommendationResponse>> budgetRecommendations(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    require(companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            budgetRecommendationService.recommend(companyId, campaignId, from, to),
            "Cross-provider budget recommendations fetched"));
  }

  @PostMapping("/{campaignId}/analytics/sync")
  public ResponseEntity<ApiResponse<ReachlyoCampaignAnalyticsSyncResponse>> syncAnalytics(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            campaignAnalyticsService.sync(companyId, campaignId, from, to),
            "Unified campaign analytics synchronized"));
  }

  @PostMapping("/{campaignId}/archive")
  public ResponseEntity<ApiResponse<ReachlyoCampaignResponse>> archive(
      @PathVariable Long companyId, @PathVariable Long campaignId) {
    require(companyId, CompanyPermissionKeys.ADS_DELETE);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, campaignService.archive(companyId, campaignId), "Reachlyo campaign archived"));
  }

  @PostMapping("/{campaignId}/channels")
  public ResponseEntity<ApiResponse<ReachlyoCampaignResponse>> addChannel(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody CreateCampaignChannelRequest request) {
    require(companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                campaignService.addChannel(companyId, campaignId, request),
                "Campaign channel added"));
  }

  @PatchMapping("/{campaignId}/channels/{channelId}")
  public ResponseEntity<ApiResponse<ReachlyoCampaignResponse>> updateChannel(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long channelId,
      @Valid @RequestBody UpdateCampaignChannelRequest request) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            campaignService.updateChannel(companyId, campaignId, channelId, request),
            "Campaign channel updated"));
  }

  @DeleteMapping("/{campaignId}/channels/{channelId}")
  public ResponseEntity<ApiResponse<ReachlyoCampaignResponse>> removeChannel(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long channelId) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            campaignService.removeChannel(companyId, campaignId, channelId),
            "Campaign channel removed"));
  }

  @PutMapping("/{campaignId}/budget")
  public ResponseEntity<ApiResponse<ReachlyoCampaignResponse>> upsertBudget(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody UpsertBudgetPlanRequest request) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            campaignService.upsertBudget(companyId, campaignId, request),
            "Campaign budget plan saved"));
  }

  @DeleteMapping("/{campaignId}/budget")
  public ResponseEntity<ApiResponse<ReachlyoCampaignResponse>> removeBudget(
      @PathVariable Long companyId, @PathVariable Long campaignId) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            campaignService.removeBudget(companyId, campaignId),
            "Campaign budget plan removed"));
  }

  private Long require(Long companyId, String permission) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }
}
