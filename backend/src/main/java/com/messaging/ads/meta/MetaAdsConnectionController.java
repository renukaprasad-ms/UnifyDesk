package com.messaging.ads.meta;

import com.messaging.ads.meta.dto.MetaAdsAuthorizationResponse;
import com.messaging.ads.meta.dto.MetaAdsCampaignSyncResponse;
import com.messaging.ads.meta.dto.MetaAdsResourceSyncResponse;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

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
