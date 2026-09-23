package com.messaging.ads.meta;

import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.meta.dto.CreateMetaCampaignRequest;
import com.messaging.ads.meta.dto.MetaAdsAuthorizationResponse;
import com.messaging.ads.meta.dto.MetaCampaignStatusRequest;
import com.messaging.ads.meta.dto.UpdateMetaCampaignRequest;
import com.messaging.ads.meta.dto.MetaAdsCampaignSyncResponse;
import com.messaging.ads.meta.dto.MetaAdsResourceSyncResponse;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
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
  private final MetaAdsResourceSyncService resourceSyncService;
  private final MetaAdsCampaignSyncService campaignSyncService;
  private final MetaAdsCampaignMutationService campaignMutationService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

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
