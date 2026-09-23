package com.messaging.ads.controller;

import com.messaging.ads.dto.AdAccountResourceResponse;
import com.messaging.ads.dto.AdAccountResponse;
import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.dto.AdProviderConnectionResponse;
import com.messaging.ads.dto.AdProviderResponse;
import com.messaging.ads.dto.UpdateAdAccountSelectionRequest;
import com.messaging.ads.service.AdAccountResourceService;
import com.messaging.ads.service.AdCampaignService;
import com.messaging.ads.service.AdCenterService;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
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
  private final AdAccountResourceService resourceService;
  private final AdCampaignService campaignService;
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
            adCenterService.updateSelection(companyId, accountId, request),
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
            adCenterService.disconnect(companyId, connectionId),
            "Advertising provider disconnected"));
  }

  private void requireRead(Long companyId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.ADS_READ);
  }
}
