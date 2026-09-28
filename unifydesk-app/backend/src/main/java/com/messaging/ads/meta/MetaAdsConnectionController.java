package com.messaging.ads.meta;

import com.fasterxml.jackson.databind.JsonNode;

import com.messaging.ads.dto.AdAccountBillingResponse;
import com.messaging.ads.dto.AdAccountHealthResponse;
import com.messaging.ads.dto.AdAudienceResponse;
import com.messaging.ads.dto.AdAccountResourceResponse;
import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.dto.AdConversionResponse;
import com.messaging.ads.dto.AdCreativeResponse;
import com.messaging.ads.dto.AdResponse;
import com.messaging.ads.dto.AdSetResponse;
import com.messaging.ads.meta.dto.CreateMetaAudienceRequest;
import com.messaging.ads.meta.dto.CreateMetaCampaignRequest;
import com.messaging.ads.meta.dto.CreateMetaConversionRequest;
import com.messaging.ads.meta.dto.CreateMetaPixelRequest;
import com.messaging.ads.meta.dto.CreateMetaCreativeRequest;
import com.messaging.ads.meta.dto.CreateMetaAdRequest;
import com.messaging.ads.meta.dto.CreateMetaCatalogAdRequest;
import com.messaging.ads.meta.dto.CreateMetaAdSetRequest;
import com.messaging.ads.meta.dto.MetaAdsAuthorizationResponse;
import com.messaging.ads.meta.dto.MetaAudienceSyncResponse;
import com.messaging.ads.meta.dto.MetaCampaignStatusRequest;
import com.messaging.ads.meta.dto.MetaConversionSyncResponse;
import com.messaging.ads.meta.dto.MetaInsightBreakdownsSyncResponse;
import com.messaging.ads.meta.dto.MetaCustomerListMembersRequest;
import com.messaging.ads.meta.dto.MetaCustomerListMembersResponse;
import com.messaging.ads.meta.dto.MetaInsightsSyncResponse;
import com.messaging.ads.meta.dto.MetaGeoLocationSuggestion;
import com.messaging.ads.meta.dto.MetaDetailedTargetingSuggestion;
import com.messaging.ads.meta.dto.MetaAdStatusRequest;
import com.messaging.ads.meta.dto.MetaAdSyncResponse;
import com.messaging.ads.meta.dto.MetaCreativeSyncResponse;
import com.messaging.ads.meta.dto.MetaAdSetStatusRequest;
import com.messaging.ads.meta.dto.MetaAdSetSyncResponse;
import com.messaging.ads.meta.dto.UpdateMetaCampaignRequest;
import com.messaging.ads.meta.dto.UpdateMetaSpendCapRequest;
import com.messaging.ads.meta.dto.UpdateMetaAdRequest;
import com.messaging.ads.meta.dto.UpdateMetaAdSetRequest;
import com.messaging.ads.meta.dto.MetaAdsCampaignSyncResponse;
import com.messaging.ads.meta.dto.MetaAdsResourceSyncResponse;
import com.messaging.ads.meta.dto.MetaAdStudyResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/ads/meta")
@RequiredArgsConstructor
public class MetaAdsConnectionController {

  private final MetaAdsConnectionService connectionService;
  private final MetaAdsBillingService billingService;
  private final MetaAdsHealthService healthService;
  private final MetaAdsPixelMutationService pixelMutationService;
  private final MetaAdsInsightSyncService insightSyncService;
  private final MetaAdsInsightBreakdownSyncService insightBreakdownSyncService;
  private final MetaAdsResourceSyncService resourceSyncService;
  private final MetaAdsAudienceSyncService audienceSyncService;
  private final MetaAdsAudienceMutationService audienceMutationService;
  private final MetaAdsCustomerListService customerListService;
  private final MetaAdsCampaignSyncService campaignSyncService;
  private final MetaAdsConversionSyncService conversionSyncService;
  private final MetaAdsConversionMutationService conversionMutationService;
  private final MetaAdsCampaignMutationService campaignMutationService;
  private final MetaAdsCreativeSyncService creativeSyncService;
  private final MetaAdsCreativeMutationService creativeMutationService;
  private final MetaAdsAdSyncService adSyncService;
  private final MetaAdsAdMutationService adMutationService;
  private final MetaAdsAdSetSyncService adSetSyncService;
  private final MetaAdsAdSetMutationService adSetMutationService;
  private final MetaAdsGeoTargetingService geoTargetingService;
  private final MetaAdsDetailedTargetingService detailedTargetingService;
  private final MetaAdsExperimentService experimentService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/accounts/{accountId}/experiments")
  public ResponseEntity<ApiResponse<List<MetaAdStudyResponse>>> experiments(
      @PathVariable Long companyId,
      @PathVariable Long accountId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            experimentService.studies(companyId, accountId),
            "Meta advertising studies fetched"));
  }

  @GetMapping("/accounts/{accountId}/geo-targeting/search")
  public ResponseEntity<ApiResponse<List<MetaGeoLocationSuggestion>>> searchGeoTargeting(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) List<String> types,
      @RequestParam(required = false) String countryCode,
      @RequestParam(defaultValue = "20") Integer limit) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            geoTargetingService.search(
                companyId, accountId, q, types, countryCode, limit),
            "Meta geo targeting suggestions fetched"));
  }

  @GetMapping("/accounts/{accountId}/detailed-targeting/interests")
  public ResponseEntity<ApiResponse<List<MetaDetailedTargetingSuggestion>>> searchInterests(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @RequestParam String q,
      @RequestParam(defaultValue = "20") Integer limit) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            detailedTargetingService.searchInterests(companyId, accountId, q, limit),
            "Meta detailed targeting interests fetched"));
  }

  @PostMapping("/accounts/{accountId}/health/sync")
  public ResponseEntity<ApiResponse<AdAccountHealthResponse>> syncHealth(
      @PathVariable Long companyId,
      @PathVariable Long accountId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            healthService.sync(companyId, accountId),
            "Meta account health synchronized"));
  }

  @PostMapping("/accounts/{accountId}/billing/sync")
  public ResponseEntity<ApiResponse<AdAccountBillingResponse>> syncBilling(
      @PathVariable Long companyId,
      @PathVariable Long accountId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            billingService.sync(companyId, accountId),
            "Meta billing synchronized"));
  }

  @PatchMapping("/accounts/{accountId}/billing/spend-cap")
  public ResponseEntity<ApiResponse<AdAccountBillingResponse>> updateSpendCap(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody UpdateMetaSpendCapRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            billingService.updateSpendCap(
                companyId, accountId, request.spendCapMinor(), userId),
            "Meta account spend cap updated"));
  }

  @PostMapping("/accounts/{accountId}/insights/sync")
  public ResponseEntity<ApiResponse<MetaInsightsSyncResponse>> syncInsights(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            insightSyncService.sync(companyId, accountId, from, to),
            "Meta analytics synchronized"));
  }

  @PostMapping("/accounts/{accountId}/insights/breakdowns/sync")
  public ResponseEntity<ApiResponse<MetaInsightBreakdownsSyncResponse>> syncInsightBreakdowns(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            insightBreakdownSyncService.sync(companyId, accountId, from, to),
            "Meta analytics breakdowns synchronized"));
  }

  @PostMapping("/accounts/{accountId}/pixels")
  public ResponseEntity<ApiResponse<AdAccountResourceResponse>> createPixel(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody CreateMetaPixelRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                pixelMutationService.create(companyId, accountId, request),
                "Meta pixel created"));
  }

  @PostMapping("/accounts/{accountId}/audiences/sync")
  public ResponseEntity<ApiResponse<MetaAudienceSyncResponse>> syncAudiences(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            audienceSyncService.sync(companyId, accountId),
            "Meta audiences synchronized"));
  }

  @PostMapping("/accounts/{accountId}/audiences")
  public ResponseEntity<ApiResponse<AdAudienceResponse>> createAudience(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody CreateMetaAudienceRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                audienceMutationService.create(companyId, accountId, request, userId),
                "Meta audience created"));
  }

  @PostMapping("/audiences/{audienceId}/members")
  public ResponseEntity<ApiResponse<MetaCustomerListMembersResponse>> mutateCustomerListMembers(
      @PathVariable Long companyId,
      @PathVariable Long audienceId,
      @Valid @RequestBody MetaCustomerListMembersRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            customerListService.mutateMembers(companyId, audienceId, request, userId),
            request.operation() == MetaCustomerListMembersRequest.Operation.ADD
                ? "Meta customer-list members submitted"
                : "Meta customer-list members removed"));
  }

  @PostMapping("/accounts/{accountId}/conversions/sync")
  public ResponseEntity<ApiResponse<MetaConversionSyncResponse>> syncConversions(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            conversionSyncService.sync(companyId, accountId),
            "Meta custom conversions synchronized"));
  }

  @PostMapping("/accounts/{accountId}/conversions")
  public ResponseEntity<ApiResponse<AdConversionResponse>> createConversion(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody CreateMetaConversionRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                conversionMutationService.create(companyId, accountId, request),
                "Meta custom conversion created"));
  }
  @GetMapping("/accounts/{accountId}/pages/{pageId}/posts")
  public ResponseEntity<ApiResponse<List<MetaAdsApiClient.ExistingPostSummary>>> existingPosts(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @PathVariable String pageId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            creativeMutationService.existingPosts(companyId, accountId, pageId),
            "Meta existing posts fetched"));
  }

  @PostMapping("/accounts/{accountId}/creatives/sync")
  public ResponseEntity<ApiResponse<MetaCreativeSyncResponse>> syncCreatives(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            creativeSyncService.sync(companyId, accountId),
            "Meta creatives synchronized"));
  }

  @PostMapping("/accounts/{accountId}/creatives")
  public ResponseEntity<ApiResponse<AdCreativeResponse>> createCreative(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody CreateMetaCreativeRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                creativeMutationService.create(companyId, accountId, request, userId),
                "Meta creative created"));
  }

  @PostMapping("/ad-sets/{adSetId}/ads/sync")
  public ResponseEntity<ApiResponse<MetaAdSyncResponse>> syncAds(
      @PathVariable Long companyId, @PathVariable Long adSetId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            adSyncService.sync(companyId, adSetId),
            "Meta ads synchronized"));
  }

  @PostMapping("/ad-sets/{adSetId}/ads")
  public ResponseEntity<ApiResponse<AdResponse>> createAd(
      @PathVariable Long companyId,
      @PathVariable Long adSetId,
      @Valid @RequestBody CreateMetaAdRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                adMutationService.create(companyId, adSetId, request),
                "Meta ad created in paused state"));
  }

  @PostMapping("/ad-sets/{adSetId}/catalog-ads")
  public ResponseEntity<ApiResponse<AdResponse>> createCatalogAd(
      @PathVariable Long companyId,
      @PathVariable Long adSetId,
      @Valid @RequestBody CreateMetaCatalogAdRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                adMutationService.createCatalog(companyId, adSetId, request, userId),
                "Meta catalog ad created in paused state"));
  }

  @GetMapping("/ads/{adId}/background-preview")
  public ResponseEntity<ApiResponse<JsonNode>> backgroundPreview(
      @PathVariable Long companyId,
      @PathVariable Long adId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            adMutationService.backgroundPreview(companyId, adId),
            "Meta background generation preview fetched"));
  }

  @PatchMapping("/ads/{adId}")
  public ResponseEntity<ApiResponse<AdResponse>> updateAd(
      @PathVariable Long companyId,
      @PathVariable Long adId,
      @Valid @RequestBody UpdateMetaAdRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            adMutationService.update(companyId, adId, request),
            "Meta ad updated"));
  }

  @PostMapping("/ads/{adId}/status")
  public ResponseEntity<ApiResponse<AdResponse>> updateAdStatus(
      @PathVariable Long companyId,
      @PathVariable Long adId,
      @Valid @RequestBody MetaAdStatusRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            adMutationService.setStatus(companyId, adId, request.status()),
            "Meta ad status updated"));
  }
  @PostMapping("/campaigns/{campaignId}/ad-sets/sync")
  public ResponseEntity<ApiResponse<MetaAdSetSyncResponse>> syncAdSets(
      @PathVariable Long companyId, @PathVariable Long campaignId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            adSetSyncService.sync(companyId, campaignId),
            "Meta ad sets synchronized"));
  }

  @PostMapping("/campaigns/{campaignId}/ad-sets")
  public ResponseEntity<ApiResponse<AdSetResponse>> createAdSet(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody CreateMetaAdSetRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                adSetMutationService.create(companyId, campaignId, request, userId),
                "Meta ad set created in paused state"));
  }

  @PatchMapping("/ad-sets/{adSetId}")
  public ResponseEntity<ApiResponse<AdSetResponse>> updateAdSet(
      @PathVariable Long companyId,
      @PathVariable Long adSetId,
      @Valid @RequestBody UpdateMetaAdSetRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            adSetMutationService.update(companyId, adSetId, request, userId),
            "Meta ad set updated"));
  }

  @PostMapping("/ad-sets/{adSetId}/status")
  public ResponseEntity<ApiResponse<AdSetResponse>> updateAdSetStatus(
      @PathVariable Long companyId,
      @PathVariable Long adSetId,
      @Valid @RequestBody MetaAdSetStatusRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            adSetMutationService.setStatus(companyId, adSetId, request.status()),
            "Meta ad set status updated"));
  }
  @PostMapping("/accounts/{accountId}/campaigns")
  public ResponseEntity<ApiResponse<AdCampaignResponse>> createCampaign(
      @PathVariable Long companyId,
      @PathVariable Long accountId,
      @Valid @RequestBody CreateMetaCampaignRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                campaignMutationService.create(companyId, accountId, request),
                "Meta campaign created in paused state"));
  }

  @PatchMapping("/campaigns/{campaignId}")
  public ResponseEntity<ApiResponse<AdCampaignResponse>> updateCampaign(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody UpdateMetaCampaignRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            campaignMutationService.update(companyId, campaignId, request),
            "Meta campaign updated"));
  }

  @PostMapping("/campaigns/{campaignId}/status")
  public ResponseEntity<ApiResponse<AdCampaignResponse>> updateCampaignStatus(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody MetaCampaignStatusRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            campaignMutationService.setStatus(companyId, campaignId, request.status()),
            "Meta campaign status updated"));
  }
  @PostMapping("/accounts/{accountId}/campaigns/sync")
  public ResponseEntity<ApiResponse<MetaAdsCampaignSyncResponse>> syncCampaigns(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            campaignSyncService.sync(companyId, accountId),
            "Meta Ads campaigns synchronized"));
  }

  @PostMapping("/accounts/{accountId}/resources/sync")
  public ResponseEntity<ApiResponse<MetaAdsResourceSyncResponse>> syncResources(
      @PathVariable Long companyId, @PathVariable Long accountId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            resourceSyncService.sync(companyId, accountId),
            "Meta Ads account resources synchronized"));
  }

  @GetMapping("/authorize")
  public ResponseEntity<ApiResponse<MetaAdsAuthorizationResponse>> authorize(
      @PathVariable Long companyId,
      @RequestParam(required = false) String returnTo) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_CONNECT);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.authorization(companyId, userId, returnTo),
            "Meta Ads authorization created"));
  }
}
