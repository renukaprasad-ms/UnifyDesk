package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGooglePerformanceMaxCampaignRequest;
import com.messaging.ads.google.dto.GoogleAssetGroupResponse;
import com.messaging.ads.google.dto.GoogleAssetGroupUrlOptionsResponse;
import com.messaging.ads.google.dto.GoogleAutomatedVideoCrawlSource;
import com.messaging.ads.google.dto.GooglePerformanceMaxAutomationRequest;
import com.messaging.ads.google.dto.GooglePerformanceMaxAutomationResponse;
import com.messaging.ads.google.dto.GooglePerformanceMaxBrandAssetResponse;
import com.messaging.ads.google.dto.GooglePerformanceMaxBrandGuidelinesRequest;
import com.messaging.ads.google.dto.GooglePerformanceMaxBrandGuidelinesResponse;
import com.messaging.ads.google.dto.GoogleUrlCustomParameter;
import com.messaging.ads.google.dto.GoogleUrlOptionsRequest;
import com.messaging.ads.google.dto.GoogleAssetResponse;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsPerformanceMaxService {

  private static final DateTimeFormatter GOOGLE_DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
  private static final Set<String> BRAND_FONTS =
      Set.of(
          "Open Sans",
          "Roboto",
          "Montserrat",
          "Poppins",
          "Lato",
          "Oswald",
          "Playfair Display",
          "Roboto Slab");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsCampaignSyncService campaignSyncService;
  private final GoogleAdsCampaignPersistenceService campaignPersistenceService;
  private final AdCampaignRepository campaignRepository;
  private final AdActivityLogService activityLogService;

  public AdCampaignResponse create(
      Long companyId,
      Long accountId,
      CreateGooglePerformanceMaxCampaignRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    if (context.managerAccount()) {
      throw new BadRequestException(
          "Google Ads manager accounts cannot directly own Performance Max campaigns");
    }

    validate(request);

    String customerId = context.account().getExternalAccountId();
    long budgetMicros =
        GoogleAdsMoney.minorToMicros(
            request.dailyBudgetMinor(), context.account().getCurrency());

    String budgetResource = resource(customerId, "campaignBudgets", "-1");
    String campaignResource = resource(customerId, "campaigns", "-2");
    String assetGroupResource = resource(customerId, "assetGroups", "-3");

    List<Map<String, Object>> operations = new ArrayList<>();

    Map<String, Object> budget = new LinkedHashMap<>();
    budget.put("resourceName", budgetResource);
    budget.put("name", request.name().trim() + " budget " + System.currentTimeMillis());
    budget.put("amountMicros", budgetMicros);
    budget.put("deliveryMethod", "STANDARD");
    budget.put("explicitlyShared", false);
    operations.add(operation("campaignBudgetOperation", budget));

    boolean brandGuidelinesEnabled =
        request.brandGuidelinesEnabled() == null || request.brandGuidelinesEnabled();
    Map<String, Object> brandGuidelines = brandGuidelines(request, brandGuidelinesEnabled);

    Map<String, Object> campaign = new LinkedHashMap<>();
    campaign.put("resourceName", campaignResource);
    campaign.put("name", request.name().trim());
    campaign.put("status", "PAUSED");
    campaign.put("advertisingChannelType", "PERFORMANCE_MAX");
    campaign.put("campaignBudget", budgetResource);
    campaign.put("brandGuidelinesEnabled", brandGuidelinesEnabled);
    if (!brandGuidelines.isEmpty()) {
      campaign.put("brandGuidelines", brandGuidelines);
    }
    applyBidStrategy(campaign, request.bidStrategy());
    campaign.put(
        "containsEuPoliticalAdvertising",
        politicalAdvertisingStatus(request.containsEuPoliticalAdvertising()));
    if (request.startDate() != null) {
      campaign.put("startDateTime", startDateTime(request.startDate()));
    }
    if (request.endDate() != null) {
      campaign.put("endDateTime", endDateTime(request.endDate()));
    }
    operations.add(operation("campaignOperation", campaign));

    List<AssetLink> assets = new ArrayList<>();
    int assetId = -100;

    for (int i = 0; i < request.headlines().size(); i++) {
      assets.add(
          textAsset(
              operations,
              customerId,
              assetId--,
              request.name() + " headline " + (i + 1),
              request.headlines().get(i),
              "HEADLINE"));
    }

    assets.add(
        textAsset(
            operations,
            customerId,
            assetId--,
            request.name() + " long headline",
            request.longHeadline(),
            "LONG_HEADLINE"));

    for (int i = 0; i < request.descriptions().size(); i++) {
      assets.add(
          textAsset(
              operations,
              customerId,
              assetId--,
              request.name() + " description " + (i + 1),
              request.descriptions().get(i),
              "DESCRIPTION"));
    }

    AssetLink businessNameAsset =
        textAsset(
            operations,
            customerId,
            assetId--,
            request.name() + " business name",
            request.businessName(),
            "BUSINESS_NAME");
    if (!brandGuidelinesEnabled) {
      assets.add(businessNameAsset);
    }

    assets.add(
        imageAsset(
            operations,
            customerId,
            assetId--,
            request.name() + " marketing landscape",
            request.marketingImageBase64(),
            "MARKETING_IMAGE",
            ImageRule.LANDSCAPE_MARKETING));

    assets.add(
        imageAsset(
            operations,
            customerId,
            assetId--,
            request.name() + " marketing square",
            request.squareMarketingImageBase64(),
            "SQUARE_MARKETING_IMAGE",
            ImageRule.SQUARE_MARKETING));

    AssetLink logoAsset =
        imageAsset(
            operations,
            customerId,
            assetId--,
            request.name() + " logo",
            request.logoImageBase64(),
            "LOGO",
            ImageRule.LOGO);
    if (!brandGuidelinesEnabled) {
      assets.add(logoAsset);
    }

    if (request.portraitMarketingImageBase64() != null
        && !request.portraitMarketingImageBase64().isBlank()) {
      assets.add(
          imageAsset(
              operations,
              customerId,
              assetId--,
              request.name() + " marketing portrait",
              request.portraitMarketingImageBase64(),
              "PORTRAIT_MARKETING_IMAGE",
              ImageRule.PORTRAIT_MARKETING));
    }

    AssetLink landscapeLogoAsset = null;
    if (request.landscapeLogoImageBase64() != null
        && !request.landscapeLogoImageBase64().isBlank()) {
      landscapeLogoAsset =
          imageAsset(
              operations,
              customerId,
              assetId--,
              request.name() + " landscape logo",
              request.landscapeLogoImageBase64(),
              "LANDSCAPE_LOGO",
              ImageRule.LANDSCAPE_LOGO);
      if (!brandGuidelinesEnabled) {
        assets.add(landscapeLogoAsset);
      }
    }

    if (brandGuidelinesEnabled) {
      addCampaignAsset(operations, campaignResource, businessNameAsset);
      addCampaignAsset(operations, campaignResource, logoAsset);
      if (landscapeLogoAsset != null) {
        addCampaignAsset(operations, campaignResource, landscapeLogoAsset);
      }
    }

    if (request.youtubeVideoIds() != null) {
      List<String> videoIds =
          request.youtubeVideoIds().stream()
              .filter(value -> value != null && !value.isBlank())
              .map(String::trim)
              .distinct()
              .toList();
      if (videoIds.size() > 15) {
        throw new BadRequestException(
            "Performance Max supports at most 15 YouTube video assets per Asset Group");
      }
      for (int i = 0; i < videoIds.size(); i++) {
        String videoId = videoIds.get(i);
        validateYouTubeId(videoId);
        assets.add(
            youtubeAsset(
                operations,
                customerId,
                assetId--,
                request.name() + " YouTube video " + (i + 1),
                videoId));
      }
    }

    Map<String, Object> assetGroup = new LinkedHashMap<>();
    assetGroup.put("resourceName", assetGroupResource);
    assetGroup.put("name", generatedName(request.name(), "asset group"));
    assetGroup.put("campaign", campaignResource);
    assetGroup.put("finalUrls", List.of(request.finalUrl().trim()));
    assetGroup.put("status", "PAUSED");
    operations.add(operation("assetGroupOperation", assetGroup));

    for (AssetLink asset : assets) {
      Map<String, Object> link = new LinkedHashMap<>();
      link.put("assetGroup", assetGroupResource);
      link.put("asset", asset.resourceName());
      link.put("fieldType", asset.fieldType());
      operations.add(operation("assetGroupAssetOperation", link));
    }

    JsonNode response =
        apiClient.mutateAll(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            operations);

    String createdCampaignResource =
        apiClient.firstBulkResourceName(response, "campaignResult");
    String externalCampaignId = suffix(createdCampaignResource, "campaign");

    JsonNode campaignRow = campaignSyncService.campaign(context, externalCampaignId);
    AdCampaignResponse created =
        AdCampaignResponse.from(
            campaignPersistenceService.persistOne(companyId, accountId, campaignRow));

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_PERFORMANCE_MAX_CREATED",
        "CAMPAIGN",
        externalCampaignId,
        null,
        created,
        Map.of(
            "channel", "PERFORMANCE_MAX",
            "assetCount", assets.size(),
            "brandGuidelinesEnabled", brandGuidelinesEnabled,
            "initialStatus", "PAUSED"));

    return created;
  }

  public List<GoogleAssetResponse> assets(Long companyId, Long accountId) {
    var context = contextService.requireManaged(companyId, accountId);
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "SELECT asset.id, asset.resource_name, asset.name, asset.type, "
                + "asset.text_asset.text, asset.image_asset.full_size.url, "
                + "asset.image_asset.full_size.width_pixels, "
                + "asset.image_asset.full_size.height_pixels, "
                + "asset.youtube_video_asset.youtube_video_id "
                + "FROM asset "
                + "WHERE asset.type IN ('TEXT','IMAGE','YOUTUBE_VIDEO')");
    return rows.stream().map(this::asset).toList();
  }

  public List<GoogleAssetGroupResponse> assetGroups(Long companyId, Long campaignId) {
    AdCampaign campaign = requirePerformanceMaxCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());

    String query =
        "SELECT asset_group.id, asset_group.resource_name, asset_group.name, "
            + "asset_group.status, asset_group.primary_status, asset_group.ad_strength, "
            + "asset_group.final_urls "
            + "FROM asset_group WHERE campaign.id = "
            + numeric(campaign.getExternalCampaignId());

    return apiClient
        .searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)
        .stream()
        .map(this::assetGroup)
        .toList();
  }

  public GoogleAssetGroupResponse setAssetGroupStatus(
      Long companyId,
      Long campaignId,
      String assetGroupId,
      String requestedStatus,
      Long actorUserId) {
    AdCampaign campaign = requirePerformanceMaxCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());

    String status = requestedStatus == null ? "" : requestedStatus.trim().toUpperCase();
    if ("ACTIVE".equals(status)) status = "ENABLED";
    if (!"ENABLED".equals(status) && !"PAUSED".equals(status)) {
      throw new BadRequestException(
          "Google Performance Max asset group status must be ENABLED/ACTIVE or PAUSED");
    }

    String normalizedId = numeric(assetGroupId);
    Map<String, Object> update =
        Map.of(
            "resourceName",
            resource(
                context.account().getExternalAccountId(),
                "assetGroups",
                normalizedId),
            "status",
            status);

    apiClient.mutate(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "assetGroups",
        List.of(Map.of("update", update, "updateMask", "status")));

    GoogleAssetGroupResponse after =
        assetGroups(companyId, campaignId).stream()
            .filter(group -> normalizedId.equals(group.id()))
            .findFirst()
            .orElseThrow(
                () -> new NotFoundException(
                    "Google Performance Max asset group was updated but could not be reloaded"));

    activityLogService.record(
        companyId,
        campaign.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_ASSET_GROUP_STATUS_UPDATED",
        "ASSET_GROUP",
        normalizedId,
        null,
        after,
        Map.of("requestedStatus", status, "campaignId", campaign.getExternalCampaignId()));

    return after;
  }


  public GooglePerformanceMaxBrandGuidelinesResponse brandGuidelines(
      Long companyId, Long campaignId) {
    AdCampaign campaign = requirePerformanceMaxCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String externalCampaignId = numeric(campaign.getExternalCampaignId());

    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "SELECT campaign.id, campaign.brand_guidelines_enabled, "
                + "campaign.brand_guidelines.main_color, "
                + "campaign.brand_guidelines.accent_color, "
                + "campaign.brand_guidelines.predefined_font_family "
                + "FROM campaign WHERE campaign.id = "
                + externalCampaignId
                + " LIMIT 1");

    if (rows.isEmpty()) {
      throw new NotFoundException("Google Performance Max campaign could not be reloaded");
    }

    JsonNode providerCampaign = rows.getFirst().path("campaign");
    boolean enabled = providerCampaign.path("brandGuidelinesEnabled").asBoolean(false);
    JsonNode guidelines = providerCampaign.path("brandGuidelines");

    List<GooglePerformanceMaxBrandAssetResponse> brandAssets =
        enabled ? brandAssets(context, externalCampaignId) : List.of();

    return new GooglePerformanceMaxBrandGuidelinesResponse(
        enabled,
        text(guidelines, "mainColor"),
        text(guidelines, "accentColor"),
        text(guidelines, "predefinedFontFamily"),
        brandAssets,
        !enabled,
        false);
  }

  public GooglePerformanceMaxBrandGuidelinesResponse updateBrandGuidelines(
      Long companyId,
      Long campaignId,
      GooglePerformanceMaxBrandGuidelinesRequest request,
      Long actorUserId) {
    AdCampaign campaign = requirePerformanceMaxCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    GooglePerformanceMaxBrandGuidelinesResponse before =
        brandGuidelines(companyId, campaignId);

    String mainColor = normalizeBrandColor(request.mainColor());
    String accentColor = normalizeBrandColor(request.accentColor());
    String font = normalizeBrandFont(request.fontFamily());
    if ((mainColor == null) != (accentColor == null)) {
      throw new BadRequestException(
          "Performance Max main and accent brand colors must be provided together");
    }

    if (!before.enabled()) {
      if (!Boolean.TRUE.equals(request.enableBrandGuidelines())) {
        if (mainColor != null || accentColor != null || font != null) {
          throw new BadRequestException(
              "Enable Performance Max Brand Guidelines before setting colors or font");
        }
        return before;
      }

      boolean autoPopulate = request.autoPopulateBrandAssets() == null
          || request.autoPopulateBrandAssets();
      if (!autoPopulate) {
        throw new BadRequestException(
            "UnifyDesk currently enables existing Performance Max Brand Guidelines using Google's top-performing brand assets");
      }

      Map<String, Object> operation = new LinkedHashMap<>();
      operation.put(
          "campaign",
          resource(
              context.account().getExternalAccountId(),
              "campaigns",
              numeric(campaign.getExternalCampaignId())));
      operation.put("autoPopulateBrandAssets", true);
      if (mainColor != null) {
        operation.put("mainColor", mainColor);
        operation.put("accentColor", accentColor);
      }
      if (font != null) operation.put("fontFamily", font);

      apiClient.customerAction(
          context.accessToken(),
          context.account().getExternalAccountId(),
          context.loginCustomerId(),
          "campaigns:enablePMaxBrandGuidelines",
          Map.of("operations", List.of(operation)));
    } else {
      if (Boolean.FALSE.equals(request.enableBrandGuidelines())) {
        throw new BadRequestException(
            "Google does not allow disabling Brand Guidelines after they are enabled");
      }

      Map<String, Object> guidelineUpdate = new LinkedHashMap<>();
      List<String> masks = new ArrayList<>();
      if (mainColor != null) {
        guidelineUpdate.put("mainColor", mainColor);
        guidelineUpdate.put("accentColor", accentColor);
        masks.add("brand_guidelines.main_color");
        masks.add("brand_guidelines.accent_color");
      }
      if (font != null) {
        guidelineUpdate.put("predefinedFontFamily", font);
        masks.add("brand_guidelines.predefined_font_family");
      }

      if (!masks.isEmpty()) {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put(
            "resourceName",
            resource(
                context.account().getExternalAccountId(),
                "campaigns",
                numeric(campaign.getExternalCampaignId())));
        update.put("brandGuidelines", guidelineUpdate);
        apiClient.mutate(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "campaigns",
            List.of(
                Map.of(
                    "update", update,
                    "updateMask", String.join(",", masks))));
      }
    }

    GooglePerformanceMaxBrandGuidelinesResponse after =
        brandGuidelines(companyId, campaignId);
    activityLogService.record(
        companyId,
        campaign.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_PERFORMANCE_MAX_BRAND_GUIDELINES_UPDATED",
        "CAMPAIGN",
        campaign.getExternalCampaignId(),
        before,
        after,
        Map.of(
            "enabled", after.enabled(),
            "brandAssetCount", after.brandAssets().size()));
    return after;
  }

  public void validateBrandGuidelines(
      Boolean enabled,
      String mainColor,
      String accentColor,
      String fontFamily) {
    boolean effectiveEnabled = enabled == null || enabled;
    CreateGooglePerformanceMaxCampaignRequest probe =
        new CreateGooglePerformanceMaxCampaignRequest(
            "Brand validation",
            1L,
            "https://example.com",
            "Brand",
            List.of("One", "Two", "Three"),
            "Long headline",
            List.of("Short", "Second"),
            "placeholder",
            "placeholder",
            "placeholder",
            null,
            null,
            List.of(),
            "MAXIMIZE_CONVERSIONS",
            false,
            null,
            null,
            effectiveEnabled,
            mainColor,
            accentColor,
            fontFamily);
    brandGuidelines(probe, effectiveEnabled);
  }

  private List<GooglePerformanceMaxBrandAssetResponse> brandAssets(
      GoogleAdsAccountContextService.Context context,
      String externalCampaignId) {
    String query =
        "SELECT campaign_asset.resource_name, campaign_asset.asset, "
            + "campaign_asset.field_type, campaign_asset.status, "
            + "campaign_asset.primary_status, campaign_asset.primary_status_reasons, "
            + "asset.id, asset.name, asset.type, asset.text_asset.text, "
            + "asset.image_asset.full_size.url "
            + "FROM campaign_asset WHERE campaign.id = "
            + externalCampaignId
            + " AND campaign_asset.field_type IN ('BUSINESS_NAME','LOGO','LANDSCAPE_LOGO') "
            + "AND campaign_asset.status != 'REMOVED'";

    List<GooglePerformanceMaxBrandAssetResponse> result = new ArrayList<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode link = row.path("campaignAsset");
      JsonNode asset = row.path("asset");
      List<String> reasons = new ArrayList<>();
      JsonNode reasonValues = link.path("primaryStatusReasons");
      if (reasonValues.isArray()) {
        reasonValues.forEach(value -> reasons.add(value.asText()));
      }
      result.add(
          new GooglePerformanceMaxBrandAssetResponse(
              text(link, "resourceName"),
              text(link, "asset"),
              text(link, "fieldType"),
              text(link, "status"),
              text(link, "primaryStatus"),
              reasons,
              text(asset, "id"),
              text(asset, "name"),
              text(asset, "type"),
              text(asset.path("textAsset"), "text"),
              text(asset.path("imageAsset").path("fullSize"), "url")));
    }
    return List.copyOf(result);
  }

  public GooglePerformanceMaxAutomationResponse automation(
      Long companyId, Long campaignId) {
    AdCampaign campaign = requirePerformanceMaxCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String campaignIdExternal = numeric(campaign.getExternalCampaignId());

    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "SELECT campaign.id, campaign.asset_automation_settings "
                + "FROM campaign WHERE campaign.id = "
                + campaignIdExternal);

    Map<String, String> statuses = new LinkedHashMap<>();
    List<GoogleAutomatedVideoCrawlSource> crawlSources = new ArrayList<>();
    if (!rows.isEmpty()) {
      JsonNode settings = rows.getFirst().path("campaign").path("assetAutomationSettings");
      if (settings.isArray()) {
        for (JsonNode setting : settings) {
          String type = text(setting, "assetAutomationType");
          String status = text(setting, "assetAutomationStatus");
          if (type != null && status != null) statuses.put(type, status);
          if ("AUTOMATED_VIDEO_CRAWL".equals(type)) {
            JsonNode infos =
                setting.path("automatedVideoCrawlSetting").path("automatedVideoCrawlInfos");
            if (infos.isArray()) {
              for (JsonNode info : infos) {
                String url = text(info, "url");
                String platform = text(info, "sourcePlatform");
                if (url != null && platform != null) {
                  crawlSources.add(
                      new GoogleAutomatedVideoCrawlSource(
                          url, platform, info.path("enabled").asBoolean(true)));
                }
              }
            }
          }
        }
      }
    }

    return new GooglePerformanceMaxAutomationResponse(
        statuses, crawlSources, urlExclusions(context, campaignIdExternal));
  }

  public GooglePerformanceMaxAutomationResponse updateAutomation(
      Long companyId,
      Long campaignId,
      GooglePerformanceMaxAutomationRequest request,
      Long actorUserId) {
    AdCampaign campaign = requirePerformanceMaxCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    GooglePerformanceMaxAutomationResponse before = automation(companyId, campaignId);

    if (Boolean.TRUE.equals(request.finalUrlExpansion())
        && Boolean.FALSE.equals(request.textAssetAutomation())) {
      throw new BadRequestException(
          "Performance Max text asset automation cannot be opted out while Final URL Expansion is enabled");
    }

    List<Map<String, Object>> settings = new ArrayList<>();
    addAutomationSetting(
        settings,
        "FINAL_URL_EXPANSION_TEXT_ASSET_AUTOMATION",
        request.finalUrlExpansion(),
        null);
    addAutomationSetting(
        settings, "TEXT_ASSET_AUTOMATION", request.textAssetAutomation(), null);
    addAutomationSetting(
        settings,
        "GENERATE_ENHANCED_YOUTUBE_VIDEOS",
        request.enhancedYoutubeVideos(),
        null);
    addAutomationSetting(
        settings, "GENERATE_IMAGE_ENHANCEMENT", request.imageEnhancement(), null);

    Map<String, Object> videoCrawlSetting = null;
    if (request.automatedVideoCrawl() != null) {
      if (Boolean.TRUE.equals(request.automatedVideoCrawl())) {
        List<Map<String, Object>> infos =
            validateCrawlSources(request.automatedVideoCrawlSources()).stream()
                .map(
                    source ->
                        Map.<String, Object>of(
                            "url", source.url(),
                            "sourcePlatform", source.sourcePlatform(),
                            "enabled", source.enabled() == null || source.enabled()))
                .toList();
        videoCrawlSetting = Map.of("automatedVideoCrawlInfos", infos);
      }
      addAutomationSetting(
          settings, "AUTOMATED_VIDEO_CRAWL", request.automatedVideoCrawl(), videoCrawlSetting);
    }

    if (!settings.isEmpty()) {
      Map<String, Object> update = new LinkedHashMap<>();
      update.put(
          "resourceName",
          resource(
              context.account().getExternalAccountId(),
              "campaigns",
              numeric(campaign.getExternalCampaignId())));
      update.put("assetAutomationSettings", settings);
      apiClient.mutate(
          context.accessToken(),
          context.account().getExternalAccountId(),
          context.loginCustomerId(),
          "campaigns",
          List.of(
              Map.of(
                  "update", update,
                  "updateMask", "asset_automation_settings")));
    }

    if (request.urlExclusions() != null) {
      replaceUrlExclusions(
          context,
          numeric(campaign.getExternalCampaignId()),
          request.urlExclusions());
    }

    GooglePerformanceMaxAutomationResponse after = automation(companyId, campaignId);
    activityLogService.record(
        companyId,
        campaign.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_PERFORMANCE_MAX_AUTOMATION_UPDATED",
        "CAMPAIGN",
        campaign.getExternalCampaignId(),
        before,
        after,
        Map.of("campaignId", campaign.getExternalCampaignId()));
    return after;
  }

  public GoogleAssetGroupUrlOptionsResponse assetGroupUrlOptions(
      Long companyId, Long campaignId, String assetGroupId) {
    AdCampaign campaign = requirePerformanceMaxCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String normalizedAssetGroupId = numeric(assetGroupId);
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "SELECT asset_group.id, asset_group.tracking_url_template, "
                + "asset_group.final_url_suffix, asset_group.url_custom_parameters "
                + "FROM asset_group WHERE asset_group.id = "
                + normalizedAssetGroupId
                + " AND campaign.id = "
                + numeric(campaign.getExternalCampaignId()));
    if (rows.isEmpty()) {
      throw new NotFoundException("Google Performance Max asset group was not found");
    }
    JsonNode group = rows.getFirst().path("assetGroup");
    return new GoogleAssetGroupUrlOptionsResponse(
        normalizedAssetGroupId,
        text(group, "trackingUrlTemplate"),
        text(group, "finalUrlSuffix"),
        customParameters(group.path("urlCustomParameters")));
  }

  public GoogleAssetGroupUrlOptionsResponse updateAssetGroupUrlOptions(
      Long companyId,
      Long campaignId,
      String assetGroupId,
      GoogleUrlOptionsRequest request,
      Long actorUserId) {
    AdCampaign campaign = requirePerformanceMaxCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String normalizedAssetGroupId = numeric(assetGroupId);
    GoogleAssetGroupUrlOptionsResponse before =
        assetGroupUrlOptions(companyId, campaignId, normalizedAssetGroupId);

    Map<String, Object> update = new LinkedHashMap<>();
    update.put(
        "resourceName",
        resource(
            context.account().getExternalAccountId(), "assetGroups", normalizedAssetGroupId));
    List<String> masks = new ArrayList<>();

    if (request.trackingUrlTemplate() != null) {
      update.put("trackingUrlTemplate", request.trackingUrlTemplate().trim());
      masks.add("tracking_url_template");
    }
    if (request.finalUrlSuffix() != null) {
      update.put("finalUrlSuffix", request.finalUrlSuffix().trim());
      masks.add("final_url_suffix");
    }
    if (request.customParameters() != null) {
      List<GoogleUrlCustomParameter> params = validateCustomParameters(request.customParameters());
      update.put(
          "urlCustomParameters",
          params.stream()
              .map(item -> Map.of("key", item.key(), "value", item.value()))
              .toList());
      masks.add("url_custom_parameters");
    }
    if (masks.isEmpty()) {
      throw new BadRequestException("No Performance Max Asset Group URL option changes were supplied");
    }

    apiClient.mutate(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "assetGroups",
        List.of(
            Map.of(
                "update", update,
                "updateMask", String.join(",", masks))));

    GoogleAssetGroupUrlOptionsResponse after =
        assetGroupUrlOptions(companyId, campaignId, normalizedAssetGroupId);
    activityLogService.record(
        companyId,
        campaign.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_ASSET_GROUP_URL_OPTIONS_UPDATED",
        "ASSET_GROUP",
        normalizedAssetGroupId,
        before,
        after,
        Map.of("updatedFields", masks));
    return after;
  }

  private void addAutomationSetting(
      List<Map<String, Object>> settings,
      String type,
      Boolean enabled,
      Map<String, Object> specificSetting) {
    if (enabled == null) return;
    Map<String, Object> setting = new LinkedHashMap<>();
    setting.put("assetAutomationType", type);
    setting.put("assetAutomationStatus", enabled ? "OPTED_IN" : "OPTED_OUT");
    if (specificSetting != null) {
      setting.put("automatedVideoCrawlSetting", specificSetting);
    }
    settings.add(setting);
  }

  private List<GoogleAutomatedVideoCrawlSource> validateCrawlSources(
      List<GoogleAutomatedVideoCrawlSource> sources) {
    if (sources == null || sources.isEmpty()) {
      throw new BadRequestException(
          "At least one approved source is required when automated video crawl is enabled");
    }
    Set<String> allowedPlatforms = Set.of("LANDING_PAGE", "SOCIAL", "YOUTUBE");
    List<GoogleAutomatedVideoCrawlSource> result = new ArrayList<>();
    Set<String> dedupe = new LinkedHashSet<>();
    for (GoogleAutomatedVideoCrawlSource source : sources) {
      if (source == null) {
        throw new BadRequestException("Automated video crawl source cannot be empty");
      }
      validateUrl(source.url());
      String platform =
          source.sourcePlatform() == null
              ? ""
              : source.sourcePlatform().trim().toUpperCase(Locale.ROOT);
      if (!allowedPlatforms.contains(platform)) {
        throw new BadRequestException(
            "Automated video crawl source platform must be LANDING_PAGE, SOCIAL, or YOUTUBE");
      }
      String url = source.url().trim();
      String key = platform + "|" + url;
      if (dedupe.add(key)) {
        result.add(
            new GoogleAutomatedVideoCrawlSource(url, platform, source.enabled()));
      }
    }
    return List.copyOf(result);
  }

  private List<String> urlExclusions(
      GoogleAdsAccountContextService.Context context, String campaignId) {
    String query =
        "SELECT campaign_criterion.resource_name, campaign_criterion.webpage.criterion_name, "
            + "campaign_criterion.webpage.conditions, campaign_criterion.negative "
            + "FROM campaign_criterion WHERE campaign.id = "
            + campaignId
            + " AND campaign_criterion.type = 'WEBPAGE' "
            + "AND campaign_criterion.negative = TRUE";
    List<String> result = new ArrayList<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode conditions = row.path("campaignCriterion").path("webpage").path("conditions");
      if (!conditions.isArray()) continue;
      for (JsonNode condition : conditions) {
        if ("URL".equals(text(condition, "operand"))) {
          String argument = text(condition, "argument");
          if (argument != null) result.add(argument);
        }
      }
    }
    return result.stream().distinct().sorted().toList();
  }

  private void replaceUrlExclusions(
      GoogleAdsAccountContextService.Context context,
      String campaignId,
      List<String> requested) {
    LinkedHashSet<String> desired = new LinkedHashSet<>();
    for (String value : requested) {
      if (value == null || value.isBlank()) continue;
      String normalized = value.trim();
      if (normalized.length() > 2048) {
        throw new BadRequestException("Performance Max URL exclusion is too long");
      }
      desired.add(normalized);
    }

    String query =
        "SELECT campaign_criterion.resource_name, campaign_criterion.webpage.conditions, "
            + "campaign_criterion.negative FROM campaign_criterion WHERE campaign.id = "
            + campaignId
            + " AND campaign_criterion.type = 'WEBPAGE' "
            + "AND campaign_criterion.negative = TRUE";
    Map<String, String> existing = new LinkedHashMap<>();
    Map<String, String> managedExisting = new LinkedHashMap<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode criterion = row.path("campaignCriterion");
      JsonNode conditions = criterion.path("webpage").path("conditions");
      if (!conditions.isArray() || conditions.size() != 1) continue;
      JsonNode condition = conditions.get(0);
      if (!"URL".equals(text(condition, "operand"))) continue;
      String argument = text(condition, "argument");
      String resourceName = text(criterion, "resourceName");
      String criterionName = text(criterion.path("webpage"), "criterionName");
      if (argument != null && resourceName != null) {
        existing.put(argument, resourceName);
        if (criterionName != null
            && criterionName.startsWith("UnifyDesk URL exclusion ")) {
          managedExisting.put(argument, resourceName);
        }
      }
    }

    List<Map<String, Object>> operations = new ArrayList<>();
    for (Map.Entry<String, String> entry : managedExisting.entrySet()) {
      if (!desired.contains(entry.getKey())) {
        operations.add(Map.of("remove", entry.getValue()));
      }
    }
    String campaignResource =
        resource(context.account().getExternalAccountId(), "campaigns", campaignId);
    int index = 1;
    for (String value : desired) {
      if (existing.containsKey(value)) continue;
      Map<String, Object> webpage =
          Map.of(
              "criterionName", "UnifyDesk URL exclusion " + index++,
              "conditions",
                  List.of(
                      Map.of(
                          "operand", "URL",
                          "argument", value)));
      operations.add(
          Map.of(
              "create",
              Map.of(
                  "campaign", campaignResource,
                  "negative", true,
                  "webpage", webpage)));
    }
    if (!operations.isEmpty()) {
      apiClient.mutate(
          context.accessToken(),
          context.account().getExternalAccountId(),
          context.loginCustomerId(),
          "campaignCriteria",
          operations);
    }
  }

  private List<GoogleUrlCustomParameter> validateCustomParameters(
      List<GoogleUrlCustomParameter> values) {
    if (values == null) return List.of();
    if (values.size() > 8) {
      throw new BadRequestException("Google URL options support at most 8 custom parameters");
    }
    Set<String> keys = new LinkedHashSet<>();
    List<GoogleUrlCustomParameter> result = new ArrayList<>();
    for (GoogleUrlCustomParameter item : values) {
      if (item == null || item.key() == null || item.key().isBlank()
          || item.value() == null || item.value().isBlank()) {
        throw new BadRequestException("Google custom parameter key and value are required");
      }
      String key = item.key().trim();
      String value = item.value().trim();
      if (!key.matches("[A-Za-z0-9]+")) {
        throw new BadRequestException(
            "Google custom parameter keys may contain only letters and numbers");
      }
      if (!keys.add(key.toLowerCase(Locale.ROOT))) {
        throw new BadRequestException(
            "Google custom parameter keys must be unique ignoring case");
      }
      result.add(new GoogleUrlCustomParameter(key, value));
    }
    return List.copyOf(result);
  }

  private List<GoogleUrlCustomParameter> customParameters(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    List<GoogleUrlCustomParameter> result = new ArrayList<>();
    for (JsonNode item : node) {
      String key = text(item, "key");
      String value = text(item, "value");
      if (key != null && value != null) {
        result.add(new GoogleUrlCustomParameter(key, value));
      }
    }
    return List.copyOf(result);
  }

  private AdCampaign requirePerformanceMaxCampaign(Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS || !campaign.isAccessible()) {
      throw new BadRequestException("Google Ads campaign is not available");
    }
    if (!"PERFORMANCE_MAX".equalsIgnoreCase(campaign.getObjective())) {
      throw new BadRequestException("Campaign is not a Google Performance Max campaign");
    }
    return campaign;
  }

  private Map<String, Object> brandGuidelines(
      CreateGooglePerformanceMaxCampaignRequest request,
      boolean enabled) {
    String mainColor = normalizeBrandColor(request.brandMainColor());
    String accentColor = normalizeBrandColor(request.brandAccentColor());
    String font = normalizeBrandFont(request.brandFontFamily());

    if (!enabled && (mainColor != null || accentColor != null || font != null)) {
      throw new BadRequestException(
          "Performance Max brand colors and font require Brand Guidelines to be enabled");
    }
    if ((mainColor == null) != (accentColor == null)) {
      throw new BadRequestException(
          "Performance Max main and accent brand colors must be provided together");
    }

    Map<String, Object> result = new LinkedHashMap<>();
    if (mainColor != null) {
      result.put("mainColor", mainColor);
      result.put("accentColor", accentColor);
    }
    if (font != null) result.put("predefinedFontFamily", font);
    return result;
  }

  private String normalizeBrandColor(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (!normalized.matches("#[0-9A-Fa-f]{6}")) {
      throw new BadRequestException(
          "Performance Max brand colors must use 6-digit hex format such as #3366FF");
    }
    return normalized;
  }

  private String normalizeBrandFont(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (!BRAND_FONTS.contains(normalized)) {
      throw new BadRequestException(
          "Performance Max brand font must be one of: "
              + String.join(", ", BRAND_FONTS));
    }
    return normalized;
  }

  private void addCampaignAsset(
      List<Map<String, Object>> operations,
      String campaignResource,
      AssetLink asset) {
    operations.add(
        operation(
            "campaignAssetOperation",
            Map.of(
                "campaign", campaignResource,
                "asset", asset.resourceName(),
                "fieldType", asset.fieldType())));
  }

  private Map<String, Object> operation(String operationName, Map<String, Object> create) {
    return Map.of(operationName, Map.of("create", create));
  }

  private AssetLink textAsset(
      List<Map<String, Object>> operations,
      String customerId,
      int temporaryId,
      String name,
      String text,
      String fieldType) {
    String resourceName = resource(customerId, "assets", String.valueOf(temporaryId));
    Map<String, Object> asset = new LinkedHashMap<>();
    asset.put("resourceName", resourceName);
    asset.put("name", truncate(name, 128));
    asset.put("textAsset", Map.of("text", text.trim()));
    operations.add(operation("assetOperation", asset));
    return new AssetLink(resourceName, fieldType);
  }

  private AssetLink imageAsset(
      List<Map<String, Object>> operations,
      String customerId,
      int temporaryId,
      String name,
      String encoded,
      String fieldType,
      ImageRule rule) {
    byte[] data = decodeImage(encoded);
    validateImage(data, rule);
    String resourceName = resource(customerId, "assets", String.valueOf(temporaryId));
    Map<String, Object> asset = new LinkedHashMap<>();
    asset.put("resourceName", resourceName);
    asset.put("name", name);
    asset.put("imageAsset", Map.of("data", Base64.getEncoder().encodeToString(data)));
    operations.add(operation("assetOperation", asset));
    return new AssetLink(resourceName, fieldType);
  }

  private AssetLink youtubeAsset(
      List<Map<String, Object>> operations,
      String customerId,
      int temporaryId,
      String name,
      String youtubeVideoId) {
    String resourceName =
        resource(customerId, "assets", String.valueOf(temporaryId));
    Map<String, Object> asset = new LinkedHashMap<>();
    asset.put("resourceName", resourceName);
    asset.put("name", truncate(name, 128));
    asset.put("youtubeVideoAsset", Map.of("youtubeVideoId", youtubeVideoId));
    operations.add(operation("assetOperation", asset));
    return new AssetLink(resourceName, "YOUTUBE_VIDEO");
  }

  private void validateYouTubeId(String value) {
    if (value == null || !value.trim().matches("[A-Za-z0-9_-]{6,32}")) {
      throw new BadRequestException("A valid YouTube video ID is required");
    }
  }

  private String politicalAdvertisingStatus(Boolean containsEuPoliticalAdvertising) {
    if (containsEuPoliticalAdvertising == null) {
      throw new BadRequestException(
          "Choose whether this campaign contains EU political advertising");
    }
    return containsEuPoliticalAdvertising
        ? "CONTAINS_EU_POLITICAL_ADVERTISING"
        : "DOES_NOT_CONTAIN_EU_POLITICAL_ADVERTISING";
  }

  private String startDateTime(LocalDate date) {
    return LocalDateTime.of(date, LocalTime.MIDNIGHT).format(GOOGLE_DATE_TIME);
  }

  private String endDateTime(LocalDate date) {
    return LocalDateTime.of(date, LocalTime.of(23, 59, 59)).format(GOOGLE_DATE_TIME);
  }

  private void validate(CreateGooglePerformanceMaxCampaignRequest request) {
    if (request.headlines() == null
        || request.headlines().size() < 3
        || request.headlines().size() > 15) {
      throw new BadRequestException("Performance Max requires 3 to 15 headlines");
    }
    request.headlines().forEach(
        value -> validateText(value, 30, "Performance Max headline"));

    validateText(request.longHeadline(), 90, "Performance Max long headline");

    if (request.descriptions() == null
        || request.descriptions().size() < 2
        || request.descriptions().size() > 5) {
      throw new BadRequestException("Performance Max requires 2 to 5 descriptions");
    }
    request.descriptions().forEach(
        value -> validateText(value, 90, "Performance Max description"));
    if (request.descriptions().stream().noneMatch(value -> value != null && value.trim().length() <= 60)) {
      throw new BadRequestException(
          "Performance Max requires at least one description of 60 characters or fewer");
    }

    validateText(request.businessName(), 25, "Performance Max business name");
    validateUrl(request.finalUrl());

    if (request.startDate() != null
        && request.endDate() != null
        && request.endDate().isBefore(request.startDate())) {
      throw new BadRequestException(
          "Performance Max campaign end date must be on or after the start date");
    }
  }

  private void applyBidStrategy(Map<String, Object> campaign, String requested) {
    String strategy =
        requested == null || requested.isBlank()
            ? "MAXIMIZE_CONVERSIONS"
            : requested.trim().toUpperCase();
    switch (strategy) {
      case "MAXIMIZE_CONVERSIONS" -> campaign.put("maximizeConversions", Map.of());
      case "MAXIMIZE_CONVERSION_VALUE" -> campaign.put("maximizeConversionValue", Map.of());
      default ->
          throw new BadRequestException(
              "Performance Max bid strategy must be MAXIMIZE_CONVERSIONS or MAXIMIZE_CONVERSION_VALUE");
    }
  }

  private void validateText(String value, int max, String label) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException(label + " is required");
    }
    if (value.trim().length() > max) {
      throw new BadRequestException(label + " must be at most " + max + " characters");
    }
  }

  private void validateUrl(String value) {
    try {
      URI uri = URI.create(value.trim());
      if (!"http".equalsIgnoreCase(uri.getScheme())
          && !"https".equalsIgnoreCase(uri.getScheme())) {
        throw new IllegalArgumentException();
      }
    } catch (RuntimeException exception) {
      throw new BadRequestException(
          "Performance Max final URL must be a valid HTTP or HTTPS URL");
    }
  }

  private byte[] decodeImage(String encoded) {
    if (encoded == null || encoded.isBlank()) {
      throw new BadRequestException("Performance Max image data is required");
    }
    String value = encoded.trim();
    int comma = value.indexOf(',');
    if (value.startsWith("data:") && comma >= 0) value = value.substring(comma + 1);
    try {
      byte[] data = Base64.getDecoder().decode(value);
      if (data.length == 0 || data.length > MAX_IMAGE_BYTES) {
        throw new BadRequestException(
            "Performance Max images must be non-empty and at most 5 MB");
      }
      return data;
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Performance Max image data is not valid base64");
    }
  }

  private void validateImage(byte[] data, ImageRule rule) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
      if (image == null) {
        throw new BadRequestException("Performance Max image must be PNG, JPEG, or GIF");
      }
      int width = image.getWidth();
      int height = image.getHeight();
      if (width < rule.minWidth || height < rule.minHeight) {
        throw new BadRequestException(
            rule.label
                + " must be at least "
                + rule.minWidth
                + "x"
                + rule.minHeight
                + " pixels");
      }
      double ratio = (double) width / (double) height;
      if (Math.abs(ratio - rule.ratio) > rule.tolerance) {
        throw new BadRequestException(
            rule.label + " has an invalid aspect ratio");
      }
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("Unable to read Performance Max image");
    }
  }

  private GoogleAssetResponse asset(JsonNode row) {
    JsonNode asset = row.path("asset");
    JsonNode fullSize = asset.path("imageAsset").path("fullSize");
    return new GoogleAssetResponse(
        text(asset, "id"),
        text(asset, "resourceName"),
        text(asset, "name"),
        text(asset, "type"),
        text(asset.path("textAsset"), "text"),
        text(fullSize, "url"),
        longOrNull(fullSize, "widthPixels"),
        longOrNull(fullSize, "heightPixels"),
        text(asset.path("youtubeVideoAsset"), "youtubeVideoId"));
  }

  private GoogleAssetGroupResponse assetGroup(JsonNode row) {
    JsonNode group = row.path("assetGroup");
    List<String> finalUrls = new ArrayList<>();
    JsonNode urls = group.path("finalUrls");
    if (urls.isArray()) urls.forEach(value -> finalUrls.add(value.asText()));
    return new GoogleAssetGroupResponse(
        text(group, "id"),
        text(group, "resourceName"),
        text(group, "name"),
        text(group, "status"),
        text(group, "primaryStatus"),
        text(group, "adStrength"),
        List.copyOf(finalUrls));
  }

  private Long longOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    if (value.isIntegralNumber()) return value.longValue();
    try {
      return new BigDecimal(value.asText()).longValue();
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || value.asText().isBlank()) return null;
    return value.asText().trim();
  }

  private String suffix(String resourceName, String label) {
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException(
          "Google Ads did not return the created " + label + " resource name");
    }
    int slash = resourceName.lastIndexOf('/');
    if (slash < 0 || slash == resourceName.length() - 1) {
      throw new BadRequestException("Invalid Google Ads " + label + " resource name");
    }
    return resourceName.substring(slash + 1);
  }

  private String numeric(String value) {
    if (value == null || !value.matches("\\d+")) {
      throw new BadRequestException("Invalid Google Ads campaign ID");
    }
    return value;
  }

  private String generatedName(String base, String suffix) {
    return truncate(base.trim() + " " + suffix, 128);
  }

  private String truncate(String value, int max) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.length() <= max ? normalized : normalized.substring(0, max);
  }

  private String resource(String customerId, String collection, String id) {
    return "customers/" + customerId + "/" + collection + "/" + id;
  }

  private record AssetLink(String resourceName, String fieldType) {}

  private enum ImageRule {
    LANDSCAPE_MARKETING("Landscape marketing image", 600, 314, 1.91d, 0.04d),
    SQUARE_MARKETING("Square marketing image", 300, 300, 1.0d, 0.02d),
    LOGO("Square logo", 128, 128, 1.0d, 0.02d),
    PORTRAIT_MARKETING("Portrait marketing image", 480, 600, 0.8d, 0.02d),
    LANDSCAPE_LOGO("Landscape logo", 512, 128, 4.0d, 0.08d);

    final String label;
    final int minWidth;
    final int minHeight;
    final double ratio;
    final double tolerance;

    ImageRule(
        String label,
        int minWidth,
        int minHeight,
        double ratio,
        double tolerance) {
      this.label = label;
      this.minWidth = minWidth;
      this.minHeight = minHeight;
      this.ratio = ratio;
      this.tolerance = tolerance;
    }
  }
}
