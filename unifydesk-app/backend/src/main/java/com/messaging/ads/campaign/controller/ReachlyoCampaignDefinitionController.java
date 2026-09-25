package com.messaging.ads.campaign.controller;

import com.messaging.ads.campaign.dto.AudienceDefinitionResponse;
import com.messaging.ads.campaign.dto.ConversionGoalMappingResponse;
import com.messaging.ads.campaign.dto.CreateAudienceDefinitionRequest;
import com.messaging.ads.campaign.dto.CreateCreativePackRequest;
import com.messaging.ads.campaign.dto.CreateCreativeVariantRequest;
import com.messaging.ads.campaign.dto.CreativePackResponse;
import com.messaging.ads.campaign.dto.CreativeVariantResponse;
import com.messaging.ads.campaign.dto.UpdateAudienceDefinitionRequest;
import com.messaging.ads.campaign.dto.UpdateCreativePackRequest;
import com.messaging.ads.campaign.dto.UpdateCreativeVariantRequest;
import com.messaging.ads.campaign.dto.UpsertConversionGoalMappingRequest;
import com.messaging.ads.campaign.service.AudienceDefinitionService;
import com.messaging.ads.campaign.service.ConversionGoalMappingService;
import com.messaging.ads.campaign.service.CreativePackService;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/ads/reachlyo-campaigns/{campaignId}")
@RequiredArgsConstructor
public class ReachlyoCampaignDefinitionController {

  private final CreativePackService creativePackService;
  private final AudienceDefinitionService audienceDefinitionService;
  private final ConversionGoalMappingService conversionGoalMappingService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/creative-packs")
  public ResponseEntity<ApiResponse<List<CreativePackResponse>>> creativePacks(
      @PathVariable Long companyId, @PathVariable Long campaignId) {
    require(companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, creativePackService.list(companyId, campaignId), "Creative packs fetched"));
  }

  @PostMapping("/creative-packs")
  public ResponseEntity<ApiResponse<CreativePackResponse>> createCreativePack(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody CreateCreativePackRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(
            201,
            creativePackService.create(companyId, userId, campaignId, request),
            "Creative pack created"));
  }

  @GetMapping("/creative-packs/{packId}")
  public ResponseEntity<ApiResponse<CreativePackResponse>> creativePack(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long packId) {
    require(companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, creativePackService.get(companyId, campaignId, packId), "Creative pack fetched"));
  }

  @PatchMapping("/creative-packs/{packId}")
  public ResponseEntity<ApiResponse<CreativePackResponse>> updateCreativePack(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long packId,
      @Valid @RequestBody UpdateCreativePackRequest request) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            creativePackService.update(companyId, campaignId, packId, request),
            "Creative pack updated"));
  }

  @DeleteMapping("/creative-packs/{packId}")
  public ResponseEntity<ApiResponse<Void>> deleteCreativePack(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long packId) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    creativePackService.delete(companyId, campaignId, packId);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Creative pack deleted"));
  }

  @PostMapping("/creative-packs/{packId}/variants")
  public ResponseEntity<ApiResponse<CreativeVariantResponse>> createVariant(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long packId,
      @Valid @RequestBody CreateCreativeVariantRequest request) {
    require(companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(
            201,
            creativePackService.createVariant(companyId, campaignId, packId, request),
            "Creative variant created"));
  }

  @PatchMapping("/creative-packs/{packId}/variants/{variantId}")
  public ResponseEntity<ApiResponse<CreativeVariantResponse>> updateVariant(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long packId,
      @PathVariable Long variantId,
      @Valid @RequestBody UpdateCreativeVariantRequest request) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            creativePackService.updateVariant(companyId, campaignId, packId, variantId, request),
            "Creative variant updated"));
  }

  @DeleteMapping("/creative-packs/{packId}/variants/{variantId}")
  public ResponseEntity<ApiResponse<Void>> deleteVariant(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long packId,
      @PathVariable Long variantId) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    creativePackService.deleteVariant(companyId, campaignId, packId, variantId);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Creative variant deleted"));
  }

  @GetMapping("/audience-definitions")
  public ResponseEntity<ApiResponse<List<AudienceDefinitionResponse>>> audiences(
      @PathVariable Long companyId, @PathVariable Long campaignId) {
    require(companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, audienceDefinitionService.list(companyId, campaignId), "Audience definitions fetched"));
  }

  @PostMapping("/audience-definitions")
  public ResponseEntity<ApiResponse<AudienceDefinitionResponse>> createAudience(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody CreateAudienceDefinitionRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(
            201,
            audienceDefinitionService.create(companyId, userId, campaignId, request),
            "Audience definition created"));
  }

  @GetMapping("/audience-definitions/{definitionId}")
  public ResponseEntity<ApiResponse<AudienceDefinitionResponse>> audience(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long definitionId) {
    require(companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            audienceDefinitionService.get(companyId, campaignId, definitionId),
            "Audience definition fetched"));
  }

  @PatchMapping("/audience-definitions/{definitionId}")
  public ResponseEntity<ApiResponse<AudienceDefinitionResponse>> updateAudience(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long definitionId,
      @Valid @RequestBody UpdateAudienceDefinitionRequest request) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            audienceDefinitionService.update(companyId, campaignId, definitionId, request),
            "Audience definition updated"));
  }

  @DeleteMapping("/audience-definitions/{definitionId}")
  public ResponseEntity<ApiResponse<Void>> deleteAudience(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long definitionId) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    audienceDefinitionService.delete(companyId, campaignId, definitionId);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Audience definition deleted"));
  }

  @GetMapping("/conversion-goal-mappings")
  public ResponseEntity<ApiResponse<List<ConversionGoalMappingResponse>>> conversionMappings(
      @PathVariable Long companyId, @PathVariable Long campaignId) {
    require(companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            conversionGoalMappingService.list(companyId, campaignId),
            "Conversion goal mappings fetched"));
  }

  @PutMapping("/conversion-goal-mappings")
  public ResponseEntity<ApiResponse<ConversionGoalMappingResponse>> upsertConversionMapping(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @Valid @RequestBody UpsertConversionGoalMappingRequest request) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            conversionGoalMappingService.upsert(companyId, campaignId, request),
            "Conversion goal mapping saved"));
  }

  @DeleteMapping("/conversion-goal-mappings/{mappingId}")
  public ResponseEntity<ApiResponse<Void>> deleteConversionMapping(
      @PathVariable Long companyId,
      @PathVariable Long campaignId,
      @PathVariable Long mappingId) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    conversionGoalMappingService.delete(companyId, campaignId, mappingId);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Conversion goal mapping deleted"));
  }

  private Long require(Long companyId, String permission) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }
}
