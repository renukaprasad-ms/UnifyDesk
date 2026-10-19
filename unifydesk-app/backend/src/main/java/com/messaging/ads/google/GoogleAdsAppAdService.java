package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.dto.AdResponse;
import com.messaging.ads.dto.AdSetResponse;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleAppAdGroupRequest;
import com.messaging.ads.google.dto.CreateGoogleAppAdRequest;
import com.messaging.ads.google.dto.GoogleAppCreativeAsset;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsAppAdService {

  private final AdCampaignRepository campaignRepository;
  private final AdSetRepository adSetRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsAdGroupSyncService adGroupSyncService;
  private final GoogleAdsAdGroupPersistenceService adGroupPersistenceService;
  private final GoogleAdsAdSyncService adSyncService;
  private final GoogleAdsAdPersistenceService adPersistenceService;
  private final AdActivityLogService activityLogService;

  public List<GoogleAppCreativeAsset> creativeAssets(Long companyId, Long accountId) {
    var context = contextService.requireManaged(companyId, accountId);
    return apiClient
        .searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "SELECT asset.resource_name, asset.name, asset.type, "
                + "asset.image_asset.full_size.url, "
                + "asset.youtube_video_asset.youtube_video_id "
                + "FROM asset WHERE asset.type IN ('IMAGE','YOUTUBE_VIDEO') "
                + "AND asset.source = 'ADVERTISER'")
        .stream()
        .map(
            row -> {
              JsonNode asset = row.path("asset");
              return new GoogleAppCreativeAsset(
                  text(asset, "resourceName"),
                  text(asset, "name"),
                  text(asset, "type"),
                  text(asset.path("imageAsset").path("fullSize"), "url"),
                  text(asset.path("youtubeVideoAsset"), "youtubeVideoId"));
            })
        .filter(item -> item.resourceName() != null)
        .toList();
  }

  public AdSetResponse createAdGroup(
      Long companyId, Long campaignId, CreateGoogleAppAdGroupRequest request, Long actorUserId) {
    CampaignContext context = requireAppCampaign(companyId, campaignId);
    String subtype = context.campaign().getBuyingType();
    String customerId = context.google().account().getExternalAccountId();
    String campaignResource =
        apiClient.customerResourceName(
            customerId, "campaigns", context.campaign().getExternalCampaignId());

    if (!"APP_CAMPAIGN_FOR_ENGAGEMENT".equals(subtype) && request.targetCpaMinor() != null) {
      throw new BadRequestException(
          "Only App engagement campaigns can override target CPA at ad-group level");
    }
    if ("APP_CAMPAIGN_FOR_ENGAGEMENT".equals(subtype)
        && (request.userListId() == null || request.userListId().isBlank())) {
      throw new BadRequestException("App engagement ad groups must target a Google user list");
    }
    if (!"APP_CAMPAIGN_FOR_ENGAGEMENT".equals(subtype)
        && request.userListId() != null
        && !request.userListId().isBlank()) {
      throw new BadRequestException(
          "User-list criteria in this App flow are supported only for App engagement campaigns");
    }

    String adGroupResource = apiClient.customerResourceName(customerId, "adGroups", "-1");
    Map<String, Object> adGroup = new LinkedHashMap<>();
    adGroup.put("resourceName", adGroupResource);
    adGroup.put("campaign", campaignResource);
    adGroup.put("name", request.name().trim());
    adGroup.put("status", "PAUSED");
    if (request.targetCpaMinor() != null) {
      adGroup.put(
          "targetCpaMicros",
          GoogleAdsMoney.minorToMicros(
              request.targetCpaMinor(), context.google().account().getCurrency()));
    }

    List<Map<String, Object>> operations = new ArrayList<>();
    operations.add(Map.of("adGroupOperation", Map.of("create", adGroup)));

    if ("APP_CAMPAIGN_FOR_ENGAGEMENT".equals(subtype)) {
      String userListResource = normalizeUserListResource(customerId, request.userListId());
      operations.add(
          Map.of(
              "adGroupCriterionOperation",
              Map.of(
                  "create",
                  Map.of(
                      "adGroup",
                      adGroupResource,
                      "userList",
                      Map.of("userList", userListResource),
                      "negative",
                      false))));
    }

    JsonNode response =
        apiClient.mutateAll(
            context.google().accessToken(),
            customerId,
            context.google().loginCustomerId(),
            operations);
    String resourceName = apiClient.firstBulkResourceName(response, "adGroupResult");
    String externalAdGroupId = suffix(resourceName, "ad group");

    JsonNode row =
        adGroupSyncService.adGroup(
            context.google(), context.campaign().getExternalCampaignId(), externalAdGroupId);
    AdSetResponse created =
        AdSetResponse.from(adGroupPersistenceService.persistOne(companyId, campaignId, row));

    activityLogService.record(
        companyId,
        context.campaign().getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_APP_AD_GROUP_CREATED",
        "AD_GROUP",
        externalAdGroupId,
        null,
        created,
        Map.of("subtype", subtype, "initialStatus", "PAUSED"));
    return created;
  }

  public AdResponse createAd(
      Long companyId, Long adGroupId, CreateGoogleAppAdRequest request, Long actorUserId) {
    AdContext context = requireAppAdGroup(companyId, adGroupId);
    String subtype = context.campaign().getBuyingType();
    validateTextList(request.headlines(), 1, 5, 30, "headline");
    validateTextList(request.descriptions(), 1, 5, 90, "description");

    String customerId = context.google().account().getExternalAccountId();
    List<Map<String, Object>> headlines =
        request.headlines().stream()
            .map(value -> Map.<String, Object>of("text", value.trim()))
            .toList();
    List<Map<String, Object>> descriptions =
        request.descriptions().stream()
            .map(value -> Map.<String, Object>of("text", value.trim()))
            .toList();
    List<Map<String, Object>> images =
        normalizeAssetResources(customerId, request.imageAssetResourceNames()).stream()
            .map(resource -> Map.<String, Object>of("asset", resource))
            .toList();
    List<Map<String, Object>> videos =
        normalizeAssetResources(customerId, request.videoAssetResourceNames()).stream()
            .map(resource -> Map.<String, Object>of("asset", resource))
            .toList();

    Map<String, Object> format = new LinkedHashMap<>();
    format.put("headlines", headlines);
    format.put("descriptions", descriptions);
    if (!images.isEmpty()) format.put("images", images);

    Map<String, Object> ad = new LinkedHashMap<>();
    ad.put("name", request.name().trim());

    switch (subtype) {
      case "APP_CAMPAIGN" -> {
        if (!videos.isEmpty()) format.put("youtubeVideos", videos);
        ad.put("appAd", format);
      }
      case "APP_CAMPAIGN_FOR_ENGAGEMENT" -> {
        if (!videos.isEmpty()) format.put("videos", videos);
        ad.put("appEngagementAd", format);
      }
      case "APP_CAMPAIGN_FOR_PRE_REGISTRATION" -> {
        if (!videos.isEmpty()) format.put("youtubeVideos", videos);
        ad.put("appPreRegistrationAd", format);
      }
      default -> throw new BadRequestException("Unsupported Google App campaign subtype");
    }

    Map<String, Object> adGroupAd = new LinkedHashMap<>();
    adGroupAd.put(
        "adGroup",
        apiClient.customerResourceName(
            customerId, "adGroups", context.adGroup().getExternalAdSetId()));
    adGroupAd.put("status", "PAUSED");
    adGroupAd.put("ad", ad);

    JsonNode response =
        apiClient.mutate(
            context.google().accessToken(),
            customerId,
            context.google().loginCustomerId(),
            "adGroupAds",
            List.of(Map.of("create", adGroupAd)));
    String externalAdId = suffix(apiClient.firstMutatedResourceName(response), "ad");

    JsonNode row =
        adSyncService.ad(context.google(), context.adGroup().getExternalAdSetId(), externalAdId);
    AdResponse created =
        AdResponse.from(adPersistenceService.persistOne(companyId, adGroupId, row));

    activityLogService.record(
        companyId,
        context.adGroup().getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_APP_AD_CREATED",
        "AD",
        externalAdId,
        null,
        created,
        Map.of(
            "subtype",
            subtype,
            "initialStatus",
            "PAUSED",
            "imageAssetCount",
            images.size(),
            "videoAssetCount",
            videos.size()));
    return created;
  }

  private CampaignContext requireAppCampaign(Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !"APP".equalsIgnoreCase(campaign.getObjective())
        || !campaign.isAccessible()) {
      throw new BadRequestException("Google App campaign is not available");
    }
    return new CampaignContext(
        campaign, contextService.requireManaged(companyId, campaign.getAdAccountId()));
  }

  private AdContext requireAppAdGroup(Long companyId, Long adGroupId) {
    AdSet adGroup =
        adSetRepository
            .findByIdAndCompanyId(adGroupId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising ad group not found"));
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(adGroup.getCampaignId(), companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (adGroup.getProvider() != AdProvider.GOOGLE_ADS
        || campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !"APP".equalsIgnoreCase(campaign.getObjective())
        || !adGroup.isAccessible()
        || !campaign.isAccessible()) {
      throw new BadRequestException("Google App ad group is not available");
    }
    return new AdContext(
        adGroup, campaign, contextService.requireManaged(companyId, adGroup.getAdAccountId()));
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isNull()) return null;
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    String result = value.asText(null);
    return result == null || result.isBlank() ? null : result.trim();
  }

  private String normalizeUserListResource(String customerId, String value) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException("Google App engagement user list is required");
    }
    String normalized = value.trim();
    if (normalized.matches("\\d+")) {
      return apiClient.customerResourceName(customerId, "userLists", normalized);
    }
    if (!normalized.matches("customers/\\d+/userLists/\\d+")) {
      throw new BadRequestException("Invalid Google user list resource name");
    }
    return normalized;
  }

  private List<String> normalizeAssetResources(String customerId, List<String> values) {
    if (values == null || values.isEmpty()) return List.of();
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (String value : values) {
      if (value == null || value.isBlank()) continue;
      String normalized = value.trim();
      if (normalized.matches("\\d+")) {
        normalized = apiClient.customerResourceName(customerId, "assets", normalized);
      } else if (!normalized.matches("customers/\\d+/assets/\\d+")) {
        throw new BadRequestException("Invalid Google asset resource name: " + normalized);
      }
      result.add(normalized);
    }
    return List.copyOf(result);
  }

  private void validateTextList(
      List<String> values, int min, int max, int maxLength, String label) {
    if (values == null || values.size() < min || values.size() > max) {
      throw new BadRequestException(
          "Google App ad requires between " + min + " and " + max + " " + label + " values");
    }
    for (String value : values) {
      if (value == null || value.isBlank() || value.trim().length() > maxLength) {
        throw new BadRequestException(
            "Google App " + label + " must be non-empty and at most " + maxLength + " characters");
      }
    }
  }

  private String suffix(String resourceName, String label) {
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException("Google Ads did not return the created " + label);
    }
    int slash = resourceName.lastIndexOf('/');
    if (slash < 0 || slash == resourceName.length() - 1) {
      throw new BadRequestException("Invalid Google Ads " + label + " resource name");
    }
    String suffix = resourceName.substring(slash + 1);
    int separator = suffix.lastIndexOf('~');
    return separator >= 0 ? suffix.substring(separator + 1) : suffix;
  }

  private record CampaignContext(
      AdCampaign campaign, GoogleAdsAccountContextService.Context google) {}

  private record AdContext(
      AdSet adGroup, AdCampaign campaign, GoogleAdsAccountContextService.Context google) {}
}
