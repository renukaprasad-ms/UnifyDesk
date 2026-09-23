package com.messaging.ads.controller;

import com.messaging.ads.dto.AdAccountBillingResponse;
import com.messaging.ads.dto.AdAccountHealthResponse;
import com.messaging.ads.dto.AdActivityLogResponse;
import com.messaging.ads.dto.AdAccountResourceResponse;
import com.messaging.ads.dto.AdAccountResponse;
import com.messaging.ads.dto.AdAudienceResponse;
import com.messaging.ads.dto.AdAnalyticsBreakdownResponse;
import com.messaging.ads.dto.AdAnalyticsResponse;
import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.dto.AdConversionResponse;
import com.messaging.ads.dto.AdCreativeResponse;
import com.messaging.ads.dto.AdResponse;
import com.messaging.ads.dto.AdProviderConnectionResponse;
import com.messaging.ads.dto.AdProviderResponse;
import com.messaging.ads.dto.AdRecommendationsResponse;
import com.messaging.ads.dto.AdRecommendationSummaryResponse;
import com.messaging.ads.dto.UpdateAdRecommendationStateRequest;
import com.messaging.ads.dto.AdSetResponse;
import com.messaging.ads.dto.UpdateAdAccountSelectionRequest;
import com.messaging.ads.service.AdAccountBillingService;
import com.messaging.ads.service.AdAccountHealthService;
import com.messaging.ads.service.AdActivityService;
import com.messaging.ads.service.AdAccountResourceService;
import com.messaging.ads.service.AdAudienceService;
import com.messaging.ads.service.AdAnalyticsBreakdownService;
import com.messaging.ads.service.AdAnalyticsService;
import com.messaging.ads.service.AdCampaignService;
import com.messaging.ads.service.AdConversionService;
import com.messaging.ads.service.AdCreativeService;
import com.messaging.ads.service.AdService;
import com.messaging.ads.service.AdCenterService;
import com.messaging.ads.service.AdSetService;
import com.messaging.ads.service.AdRecommendationLifecycleService;
import com.messaging.ads.enums.AdInsightBreakdownType;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/ads")
@RequiredArgsConstructor
public class AdCenterController {

  private final AdCenterService adCenterService;
  private final AdAccountBillingService billingService;
  private final AdAccountHealthService healthService;
  private final AdActivityService activityService;
  private final AdAccountResourceService resourceService;
  private final AdAnalyticsService analyticsService;
  private final AdAnalyticsBreakdownService analyticsBreakdownService;
  private final AdAudienceService audienceService;
  private final AdCampaignService campaignService;
  private final AdConversionService conversionService;
  private final AdCreativeService creativeService;
  private final AdService adService;
  private final AdSetService adSetService;
  private final AdRecommendationLifecycleService recommendationLifecycleService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/providers")
  public ResponseEntity<ApiResponse<List<AdProviderResponse>>> providers(
      @PathVariable Long companyId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(200, adCenterService.providers(), "Advertising providers fetched"));
  }

  @GetMapping("/connections")
  public ResponseEntity<ApiResponse<List<AdProviderConnectionResponse>>> connections(
      @PathVariable Long companyId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, adCenterService.connections(companyId), "Advertising connections fetched"));
  }

  @GetMapping("/accounts")
  public ResponseEntity<ApiResponse<List<AdAccountResponse>>> accounts(
      @PathVariable Long companyId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(200, adCenterService.accounts(companyId), "Ad accounts fetched"));
  }

  @GetMapping("/recommendations/summary")
  public ResponseEntity<ApiResponse<AdRecommendationSummaryResponse>> recommendationSummary(
      @PathVariable Long companyId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            recommendationLifecycleService.summary(companyId),
            "Advertising recommendation summary fetched"));
  }

  @GetMapping("/accounts/{accountId}/recommendations")
  public ResponseEntity<ApiResponse<AdRecommendationsResponse>> recommendations(
      @PathVariable Long companyId,
      @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            recommendationLifecycleService.feed(companyId, accountId),
            "Advertising recommendations fetched"));
  }

  @PostMapping("/accounts/{accountId}/recommendations/reconcile")
  public ResponseEntity<ApiResponse<AdRecommendationsResponse>> reconcileRecommendations(
      @PathVariable Long companyId,
      @PathVariable Long accountId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            recommendationLifecycleService.reconcile(companyId, accountId, userId),
            "Advertising recommendation lifecycle reconciled"));
  }

  @PatchMapping("/accounts/{accountId}/recommendations/{recommendationKey}")
  public ResponseEntity<ApiResponse<AdRecommendationsResponse>> updateRecommendationState(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String recommendationKey,
      @Valid @RequestBody UpdateAdRecommendationStateRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            recommendationLifecycleService.updateState(
                companyId, accountId, recommendationKey, request, userId),
            "Advertising recommendation state updated"));
  }

  @GetMapping("/accounts/{accountId}/health")
  public ResponseEntity<ApiResponse<AdAccountHealthResponse>> health(
      @PathVariable Long companyId,
      @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            healthService.get(companyId, accountId),
            "Advertising account health fetched"));
  }

  @GetMapping("/activity")
  public ResponseEntity<ApiResponse<Page<AdActivityLogResponse>>> activity(
      @PathVariable Long companyId,
      @RequestParam(required = false) Long accountId,
      @PageableDefault(size = 50) Pageable pageable) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            activityService.list(companyId, accountId, pageable),
            "Advertising activity fetched"));
  }

  @GetMapping("/accounts/{accountId}/billing")
  public ResponseEntity<ApiResponse<AdAccountBillingResponse>> billing(
      @PathVariable Long companyId,
      @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            billingService.get(companyId, accountId),
            "Advertising account billing fetched"));
  }

  @GetMapping("/accounts/{accountId}/analytics")
  public ResponseEntity<ApiResponse<AdAnalyticsResponse>> analytics(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(defaultValue = "CAMPAIGN") AdInsightLevel level) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            analyticsService.analytics(companyId, accountId, from, to, level),
            "Advertising analytics fetched"));
  }

  @GetMapping("/accounts/{accountId}/analytics/breakdowns")
  public ResponseEntity<ApiResponse<AdAnalyticsBreakdownResponse>> analyticsBreakdown(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam AdInsightBreakdownType type) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            analyticsBreakdownService.breakdown(companyId, accountId, from, to, type),
            "Advertising analytics breakdown fetched"));
  }

  @GetMapping("/accounts/{accountId}/audiences")
  public ResponseEntity<ApiResponse<List<AdAudienceResponse>>> audiences(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, audienceService.list(companyId, accountId), "Advertising audiences fetched"));
  }

  @GetMapping("/accounts/{accountId}/conversions")
  public ResponseEntity<ApiResponse<List<AdConversionResponse>>> conversions(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, conversionService.list(companyId, accountId), "Advertising conversions fetched"));
  }
  @GetMapping("/accounts/{accountId}/creatives")
  public ResponseEntity<ApiResponse<List<AdCreativeResponse>>> creatives(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, creativeService.list(companyId, accountId), "Advertising creatives fetched"));
  }

  @GetMapping("/creatives/{creativeId}")
  public ResponseEntity<ApiResponse<AdCreativeResponse>> creative(
      @PathVariable Long companyId, @PathVariable Long creativeId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, creativeService.get(companyId, creativeId), "Advertising creative fetched"));
  }

  @GetMapping("/ad-sets/{adSetId}/ads")
  public ResponseEntity<ApiResponse<List<AdResponse>>> ads(
      @PathVariable Long companyId, @PathVariable Long adSetId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(200, adService.list(companyId, adSetId), "Advertising ads fetched"));
  }

  @GetMapping("/ads/{adId}")
  public ResponseEntity<ApiResponse<AdResponse>> ad(
      @PathVariable Long companyId, @PathVariable Long adId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(200, adService.get(companyId, adId), "Advertising ad fetched"));
  }
  @GetMapping("/campaigns/{campaignId}/ad-sets")
  public ResponseEntity<ApiResponse<List<AdSetResponse>>> adSets(
      @PathVariable Long companyId, @PathVariable Long campaignId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, adSetService.list(companyId, campaignId), "Advertising ad sets fetched"));
  }

  @GetMapping("/ad-sets/{adSetId}")
  public ResponseEntity<ApiResponse<AdSetResponse>> adSet(
      @PathVariable Long companyId, @PathVariable Long adSetId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(200, adSetService.get(companyId, adSetId), "Advertising ad set fetched"));
  }
  @GetMapping("/campaigns/{campaignId}")
  public ResponseEntity<ApiResponse<AdCampaignResponse>> campaign(
      @PathVariable Long companyId, @PathVariable Long campaignId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, campaignService.get(companyId, campaignId), "Advertising campaign fetched"));
  }

  @GetMapping("/campaigns")
  public ResponseEntity<ApiResponse<Page<AdCampaignResponse>>> campaigns(
      @PathVariable Long companyId,
      @RequestParam(required = false) Long accountId,
      @PageableDefault(size = 50, sort = "providerUpdatedAt") Pageable pageable) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            campaignService.list(companyId, accountId, pageable),
            "Advertising campaigns fetched"));
  }

  @GetMapping("/resources")
  public ResponseEntity<ApiResponse<List<AdAccountResourceResponse>>> resources(
      @PathVariable Long companyId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, resourceService.listCompany(companyId), "Advertising resources fetched"));
  }

  @GetMapping("/accounts/{accountId}/resources")
  public ResponseEntity<ApiResponse<List<AdAccountResourceResponse>>> resources(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            resourceService.list(companyId, accountId),
            "Ad account resources fetched"));
  }

  @PatchMapping("/accounts/{accountId}")
  public ResponseEntity<ApiResponse<AdAccountResponse>> updateAccount(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody UpdateAdAccountSelectionRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            adCenterService.updateSelection(companyId, accountId, request, userId),
            "Ad account selection updated"));
  }

  @PostMapping("/connections/{connectionId}/disconnect")
  public ResponseEntity<ApiResponse<AdProviderConnectionResponse>> disconnect(
      @PathVariable Long companyId, @PathVariable Long connectionId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_DISCONNECT);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            adCenterService.disconnect(companyId, connectionId, userId),
            "Advertising provider disconnected"));
  }

  private void requireRead(Long companyId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.ADS_READ);
  }
}
