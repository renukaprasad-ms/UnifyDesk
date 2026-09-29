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
import com.messaging.ads.google.dto.CreateGoogleAdvancedCampaignRequest;
import com.messaging.ads.google.dto.CreateGoogleAudienceRequest;
import com.messaging.ads.google.dto.CreateGoogleCampaignMixExperimentRequest;
import com.messaging.ads.google.dto.CreateGoogleCampaignRequest;
import com.messaging.ads.google.dto.CreateGoogleExperimentRequest;
import com.messaging.ads.google.dto.CreateGoogleConversionRequest;
import com.messaging.ads.google.dto.CreateGoogleKeywordRequest;
import com.messaging.ads.google.dto.CreateGooglePerformanceMaxCampaignRequest;
import com.messaging.ads.google.dto.CreateGoogleResponsiveSearchAdRequest;
import com.messaging.ads.google.dto.CreateGoogleResponsiveDisplayAdRequest;
import com.messaging.ads.google.dto.CreateGoogleDemandGenVideoAdRequest;
import com.messaging.ads.google.dto.CreateGoogleDemandGenMultiAssetAdRequest;
import com.messaging.ads.google.dto.CreateGoogleDemandGenCarouselAdRequest;
import com.messaging.ads.google.dto.CreateGoogleShoppingProductAdRequest;
import com.messaging.ads.google.dto.GoogleAdGroupStatusRequest;
import com.messaging.ads.google.dto.GoogleAdGroupSyncResponse;
import com.messaging.ads.google.dto.GoogleAdStatusRequest;
import com.messaging.ads.google.dto.GoogleAdSyncResponse;
import com.messaging.ads.google.dto.GoogleAdsAuthorizationResponse;
import com.messaging.ads.google.dto.GoogleAudienceSyncResponse;
import com.messaging.ads.google.dto.GoogleCampaignStatusRequest;
import com.messaging.ads.google.dto.GoogleCampaignSyncResponse;
import com.messaging.ads.google.dto.GoogleCampaignSimulationResponse;
import com.messaging.ads.google.dto.GoogleConversionSyncResponse;
import com.messaging.ads.google.dto.GoogleInsightsSyncResponse;
import com.messaging.ads.google.dto.GoogleInsightBreakdownsSyncResponse;
import com.messaging.ads.google.dto.GoogleKeywordSyncResponse;
import com.messaging.ads.google.dto.GoogleFullSyncResponse;
import com.messaging.ads.google.dto.GoogleExperimentActionResponse;
import com.messaging.ads.google.dto.GoogleExperimentResponse;
import com.messaging.ads.google.dto.GoogleAssetGenerationImageRequest;
import com.messaging.ads.google.dto.GoogleAssetGenerationImageResponse;
import com.messaging.ads.google.dto.GoogleAssetGenerationTextRequest;
import com.messaging.ads.google.dto.GoogleAssetGenerationTextResponse;
import com.messaging.ads.google.dto.GoogleAssetGroupResponse;
import com.messaging.ads.google.dto.GoogleAssetGroupPerformanceResponse;
import com.messaging.ads.google.dto.CreateGoogleAppCampaignRequest;
import com.messaging.ads.google.dto.CreateGoogleAppAdGroupRequest;
import com.messaging.ads.google.dto.CreateGoogleAppAdRequest;
import com.messaging.ads.google.dto.GoogleAppCampaignSettingsResponse;
import com.messaging.ads.google.dto.GoogleAppCampaignPerformanceResponse;
import com.messaging.ads.google.dto.GoogleAppCreativeAsset;
import com.messaging.ads.google.dto.GoogleProviderReportResponse;
import com.messaging.ads.google.dto.GoogleDemandGenAudienceOption;
import com.messaging.ads.google.dto.GoogleDemandGenChannelPerformance;
import com.messaging.ads.google.dto.GoogleDemandGenTargetingRequest;
import com.messaging.ads.google.dto.GoogleDemandGenTargetingResponse;
import com.messaging.ads.google.dto.GoogleAssetGroupUrlOptionsResponse;
import com.messaging.ads.google.dto.GooglePerformanceMaxAutomationRequest;
import com.messaging.ads.google.dto.GooglePerformanceMaxAutomationResponse;
import com.messaging.ads.google.dto.GooglePerformanceMaxBrandGuidelinesRequest;
import com.messaging.ads.google.dto.GooglePerformanceMaxBrandGuidelinesResponse;
import com.messaging.ads.google.dto.GoogleLifecycleGoalResponse;
import com.messaging.ads.google.dto.GoogleLifecycleGoalUpsertRequest;
import com.messaging.ads.google.dto.GooglePerformanceMaxLifecycleRequest;
import com.messaging.ads.google.dto.GooglePerformanceMaxLifecycleResponse;
import com.messaging.ads.google.dto.GoogleUserListCustomerTypeRequest;
import com.messaging.ads.google.dto.GoogleUserListCustomerTypeResponse;
import com.messaging.ads.google.dto.GoogleAssetGroupAssetResponse;
import com.messaging.ads.google.dto.GoogleAssetGroupAssetMutationRequest;
import com.messaging.ads.google.dto.GoogleAssetGroupAssetRemoveRequest;
import com.messaging.ads.google.dto.GoogleAssetGroupStatusRequest;
import com.messaging.ads.google.dto.GoogleAssetGroupSignalResponse;
import com.messaging.ads.google.dto.GoogleAssetGroupSignalsRequest;
import com.messaging.ads.google.dto.GoogleCustomerMatchUploadRequest;
import com.messaging.ads.google.dto.GoogleConversionEventUploadRequest;
import com.messaging.ads.google.dto.GoogleDataManagerUploadResponse;
import com.messaging.ads.google.dto.GoogleNativeRecommendation;
import com.messaging.ads.google.dto.GoogleNegativeKeywordBatchRequest;
import com.messaging.ads.google.dto.GoogleNegativeKeywordListResponse;
import com.messaging.ads.google.dto.GoogleNegativeKeywordMutationResponse;
import com.messaging.ads.google.dto.GoogleNegativeKeywordResponse;
import com.messaging.ads.google.dto.CreateGoogleNegativeKeywordListRequest;
import com.messaging.ads.google.dto.GoogleMerchantAccountResponse;
import com.messaging.ads.google.dto.GoogleMerchantDataSourceResponse;
import com.messaging.ads.google.dto.CreateGoogleMerchantDataSourceRequest;
import com.messaging.ads.google.dto.GoogleMerchantProductPage;
import com.messaging.ads.google.dto.GoogleMerchantProductResponse;
import com.messaging.ads.google.dto.UpsertGoogleMerchantProductRequest;
import com.messaging.ads.google.dto.DeleteGoogleMerchantProductRequest;
import com.messaging.ads.google.dto.GoogleRecommendationActionRequest;
import com.messaging.ads.google.dto.GoogleRecommendationActionResponse;
import com.messaging.ads.google.dto.GoogleSearchTermReportResponse;
import com.messaging.ads.google.dto.GoogleSearchAudienceTargetingRequest;
import com.messaging.ads.google.dto.GoogleDetailedDemographicCatalogResponse;
import com.messaging.ads.google.dto.GoogleSearchDemographicTargetingRequest;
import com.messaging.ads.google.dto.GoogleSearchInterestCatalogResponse;
import com.messaging.ads.google.dto.GoogleSearchInterestTargetingRequest;
import com.messaging.ads.google.dto.GoogleAiMaxCombinationReportResponse;
import com.messaging.ads.google.dto.GoogleBrandSuggestionResponse;
import com.messaging.ads.google.dto.GoogleKeywordForecastRequest;
import com.messaging.ads.google.dto.GoogleKeywordForecastResponse;
import com.messaging.ads.google.dto.GoogleKeywordPlannerIdeasRequest;
import com.messaging.ads.google.dto.GoogleKeywordPlannerHistoricalRequest;
import com.messaging.ads.google.dto.GoogleKeywordPlannerResponse;
import com.messaging.ads.google.dto.GoogleAssetResponse;
import com.messaging.ads.google.dto.GoogleAssetLinkRequest;
import com.messaging.ads.google.dto.GoogleAdvancedReusableAssetCreateRequest;
import com.messaging.ads.google.dto.GoogleReusableAssetCreateRequest;
import com.messaging.ads.google.dto.GoogleReusableAssetLinkResponse;
import com.messaging.ads.google.dto.GoogleReusableAssetResponse;
import com.messaging.ads.google.dto.GoogleUrlOptionsRequest;
import com.messaging.ads.google.dto.GoogleUrlOptionsResponse;
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
import java.util.List;
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
  private final GoogleAdsAdvancedCampaignService advancedCampaignService;
  private final GoogleAdsAppCampaignService appCampaignService;
  private final GoogleAdsAppAdService appAdService;
  private final GoogleAdsProviderReportingService providerReportingService;
  private final GoogleAdsSimulationService simulationService;
  private final GoogleAdsExperimentService experimentService;
  private final GoogleAdsAdGroupSyncService adGroupSyncService;
  private final GoogleAdsAdGroupMutationService adGroupMutationService;
  private final GoogleAdsSearchAudienceService searchAudienceService;
  private final GoogleAdsSearchInterestService searchInterestService;
  private final GoogleAdsCampaignTargetingService campaignTargetingService;
  private final GoogleAdsPerformanceMaxLifecycleService performanceMaxLifecycleService;
  private final GoogleAdsKeywordSyncService keywordSyncService;
  private final GoogleAdsKeywordMutationService keywordMutationService;
  private final GoogleAdsNegativeKeywordService negativeKeywordService;
  private final GoogleAdsAdSyncService adSyncService;
  private final GoogleAdsAdMutationService adMutationService;
  private final GoogleAdsVisualAdService visualAdService;
  private final GoogleAdsDemandGenCreativeService demandGenCreativeService;
  private final GoogleAdsDemandGenTargetingService demandGenTargetingService;
  private final GoogleAdsAudienceSyncService audienceSyncService;
  private final GoogleAdsAudienceMutationService audienceMutationService;
  private final GoogleAdsConversionSyncService conversionSyncService;
  private final GoogleAdsConversionMutationService conversionMutationService;
  private final GoogleAdsInsightSyncService insightSyncService;
  private final GoogleAdsSearchTermService searchTermService;
  private final GoogleAdsAiMaxReportingService aiMaxReportingService;
  private final GoogleAdsAiMaxBrandService aiMaxBrandService;
  private final GoogleAdsKeywordPlannerService keywordPlannerService;
  private final GoogleAdsInsightBreakdownSyncService insightBreakdownSyncService;
  private final GoogleAdsHealthService healthService;
  private final GoogleAdsBillingService billingService;
  private final GoogleAdsFullSyncService fullSyncService;
  private final GoogleAdsPerformanceMaxService performanceMaxService;
  private final GoogleAdsReusableAssetService reusableAssetService;
  private final GoogleAdsAssetGenerationService assetGenerationService;
  private final GoogleAdsAssetGroupSignalService assetGroupSignalService;
  private final GoogleAdsAssetGroupAssetService assetGroupAssetService;
  private final GoogleDataManagerService dataManagerService;
  private final GoogleAdsNativeRecommendationService nativeRecommendationService;
  private final GoogleMerchantService merchantService;
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

  @PostMapping("/accounts/{accountId}/asset-generation:text")
  public ResponseEntity<ApiResponse<GoogleAssetGenerationTextResponse>> generateTextAssets(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody GoogleAssetGenerationTextRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            assetGenerationService.generateText(companyId, accountId, request, userId),
            "Google AI text asset suggestions generated"));
  }

  @PostMapping("/accounts/{accountId}/asset-generation:images")
  public ResponseEntity<ApiResponse<GoogleAssetGenerationImageResponse>> generateImageAssets(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody GoogleAssetGenerationImageRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            assetGenerationService.generateImages(companyId, accountId, request, userId),
            "Google AI image asset suggestions generated"));
  }

  @GetMapping("/accounts/{accountId}/reusable-assets")
  public ResponseEntity<ApiResponse<List<GoogleReusableAssetResponse>>> reusableAssets(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            reusableAssetService.assets(companyId, accountId),
            "Google reusable assets fetched"));
  }

  @PostMapping("/accounts/{accountId}/reusable-assets")
  public ResponseEntity<ApiResponse<GoogleReusableAssetResponse>> createReusableAsset(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody GoogleReusableAssetCreateRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                reusableAssetService.create(companyId, accountId, request, userId),
                "Google reusable asset created"));
  }

  @PostMapping("/accounts/{accountId}/reusable-assets/advanced")
  public ResponseEntity<ApiResponse<GoogleReusableAssetResponse>> createAdvancedReusableAsset(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody GoogleAdvancedReusableAssetCreateRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                reusableAssetService.createAdvanced(companyId, accountId, request, userId),
                "Google reusable asset created"));
  }

  @GetMapping("/accounts/{accountId}/reusable-assets/links")
  public ResponseEntity<ApiResponse<List<GoogleReusableAssetLinkResponse>>> reusableAssetLinks(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @RequestParam GoogleAssetLinkRequest.Level level,
      @RequestParam(required = false) Long targetId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            reusableAssetService.links(companyId, accountId, level, targetId),
            "Google reusable asset links fetched"));
  }

  @PostMapping("/accounts/{accountId}/reusable-assets/{assetId}/links")
  public ResponseEntity<ApiResponse<GoogleReusableAssetLinkResponse>> attachReusableAsset(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String assetId,
      @Valid @RequestBody GoogleAssetLinkRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            reusableAssetService.attach(companyId, accountId, assetId, request, userId),
            "Google reusable asset attached"));
  }

  @PostMapping("/accounts/{accountId}/reusable-assets/{assetId}/links:detach")
  public ResponseEntity<ApiResponse<Boolean>> detachReusableAsset(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String assetId,
      @Valid @RequestBody GoogleAssetLinkRequest request) {
    Long userId = requireModify(companyId);
    reusableAssetService.detach(companyId, accountId, assetId, request, userId);
    return ResponseEntity.ok(
        ApiResponse.success(200, true, "Google reusable asset detached"));
  }

  @GetMapping("/accounts/{accountId}/url-options")
  public ResponseEntity<ApiResponse<GoogleUrlOptionsResponse>> accountUrlOptions(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            reusableAssetService.urlOptions(
                companyId, accountId, GoogleAssetLinkRequest.Level.CUSTOMER, null),
            "Google account URL options fetched"));
  }

  @PutMapping("/accounts/{accountId}/url-options")
  public ResponseEntity<ApiResponse<GoogleUrlOptionsResponse>> updateAccountUrlOptions(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody GoogleUrlOptionsRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            reusableAssetService.updateUrlOptions(
                companyId,
                accountId,
                GoogleAssetLinkRequest.Level.CUSTOMER,
                null,
                request,
                userId),
            "Google account URL options updated"));
  }

  @GetMapping("/accounts/{accountId}/campaigns/{campaignId}/url-options")
  public ResponseEntity<ApiResponse<GoogleUrlOptionsResponse>> campaignUrlOptions(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable Long campaignId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            reusableAssetService.urlOptions(
                companyId, accountId, GoogleAssetLinkRequest.Level.CAMPAIGN, campaignId),
            "Google campaign URL options fetched"));
  }

  @PutMapping("/accounts/{accountId}/campaigns/{campaignId}/url-options")
  public ResponseEntity<ApiResponse<GoogleUrlOptionsResponse>> updateCampaignUrlOptions(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable Long campaignId,
      @Valid @RequestBody GoogleUrlOptionsRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            reusableAssetService.updateUrlOptions(
                companyId,
                accountId,
                GoogleAssetLinkRequest.Level.CAMPAIGN,
                campaignId,
                request,
                userId),
            "Google campaign URL options updated"));
  }

  @GetMapping("/accounts/{accountId}/ad-groups/{adGroupId}/url-options")
  public ResponseEntity<ApiResponse<GoogleUrlOptionsResponse>> adGroupUrlOptions(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable Long adGroupId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            reusableAssetService.urlOptions(
                companyId, accountId, GoogleAssetLinkRequest.Level.AD_GROUP, adGroupId),
            "Google ad-group URL options fetched"));
  }

  @PutMapping("/accounts/{accountId}/ad-groups/{adGroupId}/url-options")
  public ResponseEntity<ApiResponse<GoogleUrlOptionsResponse>> updateAdGroupUrlOptions(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable Long adGroupId,
      @Valid @RequestBody GoogleUrlOptionsRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            reusableAssetService.updateUrlOptions(
                companyId,
                accountId,
                GoogleAssetLinkRequest.Level.AD_GROUP,
                adGroupId,
                request,
                userId),
            "Google ad-group URL options updated"));
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

  @GetMapping("/campaigns/{campaignId}/asset-groups/performance")
  public ResponseEntity<ApiResponse<java.util.List<GoogleAssetGroupPerformanceResponse>>>
      assetGroupPerformance(
          @PathVariable Long companyId,
          @PathVariable Long campaignId,
          @RequestParam(defaultValue = "30") Integer days) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxService.assetGroupPerformance(companyId, campaignId, days),
            "Google Performance Max asset group performance fetched"));
  }

  @GetMapping("/campaigns/{campaignId}/asset-groups/{assetGroupId}/assets")
  public ResponseEntity<ApiResponse<java.util.List<GoogleAssetGroupAssetResponse>>> assetGroupAssets(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable String assetGroupId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            assetGroupAssetService.list(companyId, campaignId, assetGroupId),
            "Google Performance Max Asset Group assets fetched"));
  }

  @PostMapping("/campaigns/{campaignId}/asset-groups/{assetGroupId}/assets")
  public ResponseEntity<ApiResponse<java.util.List<GoogleAssetGroupAssetResponse>>> addAssetGroupAsset(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable String assetGroupId,
      @Valid @RequestBody GoogleAssetGroupAssetMutationRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            assetGroupAssetService.add(
                companyId, campaignId, assetGroupId, request, userId),
            "Google Performance Max Asset Group asset added"));
  }

  @PostMapping("/campaigns/{campaignId}/asset-groups/{assetGroupId}/assets:remove")
  public ResponseEntity<ApiResponse<java.util.List<GoogleAssetGroupAssetResponse>>> removeAssetGroupAsset(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable String assetGroupId,
      @Valid @RequestBody GoogleAssetGroupAssetRemoveRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            assetGroupAssetService.remove(
                companyId, campaignId, assetGroupId, request, userId),
            "Google Performance Max Asset Group asset removed"));
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


  @GetMapping("/accounts/{accountId}/lifecycle-goals")
  public ResponseEntity<ApiResponse<List<GoogleLifecycleGoalResponse>>> lifecycleGoals(
      @PathVariable Long companyId,
      @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxLifecycleService.goals(companyId, accountId),
            "Google lifecycle goals fetched"));
  }

  @PutMapping("/accounts/{accountId}/lifecycle-goals/{goalType}")
  public ResponseEntity<ApiResponse<GoogleLifecycleGoalResponse>> upsertLifecycleGoal(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String goalType,
      @Valid @RequestBody GoogleLifecycleGoalUpsertRequest request) {
    Long userId = requireModify(companyId);
    GoogleLifecycleGoalUpsertRequest normalized =
        new GoogleLifecycleGoalUpsertRequest(
            goalType,
            request.additionalValue(),
            request.additionalHighLifetimeValue(),
            request.valueMultiplier());
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxLifecycleService.upsertGoal(
                companyId, accountId, normalized, userId),
            "Google lifecycle goal saved"));
  }

  @GetMapping("/accounts/{accountId}/user-list-customer-types")
  public ResponseEntity<ApiResponse<List<GoogleUserListCustomerTypeResponse>>>
      userListCustomerTypes(
          @PathVariable Long companyId,
          @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxLifecycleService.customerTypes(companyId, accountId),
            "Google user-list customer types fetched"));
  }

  @PutMapping("/accounts/{accountId}/user-list-customer-types")
  public ResponseEntity<ApiResponse<List<GoogleUserListCustomerTypeResponse>>>
      replaceUserListCustomerTypes(
          @PathVariable Long companyId,
          @PathVariable Long accountId,
          @Valid @RequestBody GoogleUserListCustomerTypeRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxLifecycleService.replaceCustomerTypes(
                companyId, accountId, request, userId),
            "Google user-list customer types updated"));
  }

  @GetMapping("/campaigns/{campaignId}/performance-max/lifecycle-goals")
  public ResponseEntity<ApiResponse<List<GooglePerformanceMaxLifecycleResponse>>>
      performanceMaxLifecycleGoals(
          @PathVariable Long companyId,
          @PathVariable Long campaignId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxLifecycleService.campaignLifecycle(companyId, campaignId),
            "Performance Max lifecycle goals fetched"));
  }

  @PutMapping("/campaigns/{campaignId}/performance-max/lifecycle-goals/{goalType}")
  public ResponseEntity<ApiResponse<GooglePerformanceMaxLifecycleResponse>>
      updatePerformanceMaxLifecycleGoal(
          @PathVariable Long companyId,
          @PathVariable Long campaignId,
          @PathVariable String goalType,
          @Valid @RequestBody GooglePerformanceMaxLifecycleRequest request) {
    Long userId = requireModify(companyId);
    GooglePerformanceMaxLifecycleRequest normalized =
        new GooglePerformanceMaxLifecycleRequest(
            goalType,
            request.targetOption(),
            request.additionalValue(),
            request.additionalHighLifetimeValue(),
            request.valueMultiplier(),
            request.enableBidAdjustmentsForLoyaltyMembers(),
            request.showTargetedLoyaltyMemberBenefitsInPla());
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxLifecycleService.upsertCampaignLifecycle(
                companyId, campaignId, normalized, userId),
            "Performance Max lifecycle goal updated"));
  }

  @DeleteMapping("/campaigns/{campaignId}/performance-max/lifecycle-goals/{goalType}")
  public ResponseEntity<ApiResponse<List<GooglePerformanceMaxLifecycleResponse>>>
      removePerformanceMaxLifecycleGoal(
          @PathVariable Long companyId,
          @PathVariable Long campaignId,
          @PathVariable String goalType) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxLifecycleService.removeCampaignLifecycle(
                companyId, campaignId, goalType, userId),
            "Performance Max lifecycle goal removed"));
  }

  @GetMapping("/campaigns/{campaignId}/performance-max/brand-guidelines")
  public ResponseEntity<ApiResponse<GooglePerformanceMaxBrandGuidelinesResponse>>
      performanceMaxBrandGuidelines(
          @PathVariable Long companyId,
          @PathVariable Long campaignId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxService.brandGuidelines(companyId, campaignId),
            "Google Performance Max brand guidelines fetched"));
  }

  @PutMapping("/campaigns/{campaignId}/performance-max/brand-guidelines")
  public ResponseEntity<ApiResponse<GooglePerformanceMaxBrandGuidelinesResponse>>
      updatePerformanceMaxBrandGuidelines(
          @PathVariable Long companyId,
          @PathVariable Long campaignId,
          @Valid @RequestBody GooglePerformanceMaxBrandGuidelinesRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxService.updateBrandGuidelines(
                companyId, campaignId, request, userId),
            "Google Performance Max brand guidelines updated"));
  }

  @GetMapping("/campaigns/{campaignId}/performance-max/automation")
  public ResponseEntity<ApiResponse<GooglePerformanceMaxAutomationResponse>> performanceMaxAutomation(
      @PathVariable Long companyId, @PathVariable Long campaignId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxService.automation(companyId, campaignId),
            "Google Performance Max automation settings fetched"));
  }

  @PutMapping("/campaigns/{campaignId}/performance-max/automation")
  public ResponseEntity<ApiResponse<GooglePerformanceMaxAutomationResponse>> updatePerformanceMaxAutomation(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody GooglePerformanceMaxAutomationRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxService.updateAutomation(companyId, campaignId, request, userId),
            "Google Performance Max automation settings updated"));
  }

  @GetMapping("/campaigns/{campaignId}/asset-groups/{assetGroupId}/url-options")
  public ResponseEntity<ApiResponse<GoogleAssetGroupUrlOptionsResponse>> assetGroupUrlOptions(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable String assetGroupId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxService.assetGroupUrlOptions(companyId, campaignId, assetGroupId),
            "Google Performance Max Asset Group URL options fetched"));
  }

  @PutMapping("/campaigns/{campaignId}/asset-groups/{assetGroupId}/url-options")
  public ResponseEntity<ApiResponse<GoogleAssetGroupUrlOptionsResponse>> updateAssetGroupUrlOptions(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable String assetGroupId,
      @Valid @RequestBody GoogleUrlOptionsRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            performanceMaxService.updateAssetGroupUrlOptions(
                companyId, campaignId, assetGroupId, request, userId),
            "Google Performance Max Asset Group URL options updated"));
  }

  @PostMapping("/accounts/{accountId}/advanced-campaigns")
  public ResponseEntity<ApiResponse<AdCampaignResponse>> createAdvancedCampaign(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody CreateGoogleAdvancedCampaignRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                advancedCampaignService.create(
                    companyId, accountId, request, userId),
                "Google advanced campaign created in paused state"));
  }

  @PostMapping("/accounts/{accountId}/app-campaigns")
  public ResponseEntity<ApiResponse<AdCampaignResponse>> createAppCampaign(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody CreateGoogleAppCampaignRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                appCampaignService.create(companyId, accountId, request, userId),
                "Google App campaign created in paused state"));
  }

  @GetMapping("/campaigns/{campaignId}/app-settings")
  public ResponseEntity<ApiResponse<GoogleAppCampaignSettingsResponse>> appCampaignSettings(
      @PathVariable Long companyId, @PathVariable Long campaignId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            appCampaignService.settings(companyId, campaignId),
            "Google App campaign settings fetched"));
  }

  @GetMapping("/campaigns/{campaignId}/app-performance")
  public ResponseEntity<ApiResponse<GoogleAppCampaignPerformanceResponse>> appCampaignPerformance(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            appCampaignService.performance(companyId, campaignId, from, to),
            "Google App campaign performance fetched"));
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

  @GetMapping("/accounts/{accountId}/app-creative-assets")
  public ResponseEntity<ApiResponse<List<GoogleAppCreativeAsset>>> appCreativeAssets(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            appAdService.creativeAssets(companyId, accountId),
            "Google App creative assets fetched"));
  }

  @PostMapping("/campaigns/{campaignId}/app-ad-groups")
  public ResponseEntity<ApiResponse<AdSetResponse>> createAppAdGroup(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody CreateGoogleAppAdGroupRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                appAdService.createAdGroup(companyId, campaignId, request, userId),
                "Google App ad group created in paused state"));
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

  @GetMapping("/accounts/{accountId}/detailed-demographics")
  public ResponseEntity<ApiResponse<GoogleDetailedDemographicCatalogResponse>>
      detailedDemographics(
          @PathVariable Long companyId,
          @PathVariable Long accountId,
          @RequestParam(required = false) String q,
          @RequestParam(defaultValue = "40") int limit) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            campaignTargetingService.detailedDemographics(
                companyId, accountId, q, limit),
            "Google detailed demographics fetched"));
  }

  @GetMapping("/campaigns/{campaignId}/search-demographics")
  public ResponseEntity<ApiResponse<GoogleAdsCampaignTargetingService.SearchDemographicSnapshot>>
      searchDemographics(
          @PathVariable Long companyId,
          @PathVariable Long campaignId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            campaignTargetingService.searchDemographics(companyId, campaignId),
            "Google Search demographics fetched"));
  }

  @PutMapping("/campaigns/{campaignId}/search-demographics")
  public ResponseEntity<ApiResponse<GoogleAdsCampaignTargetingService.SearchDemographicSnapshot>>
      updateSearchDemographics(
          @PathVariable Long companyId,
          @PathVariable Long campaignId,
          @Valid @RequestBody GoogleSearchDemographicTargetingRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            campaignTargetingService.replaceSearchDemographics(
                companyId, campaignId, request, userId),
            "Google Search demographics updated"));
  }

  @GetMapping("/accounts/{accountId}/search-interest-catalog")
  public ResponseEntity<ApiResponse<GoogleSearchInterestCatalogResponse>>
      searchInterestCatalog(
          @PathVariable Long companyId,
          @PathVariable Long accountId,
          @RequestParam(required = false) String q,
          @RequestParam(defaultValue = "40") Integer limit) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            searchInterestService.catalog(companyId, accountId, q, limit),
            "Google Search interest segments fetched"));
  }

  @GetMapping("/ad-groups/{adGroupId}/search-interest-targeting")
  public ResponseEntity<ApiResponse<GoogleAdsSearchInterestService.TargetingSnapshot>>
      searchInterestTargeting(
          @PathVariable Long companyId,
          @PathVariable Long adGroupId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            searchInterestService.targeting(companyId, adGroupId),
            "Google Search interest targeting fetched"));
  }

  @PutMapping("/ad-groups/{adGroupId}/search-interest-targeting")
  public ResponseEntity<ApiResponse<GoogleAdsSearchInterestService.TargetingSnapshot>>
      updateSearchInterestTargeting(
          @PathVariable Long companyId,
          @PathVariable Long adGroupId,
          @Valid @RequestBody GoogleSearchInterestTargetingRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            searchInterestService.replace(
                companyId,
                adGroupId,
                request.includedResourceNames(),
                request.excludedResourceNames(),
                userId),
            "Google Search interest targeting updated"));
  }

  @GetMapping("/ad-groups/{adGroupId}/search-audience-targeting")
  public ResponseEntity<ApiResponse<GoogleAdsSearchAudienceService.TargetingSnapshot>>
      searchAudienceTargeting(
          @PathVariable Long companyId,
          @PathVariable Long adGroupId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            searchAudienceService.targeting(companyId, adGroupId),
            "Google Search audience targeting fetched"));
  }

  @PutMapping("/ad-groups/{adGroupId}/search-audience-targeting")
  public ResponseEntity<ApiResponse<GoogleAdsSearchAudienceService.TargetingSnapshot>>
      updateSearchAudienceTargeting(
          @PathVariable Long companyId,
          @PathVariable Long adGroupId,
          @Valid @RequestBody GoogleSearchAudienceTargetingRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            searchAudienceService.replace(
                companyId,
                adGroupId,
                request.includedAudienceIds(),
                request.excludedAudienceIds(),
                request.mode(),
                userId),
            "Google Search audience targeting updated"));
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

  @GetMapping("/campaigns/{campaignId}/negative-keywords")
  public ResponseEntity<ApiResponse<java.util.List<GoogleNegativeKeywordResponse>>> campaignNegativeKeywords(
      @PathVariable Long companyId, @PathVariable Long campaignId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            negativeKeywordService.campaignNegatives(companyId, campaignId),
            "Google campaign negative keywords fetched"));
  }

  @PostMapping("/campaigns/{campaignId}/negative-keywords")
  public ResponseEntity<ApiResponse<GoogleNegativeKeywordMutationResponse>> addCampaignNegativeKeywords(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody GoogleNegativeKeywordBatchRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            negativeKeywordService.addCampaignNegatives(
                companyId, campaignId, request, userId),
            "Google campaign negative keywords updated"));
  }

  @DeleteMapping("/campaigns/{campaignId}/negative-keywords/{criterionId}")
  public ResponseEntity<ApiResponse<Boolean>> removeCampaignNegativeKeyword(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable String criterionId) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            negativeKeywordService.removeCampaignNegative(
                companyId, campaignId, criterionId, userId),
            "Google campaign negative keyword removed"));
  }

  @GetMapping("/accounts/{accountId}/negative-keyword-lists")
  public ResponseEntity<ApiResponse<java.util.List<GoogleNegativeKeywordListResponse>>> negativeKeywordLists(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @RequestParam(required = false) Long campaignId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            negativeKeywordService.sharedLists(companyId, accountId, campaignId),
            "Google negative keyword lists fetched"));
  }

  @PostMapping("/accounts/{accountId}/negative-keyword-lists")
  public ResponseEntity<ApiResponse<GoogleNegativeKeywordListResponse>> createNegativeKeywordList(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody CreateGoogleNegativeKeywordListRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                negativeKeywordService.createSharedList(
                    companyId, accountId, request, userId),
                "Google negative keyword list created"));
  }

  @GetMapping("/accounts/{accountId}/negative-keyword-lists/{sharedSetId}")
  public ResponseEntity<ApiResponse<GoogleNegativeKeywordListResponse>> negativeKeywordList(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String sharedSetId,
      @RequestParam(required = false) Long campaignId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            negativeKeywordService.sharedList(
                companyId, accountId, sharedSetId, campaignId),
            "Google negative keyword list fetched"));
  }

  @GetMapping("/accounts/{accountId}/negative-keyword-lists/{sharedSetId}/members")
  public ResponseEntity<ApiResponse<java.util.List<GoogleNegativeKeywordResponse>>> negativeKeywordListMembers(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String sharedSetId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            negativeKeywordService.sharedMembers(
                companyId, accountId, sharedSetId),
            "Google negative keyword list members fetched"));
  }

  @PostMapping("/accounts/{accountId}/negative-keyword-lists/{sharedSetId}/members")
  public ResponseEntity<ApiResponse<GoogleNegativeKeywordMutationResponse>> addNegativeKeywordListMembers(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String sharedSetId,
      @Valid @RequestBody GoogleNegativeKeywordBatchRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            negativeKeywordService.addSharedMembers(
                companyId, accountId, sharedSetId, request, userId),
            "Google negative keyword list members updated"));
  }

  @DeleteMapping("/accounts/{accountId}/negative-keyword-lists/{sharedSetId}/members/{criterionId}")
  public ResponseEntity<ApiResponse<Boolean>> removeNegativeKeywordListMember(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String sharedSetId,
      @PathVariable String criterionId) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            negativeKeywordService.removeSharedMember(
                companyId, accountId, sharedSetId, criterionId, userId),
            "Google negative keyword list member removed"));
  }

  @PostMapping("/campaigns/{campaignId}/negative-keyword-lists/{sharedSetId}")
  public ResponseEntity<ApiResponse<GoogleNegativeKeywordListResponse>> attachNegativeKeywordList(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable String sharedSetId) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            negativeKeywordService.attachSharedList(
                companyId, campaignId, sharedSetId, userId),
            "Google negative keyword list attached"));
  }

  @DeleteMapping("/campaigns/{campaignId}/negative-keyword-lists/{sharedSetId}")
  public ResponseEntity<ApiResponse<GoogleNegativeKeywordListResponse>> detachNegativeKeywordList(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable String sharedSetId) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            negativeKeywordService.detachSharedList(
                companyId, campaignId, sharedSetId, userId),
            "Google negative keyword list detached"));
  }

  @DeleteMapping("/keywords/{keywordId}")
  public ResponseEntity<ApiResponse<Boolean>> removeKeyword(
      @PathVariable Long companyId,
      @PathVariable Long keywordId) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            keywordMutationService.remove(companyId, keywordId, userId),
            "Google negative keyword removed"));
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

  @PostMapping("/ad-groups/{adGroupId}/app-ads")
  public ResponseEntity<ApiResponse<AdResponse>> createAppAd(
      @PathVariable Long companyId,
      @PathVariable Long adGroupId,
      @Valid @RequestBody CreateGoogleAppAdRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                appAdService.createAd(companyId, adGroupId, request, userId),
                "Google App ad created in paused state"));
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

  @PostMapping("/ad-groups/{adGroupId}/display-ads")
  public ResponseEntity<ApiResponse<AdResponse>> createResponsiveDisplayAd(
      @PathVariable Long companyId,
      @PathVariable Long adGroupId,
      @Valid @RequestBody CreateGoogleResponsiveDisplayAdRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                visualAdService.createResponsiveDisplay(
                    companyId, adGroupId, request, userId),
                "Google responsive display ad created in paused state"));
  }

  @PostMapping("/ad-groups/{adGroupId}/demand-gen-video-ads")
  public ResponseEntity<ApiResponse<AdResponse>> createDemandGenVideoAd(
      @PathVariable Long companyId,
      @PathVariable Long adGroupId,
      @Valid @RequestBody CreateGoogleDemandGenVideoAdRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                visualAdService.createDemandGenVideo(
                    companyId, adGroupId, request, userId),
                "Google Demand Gen video ad created in paused state"));
  }

  @GetMapping("/ad-groups/{adGroupId}/demand-gen-targeting")
  public ResponseEntity<ApiResponse<GoogleDemandGenTargetingResponse>> demandGenTargeting(
      @PathVariable Long companyId, @PathVariable Long adGroupId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            demandGenTargetingService.targeting(companyId, adGroupId),
            "Google Demand Gen targeting fetched"));
  }

  @PutMapping("/ad-groups/{adGroupId}/demand-gen-targeting")
  public ResponseEntity<ApiResponse<GoogleDemandGenTargetingResponse>> updateDemandGenTargeting(
      @PathVariable Long companyId,
      @PathVariable Long adGroupId,
      @Valid @RequestBody GoogleDemandGenTargetingRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            demandGenTargetingService.update(companyId, adGroupId, request, userId),
            "Google Demand Gen targeting updated"));
  }

  @GetMapping("/ad-groups/{adGroupId}/demand-gen-audiences")
  public ResponseEntity<ApiResponse<List<GoogleDemandGenAudienceOption>>> demandGenAudiences(
      @PathVariable Long companyId, @PathVariable Long adGroupId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            demandGenTargetingService.audiences(companyId, adGroupId),
            "Google Demand Gen audiences fetched"));
  }

  @GetMapping("/ad-groups/{adGroupId}/demand-gen-channel-performance")
  public ResponseEntity<ApiResponse<List<GoogleDemandGenChannelPerformance>>> demandGenChannelPerformance(
      @PathVariable Long companyId,
      @PathVariable Long adGroupId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            demandGenTargetingService.channelPerformance(companyId, adGroupId, from, to),
            "Google Demand Gen channel performance fetched"));
  }

  @PostMapping("/ad-groups/{adGroupId}/demand-gen-multi-asset-ads")
  public ResponseEntity<ApiResponse<AdResponse>> createDemandGenMultiAssetAd(
      @PathVariable Long companyId,
      @PathVariable Long adGroupId,
      @Valid @RequestBody CreateGoogleDemandGenMultiAssetAdRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                demandGenCreativeService.createMultiAsset(
                    companyId, adGroupId, request, userId),
                "Google Demand Gen Multi-Asset ad created in paused state"));
  }

  @PostMapping("/ad-groups/{adGroupId}/demand-gen-carousel-ads")
  public ResponseEntity<ApiResponse<AdResponse>> createDemandGenCarouselAd(
      @PathVariable Long companyId,
      @PathVariable Long adGroupId,
      @Valid @RequestBody CreateGoogleDemandGenCarouselAdRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                demandGenCreativeService.createCarousel(
                    companyId, adGroupId, request, userId),
                "Google Demand Gen Carousel ad created in paused state"));
  }

  @PostMapping("/ad-groups/{adGroupId}/shopping-product-ads")
  public ResponseEntity<ApiResponse<AdResponse>> createShoppingProductAd(
      @PathVariable Long companyId,
      @PathVariable Long adGroupId,
      @RequestBody CreateGoogleShoppingProductAdRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                visualAdService.createShoppingProductAd(
                    companyId, adGroupId, request, userId),
                "Google Shopping product ad created in paused state"));
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

  @PostMapping("/accounts/{accountId}/keyword-planner/ideas")
  public ResponseEntity<ApiResponse<GoogleKeywordPlannerResponse>> keywordIdeas(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody GoogleKeywordPlannerIdeasRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            keywordPlannerService.ideas(companyId, accountId, request, userId),
            "Google Keyword Planner ideas generated"));
  }

  @PostMapping("/accounts/{accountId}/keyword-planner/forecast")
  public ResponseEntity<ApiResponse<GoogleKeywordForecastResponse>> keywordForecast(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody GoogleKeywordForecastRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            keywordPlannerService.forecast(companyId, accountId, request, userId),
            "Google keyword forecast generated"));
  }

  @PostMapping("/accounts/{accountId}/keyword-planner/historical-metrics")
  public ResponseEntity<ApiResponse<GoogleKeywordPlannerResponse>> keywordHistoricalMetrics(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody GoogleKeywordPlannerHistoricalRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            keywordPlannerService.historical(companyId, accountId, request, userId),
            "Google Keyword Planner historical metrics generated"));
  }

  @GetMapping("/accounts/{accountId}/ai-max/brands/suggest")
  public ResponseEntity<ApiResponse<List<GoogleBrandSuggestionResponse>>> suggestAiMaxBrands(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @RequestParam String prefix,
      @RequestParam(required = false) List<String> selectedBrandIds) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            aiMaxBrandService.suggest(
                companyId,
                accountId,
                prefix,
                selectedBrandIds == null ? List.of() : selectedBrandIds),
            "Google brand suggestions fetched"));
  }

  @GetMapping("/campaigns/{campaignId}/ai-max/report")
  public ResponseEntity<ApiResponse<GoogleAiMaxCombinationReportResponse>> aiMaxReport(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            aiMaxReportingService.report(companyId, campaignId, from, to),
            "Google AI Max report fetched"));
  }

  @GetMapping("/campaigns/{campaignId}/search-terms")
  public ResponseEntity<ApiResponse<GoogleSearchTermReportResponse>> searchTerms(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            searchTermService.report(companyId, campaignId, from, to),
            "Google search-term report fetched"));
  }

  @GetMapping("/accounts/{accountId}/experiments")
  public ResponseEntity<ApiResponse<List<GoogleExperimentResponse>>> experiments(
      @PathVariable Long companyId,
      @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            experimentService.list(companyId, accountId),
            "Google Ads experiments fetched"));
  }

  @PostMapping("/accounts/{accountId}/experiments")
  public ResponseEntity<ApiResponse<GoogleExperimentResponse>> createExperiment(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody CreateGoogleExperimentRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                experimentService.createSearchCustom(
                    companyId, accountId, request, userId),
                "Google Search experiment created in setup state"));
  }

  @PostMapping("/accounts/{accountId}/experiments/campaign-mix")
  public ResponseEntity<ApiResponse<GoogleExperimentResponse>> createCampaignMixExperiment(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody CreateGoogleCampaignMixExperimentRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                experimentService.createCampaignMix(
                    companyId, accountId, request, userId),
                "Google campaign-mix experiment created in setup state"));
  }

  @PostMapping("/accounts/{accountId}/experiments/{experimentId}:schedule")
  public ResponseEntity<ApiResponse<GoogleExperimentActionResponse>> scheduleExperiment(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String experimentId) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            experimentService.schedule(companyId, accountId, experimentId, userId),
            "Google Ads experiment scheduling requested"));
  }

  @PostMapping("/accounts/{accountId}/experiments/{experimentId}:promote")
  public ResponseEntity<ApiResponse<GoogleExperimentActionResponse>> promoteExperiment(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String experimentId) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            experimentService.promote(companyId, accountId, experimentId, userId),
            "Google Ads experiment promotion requested"));
  }

  @PostMapping("/accounts/{accountId}/experiments/{experimentId}:end")
  public ResponseEntity<ApiResponse<GoogleExperimentActionResponse>> endExperiment(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String experimentId) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            experimentService.end(companyId, accountId, experimentId, userId),
            "Google Ads experiment end requested"));
  }

  @PostMapping("/accounts/{accountId}/experiments/{experimentId}:graduate")
  public ResponseEntity<ApiResponse<GoogleExperimentActionResponse>> graduateExperiment(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String experimentId) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            experimentService.graduate(companyId, accountId, experimentId, userId),
            "Google Ads experiment graduated"));
  }

  @GetMapping("/campaigns/{campaignId}/simulations")
  public ResponseEntity<ApiResponse<List<GoogleCampaignSimulationResponse>>> campaignSimulations(
      @PathVariable Long companyId,
      @PathVariable Long campaignId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            simulationService.campaignSimulations(companyId, campaignId),
            "Google campaign simulations fetched"));
  }

  @GetMapping("/accounts/{accountId}/provider-report")
  public ResponseEntity<ApiResponse<GoogleProviderReportResponse>> providerReport(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) String campaignExternalId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            providerReportingService.report(
                companyId, accountId, from, to, campaignExternalId),
            "Google provider reporting drill-down fetched"));
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

  @GetMapping("/accounts/{accountId}/merchant/accounts")
  public ResponseEntity<ApiResponse<java.util.List<GoogleMerchantAccountResponse>>> merchantAccounts(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            merchantService.accounts(companyId, accountId),
            "Accessible Google Merchant Center accounts fetched"));
  }

  @GetMapping("/accounts/{accountId}/merchant/accounts/{merchantId}/data-sources")
  public ResponseEntity<ApiResponse<java.util.List<GoogleMerchantDataSourceResponse>>> merchantDataSources(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String merchantId) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            merchantService.dataSources(companyId, accountId, merchantId),
            "Merchant Center data sources fetched"));
  }

  @PostMapping("/accounts/{accountId}/merchant/accounts/{merchantId}/data-sources")
  public ResponseEntity<ApiResponse<GoogleMerchantDataSourceResponse>> createMerchantDataSource(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String merchantId,
      @Valid @RequestBody CreateGoogleMerchantDataSourceRequest request) {
    Long userId = requireCreate(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                merchantService.createDataSource(
                    companyId, accountId, merchantId, request, userId),
                "Merchant Center API data source created"));
  }

  @DeleteMapping("/accounts/{accountId}/merchant/accounts/{merchantId}/data-sources/{dataSourceId}")
  public ResponseEntity<ApiResponse<Boolean>> deleteMerchantDataSource(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String merchantId,
      @PathVariable String dataSourceId) {
    Long userId = requireModify(companyId);
    merchantService.deleteDataSource(
        companyId, accountId, merchantId, dataSourceId, userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, true, "Merchant Center data source deleted"));
  }

  @GetMapping("/accounts/{accountId}/merchant/accounts/{merchantId}/products")
  public ResponseEntity<ApiResponse<GoogleMerchantProductPage>> merchantProducts(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String merchantId,
      @RequestParam(required = false) String pageToken,
      @RequestParam(defaultValue = "100") int pageSize) {
    requireRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            merchantService.products(
                companyId, accountId, merchantId, pageToken, pageSize),
            "Merchant Center products fetched"));
  }

  @PostMapping("/accounts/{accountId}/merchant/accounts/{merchantId}/products")
  public ResponseEntity<ApiResponse<GoogleMerchantProductResponse>> upsertMerchantProduct(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String merchantId,
      @Valid @RequestBody UpsertGoogleMerchantProductRequest request) {
    Long userId = requireModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            merchantService.upsertProduct(
                companyId, accountId, merchantId, request, userId),
            "Merchant Center product submitted"));
  }

  @PostMapping("/accounts/{accountId}/merchant/accounts/{merchantId}/products:delete")
  public ResponseEntity<ApiResponse<Boolean>> deleteMerchantProduct(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String merchantId,
      @Valid @RequestBody DeleteGoogleMerchantProductRequest request) {
    Long userId = requireModify(companyId);
    merchantService.deleteProduct(
        companyId, accountId, merchantId, request, userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, true, "Merchant Center product input deleted"));
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
