package com.messaging.ads.google;

import com.messaging.ads.dto.AdAccountBillingResponse;
import com.messaging.ads.dto.AdAccountHealthResponse;
import com.messaging.ads.dto.AdAudienceResponse;
import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.dto.AdConversionResponse;
import com.messaging.ads.dto.AdKeywordResponse;
import com.messaging.ads.dto.AdResponse;
import com.messaging.ads.dto.AdSetResponse;
import com.messaging.ads.google.dto.CreateGoogleAdGroupRequest;
import com.messaging.ads.google.dto.CreateGoogleAudienceRequest;
import com.messaging.ads.google.dto.CreateGoogleCampaignRequest;
import com.messaging.ads.google.dto.CreateGoogleConversionRequest;
import com.messaging.ads.google.dto.CreateGoogleKeywordRequest;
import com.messaging.ads.google.dto.CreateGooglePerformanceMaxCampaignRequest;
import com.messaging.ads.google.dto.CreateGoogleResponsiveSearchAdRequest;
import com.messaging.ads.google.dto.GoogleAdGroupStatusRequest;
import com.messaging.ads.google.dto.GoogleAdGroupSyncResponse;
import com.messaging.ads.google.dto.GoogleAdStatusRequest;
import com.messaging.ads.google.dto.GoogleAdSyncResponse;
import com.messaging.ads.google.dto.GoogleAdsAuthorizationResponse;
import com.messaging.ads.google.dto.GoogleAudienceSyncResponse;
import com.messaging.ads.google.dto.GoogleCampaignStatusRequest;
import com.messaging.ads.google.dto.GoogleCampaignSyncResponse;
import com.messaging.ads.google.dto.GoogleConversionSyncResponse;
import com.messaging.ads.google.dto.GoogleInsightsSyncResponse;
import com.messaging.ads.google.dto.GoogleInsightBreakdownsSyncResponse;
import com.messaging.ads.google.dto.GoogleKeywordSyncResponse;
import com.messaging.ads.google.dto.GoogleFullSyncResponse;
import com.messaging.ads.google.dto.GoogleAssetGroupResponse;
import com.messaging.ads.google.dto.GoogleAssetGroupStatusRequest;
import com.messaging.ads.google.dto.GoogleAssetGroupSignalResponse;
import com.messaging.ads.google.dto.GoogleAssetGroupSignalsRequest;
import com.messaging.ads.google.dto.GoogleCustomerMatchUploadRequest;
import com.messaging.ads.google.dto.GoogleConversionEventUploadRequest;
import com.messaging.ads.google.dto.GoogleDataManagerUploadResponse;
import com.messaging.ads.google.dto.GoogleNativeRecommendation;
import com.messaging.ads.google.dto.GoogleRecommendationActionRequest;
import com.messaging.ads.google.dto.GoogleRecommendationActionResponse;
import com.messaging.ads.google.dto.GoogleAssetResponse;
import com.messaging.ads.google.dto.UpdateGoogleAdGroupRequest;
import com.messaging.ads.google.dto.UpdateGoogleCampaignRequest;
import com.messaging.ads.google.dto.UpdateGoogleKeywordRequest;
import com.messaging.ads.google.dto.UpdateGoogleResponsiveSearchAdRequest;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/ads/google")
@RequiredArgsConstructor
public class GoogleAdsConnectionController {

  private final GoogleAdsConnectionService connectionService;
  private final GoogleAdsCampaignSyncService campaignSyncService;
  private final GoogleAdsCampaignMutationService campaignMutationService;
  private final GoogleAdsAdGroupSyncService adGroupSyncService;
  private final GoogleAdsAdGroupMutationService adGroupMutationService;
  private final GoogleAdsKeywordSyncService keywordSyncService;
  private final GoogleAdsKeywordMutationService keywordMutationService;
  private final GoogleAdsAdSyncService adSyncService;
  private final GoogleAdsAdMutationService adMutationService;
  private final GoogleAdsAudienceSyncService audienceSyncService;
  private final GoogleAdsAudienceMutationService audienceMutationService;
  private final GoogleAdsConversionSyncService conversionSyncService;
  private final GoogleAdsConversionMutationService conversionMutationService;
  private final GoogleAdsInsightSyncService insightSyncService;
  private final GoogleAdsInsightBreakdownSyncService insightBreakdownSyncService;
  private final GoogleAdsHealthService healthService;
  private final GoogleAdsBillingService billingService;
  private final GoogleAdsFullSyncService fullSyncService;
  private final GoogleAdsPerformanceMaxService performanceMaxService;
  private final GoogleAdsAssetGroupSignalService assetGroupSignalService;
  private final GoogleDataManagerService dataManagerService;
  private final GoogleAdsNativeRecommendationService nativeRecommendationService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/authorize")
  public ResponseEntity<ApiResponse<GoogleAdsAuthorizationResponse>> authorize(
      @PathVariable Long companyId,
      @RequestParam(required = false) String returnTo) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_CONNECT);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.authorization(companyId, userId, returnTo),
            "Google Ads authorization created"));
  }

  @PostMapping("/accounts/{accountId}/sync")
  public ResponseEntity<ApiResponse<GoogleFullSyncResponse>> syncAll(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            fullSyncService.sync(companyId, accountId),
            "Google Ads account fully synchronized"));
  }

  @GetMapping("/accounts/{accountId}/assets")
  public ResponseEntity<ApiResponse<java.util.List<GoogleAssetResponse>>> assets(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxService.assets(companyId, accountId),
            "Google Ads asset library fetched"));
  }

  @GetMapping("/campaigns/{campaignId}/asset-groups")
  public ResponseEntity<ApiResponse<java.util.List<GoogleAssetGroupResponse>>> assetGroups(
      @PathVariable Long companyId, @PathVariable Long campaignId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxService.assetGroups(companyId, campaignId),
            "Google Performance Max asset groups fetched"));
  }

  @GetMapping("/campaigns/{campaignId}/asset-groups/{assetGroupId}/signals")
  public ResponseEntity<ApiResponse<java.util.List<GoogleAssetGroupSignalResponse>>> assetGroupSignals(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable String assetGroupId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            assetGroupSignalService.list(companyId, campaignId, assetGroupId),
            "Google Performance Max signals fetched"));
  }

  @PostMapping("/campaigns/{campaignId}/asset-groups/{assetGroupId}/signals")
  public ResponseEntity<ApiResponse<java.util.List<GoogleAssetGroupSignalResponse>>> addAssetGroupSignals(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable String assetGroupId,
      @RequestBody GoogleAssetGroupSignalsRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            assetGroupSignalService.add(
                companyId, campaignId, assetGroupId, request, userId),
            "Google Performance Max signals updated"));
  }

  @PostMapping("/campaigns/{campaignId}/asset-groups/{assetGroupId}/status")
  public ResponseEntity<ApiResponse<GoogleAssetGroupResponse>> updateAssetGroupStatus(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable String assetGroupId,
      @Valid @RequestBody GoogleAssetGroupStatusRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxService.setAssetGroupStatus(
                companyId, campaignId, assetGroupId, request.status(), userId),
            "Google Performance Max asset group status updated"));
  }

  @PostMapping("/accounts/{accountId}/performance-max-campaigns")
  public ResponseEntity<ApiResponse<AdCampaignResponse>> createPerformanceMaxCampaign(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody CreateGooglePerformanceMaxCampaignRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                performanceMaxService.create(companyId, accountId, request, userId),
                "Google Performance Max campaign created in paused state"));
  }

  @PostMapping("/accounts/{accountId}/campaigns/sync")
  public ResponseEntity<ApiResponse<GoogleCampaignSyncResponse>> syncCampaigns(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            campaignSyncService.sync(companyId, accountId),
            "Google Ads campaigns synchronized"));
  }

  @PostMapping("/accounts/{accountId}/campaigns")
  public ResponseEntity<ApiResponse<AdCampaignResponse>> createCampaign(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody CreateGoogleCampaignRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                campaignMutationService.create(companyId, accountId, request, userId),
                "Google Search campaign created in paused state"));
  }

  @PatchMapping("/campaigns/{campaignId}")
  public ResponseEntity<ApiResponse<AdCampaignResponse>> updateCampaign(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody UpdateGoogleCampaignRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            campaignMutationService.update(companyId, campaignId, request, userId),
            "Google Ads campaign updated"));
  }

  @PostMapping("/campaigns/{campaignId}/status")
  public ResponseEntity<ApiResponse<AdCampaignResponse>> updateCampaignStatus(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody GoogleCampaignStatusRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            campaignMutationService.setStatus(
                companyId, campaignId, request.status(), userId),
            "Google Ads campaign status updated"));
  }

  @PostMapping("/campaigns/{campaignId}/ad-groups/sync")
  public ResponseEntity<ApiResponse<GoogleAdGroupSyncResponse>> syncAdGroups(
      @PathVariable Long companyId, @PathVariable Long campaignId) {
    requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            adGroupSyncService.sync(companyId, campaignId),
            "Google Ads ad groups synchronized"));
  }

  @PostMapping("/campaigns/{campaignId}/ad-groups")
  public ResponseEntity<ApiResponse<AdSetResponse>> createAdGroup(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody CreateGoogleAdGroupRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                adGroupMutationService.create(companyId, campaignId, request, userId),
                "Google Ads ad group created in paused state"));
  }

  @PatchMapping("/ad-groups/{adGroupId}")
  public ResponseEntity<ApiResponse<AdSetResponse>> updateAdGroup(
      @PathVariable Long companyId,
      @PathVariable Long adGroupId,
      @Valid @RequestBody UpdateGoogleAdGroupRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            adGroupMutationService.update(companyId, adGroupId, request, userId),
            "Google Ads ad group updated"));
  }

  @PostMapping("/ad-groups/{adGroupId}/status")
  public ResponseEntity<ApiResponse<AdSetResponse>> updateAdGroupStatus(
      @PathVariable Long companyId,
      @PathVariable Long adGroupId,
      @Valid @RequestBody GoogleAdGroupStatusRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            adGroupMutationService.setStatus(
                companyId, adGroupId, request.status(), userId),
            "Google Ads ad group status updated"));
  }

  @PostMapping("/ad-groups/{adGroupId}/keywords/sync")
  public ResponseEntity<ApiResponse<GoogleKeywordSyncResponse>> syncKeywords(
      @PathVariable Long companyId, @PathVariable Long adGroupId) {
    requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            keywordSyncService.sync(companyId, adGroupId),
            "Google Ads keywords synchronized"));
  }

  @PostMapping("/ad-groups/{adGroupId}/keywords")
  public ResponseEntity<ApiResponse<AdKeywordResponse>> createKeyword(
      @PathVariable Long companyId,
      @PathVariable Long adGroupId,
      @Valid @RequestBody CreateGoogleKeywordRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                keywordMutationService.create(companyId, adGroupId, request, userId),
                "Google Ads keyword created"));
  }

  @PatchMapping("/keywords/{keywordId}")
  public ResponseEntity<ApiResponse<AdKeywordResponse>> updateKeyword(
      @PathVariable Long companyId,
      @PathVariable Long keywordId,
      @Valid @RequestBody UpdateGoogleKeywordRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            keywordMutationService.update(companyId, keywordId, request, userId),
            "Google Ads keyword updated"));
  }

  @PostMapping("/ad-groups/{adGroupId}/ads/sync")
  public ResponseEntity<ApiResponse<GoogleAdSyncResponse>> syncAds(
      @PathVariable Long companyId, @PathVariable Long adGroupId) {
    requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            adSyncService.sync(companyId, adGroupId),
            "Google Ads ads synchronized"));
  }

  @PostMapping("/ad-groups/{adGroupId}/ads")
  public ResponseEntity<ApiResponse<AdResponse>> createAd(
      @PathVariable Long companyId,
      @PathVariable Long adGroupId,
      @Valid @RequestBody CreateGoogleResponsiveSearchAdRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                adMutationService.create(companyId, adGroupId, request, userId),
                "Google responsive search ad created in paused state"));
  }

  @PatchMapping("/ads/{adId}")
  public ResponseEntity<ApiResponse<AdResponse>> updateAd(
      @PathVariable Long companyId,
      @PathVariable Long adId,
      @Valid @RequestBody UpdateGoogleResponsiveSearchAdRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            adMutationService.update(companyId, adId, request, userId),
            "Google responsive search ad updated"));
  }

  @PostMapping("/ads/{adId}/status")
  public ResponseEntity<ApiResponse<AdResponse>> updateAdStatus(
      @PathVariable Long companyId,
      @PathVariable Long adId,
      @Valid @RequestBody GoogleAdStatusRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            adMutationService.setStatus(companyId, adId, request.status(), userId),
            "Google Ads ad status updated"));
  }

  @PostMapping("/accounts/{accountId}/audiences/sync")
  public ResponseEntity<ApiResponse<GoogleAudienceSyncResponse>> syncAudiences(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            audienceSyncService.sync(companyId, accountId),
            "Google Ads audiences synchronized"));
  }

  @PostMapping("/audiences/{audienceId}/members")
  public ResponseEntity<ApiResponse<GoogleDataManagerUploadResponse>> uploadCustomerMatchMembers(
      @PathVariable Long companyId,
      @PathVariable Long audienceId,
      @Valid @RequestBody GoogleCustomerMatchUploadRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            dataManagerService.uploadAudienceMembers(
                companyId, audienceId, request, userId),
            request.validateOnly()
                ? "Google Customer Match members validated"
                : "Google Customer Match members submitted"));
  }

  @PostMapping("/accounts/{accountId}/audiences")
  public ResponseEntity<ApiResponse<AdAudienceResponse>> createAudience(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody CreateGoogleAudienceRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                audienceMutationService.create(companyId, accountId, request, userId),
                "Google Ads Customer Match audience created"));
  }

  @PostMapping("/accounts/{accountId}/conversions/sync")
  public ResponseEntity<ApiResponse<GoogleConversionSyncResponse>> syncConversions(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            conversionSyncService.sync(companyId, accountId),
            "Google Ads conversion actions synchronized"));
  }

  @PostMapping("/conversions/{conversionId}/events")
  public ResponseEntity<ApiResponse<GoogleDataManagerUploadResponse>> uploadConversionEvents(
      @PathVariable Long companyId,
      @PathVariable Long conversionId,
      @Valid @RequestBody GoogleConversionEventUploadRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            dataManagerService.uploadConversionEvents(
                companyId, conversionId, request, userId),
            request.validateOnly()
                ? "Google conversion events validated"
                : "Google conversion events submitted"));
  }

  @PostMapping("/accounts/{accountId}/conversions")
  public ResponseEntity<ApiResponse<AdConversionResponse>> createConversion(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody CreateGoogleConversionRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                conversionMutationService.create(companyId, accountId, request, userId),
                "Google Ads conversion action created"));
  }

  @PostMapping("/accounts/{accountId}/insights/sync")
  public ResponseEntity<ApiResponse<GoogleInsightsSyncResponse>> syncInsights(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            insightSyncService.sync(companyId, accountId, from, to),
            "Google Ads analytics synchronized"));
  }

  @PostMapping("/accounts/{accountId}/insights/breakdowns/sync")
  public ResponseEntity<ApiResponse<GoogleInsightBreakdownsSyncResponse>> syncInsightBreakdowns(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            insightBreakdownSyncService.sync(companyId, accountId, from, to),
            "Google Ads analytics breakdowns synchronized"));
  }

  @GetMapping("/accounts/{accountId}/native-recommendations")
  public ResponseEntity<ApiResponse<java.util.List<GoogleNativeRecommendation>>> nativeRecommendations(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            nativeRecommendationService.list(companyId, accountId),
            "Google Ads native recommendations fetched"));
  }

  @PostMapping("/accounts/{accountId}/native-recommendations/apply")
  public ResponseEntity<ApiResponse<GoogleRecommendationActionResponse>> applyNativeRecommendations(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody GoogleRecommendationActionRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            nativeRecommendationService.apply(
                companyId, accountId, request.resourceNames(), userId),
            "Google Ads recommendations applied"));
  }

  @PostMapping("/accounts/{accountId}/native-recommendations/dismiss")
  public ResponseEntity<ApiResponse<GoogleRecommendationActionResponse>> dismissNativeRecommendations(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody GoogleRecommendationActionRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            nativeRecommendationService.dismiss(
                companyId, accountId, request.resourceNames(), userId),
            "Google Ads recommendations dismissed"));
  }

  @PostMapping("/accounts/{accountId}/health/sync")
  public ResponseEntity<ApiResponse<AdAccountHealthResponse>> syncHealth(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            healthService.sync(companyId, accountId),
            "Google Ads account health synchronized"));
  }

  @PostMapping("/accounts/{accountId}/billing/sync")
  public ResponseEntity<ApiResponse<AdAccountBillingResponse>> syncBilling(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            billingService.sync(companyId, accountId),
            "Google Ads billing synchronized"));
  }

  private Long requireCreate(Long companyId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_CREATE);
    return userId;
  }

  private void requireRead(Long companyId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_READ);
  }

  private Long requireModify(Long companyId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return userId;
  }
}
