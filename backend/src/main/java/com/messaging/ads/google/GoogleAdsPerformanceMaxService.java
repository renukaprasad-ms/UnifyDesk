package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGooglePerformanceMaxCampaignRequest;
import com.messaging.ads.google.dto.GoogleAssetGroupResponse;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsPerformanceMaxService {

  private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

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

    String customerId = context.customerId();
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

    Map<String, Object> campaign = new LinkedHashMap<>();
    campaign.put("resourceName", campaignResource);
    campaign.put("name", request.name().trim());
    campaign.put("status", "PAUSED");
    campaign.put("advertisingChannelType", "PERFORMANCE_MAX");
    campaign.put("campaignBudget", budgetResource);
    campaign.put("brandGuidelinesEnabled", false);
    applyBidStrategy(campaign, request.bidStrategy());
    if (request.startDate() != null) campaign.put("startDate", request.startDate().toString());
    if (request.endDate() != null) campaign.put("endDate", request.endDate().toString());
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

    assets.add(
        textAsset(
            operations,
            customerId,
            assetId--,
            request.name() + " business name",
            request.businessName(),
            "BUSINESS_NAME"));

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

    assets.add(
        imageAsset(
            operations,
            customerId,
            assetId--,
            request.name() + " logo",
            request.logoImageBase64(),
            "LOGO",
            ImageRule.LOGO));

    Map<String, Object> assetGroup = new LinkedHashMap<>();
    assetGroup.put("resourceName", assetGroupResource);
    assetGroup.put("name", request.name().trim() + " asset group");
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
            "brandGuidelinesEnabled", false,
            "initialStatus", "PAUSED"));

    return created;
  }

  public List<GoogleAssetResponse> assets(Long companyId, Long accountId) {
    var context = contextService.requireManaged(companyId, accountId);
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.customerId(),
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
            context.customerId(),
            context.loginCustomerId(),
            query)
        .stream()
        .map(this::assetGroup)
        .toList();
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
    asset.put("name", name);
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

  private String resource(String customerId, String collection, String id) {
    return "customers/" + customerId + "/" + collection + "/" + id;
  }

  private record AssetLink(String resourceName, String fieldType) {}

  private enum ImageRule {
    LANDSCAPE_MARKETING("Landscape marketing image", 600, 314, 1.91d, 0.04d),
    SQUARE_MARKETING("Square marketing image", 300, 300, 1.0d, 0.02d),
    LOGO("Square logo", 128, 128, 1.0d, 0.02d);

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
