package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleAssetGroupAssetMutationRequest;
import com.messaging.ads.google.dto.GoogleAssetGroupAssetRemoveRequest;
import com.messaging.ads.google.dto.GoogleAssetGroupAssetResponse;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsAssetGroupAssetService {

  private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
  private static final Pattern ASSET_RESOURCE_PATTERN =
      Pattern.compile("^customers/(\\d+)/assets/(\\d+)$");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdCampaignRepository campaignRepository;
  private final AdActivityLogService activityLogService;

  public List<GoogleAssetGroupAssetResponse> list(
      Long companyId, Long campaignId, String assetGroupId) {
    Context context = context(companyId, campaignId, assetGroupId);

    String query =
        "SELECT asset_group_asset.resource_name, asset_group_asset.asset, "
            + "asset_group_asset.field_type, asset_group_asset.status, "
            + "asset_group_asset.primary_status, "
            + "asset_group_asset.primary_status_reasons, "
            + "asset_group_asset.source, "
            + "asset_group_asset.policy_summary.approval_status, "
            + "asset_group_asset.policy_summary.review_status, "
            + "asset.id, asset.resource_name, asset.name, asset.type, "
            + "asset.text_asset.text, asset.image_asset.full_size.url, "
            + "asset.image_asset.full_size.width_pixels, "
            + "asset.image_asset.full_size.height_pixels, "
            + "asset.youtube_video_asset.youtube_video_id "
            + "FROM asset_group_asset WHERE asset_group.id = "
            + context.assetGroupId()
            + " AND campaign.id = "
            + numeric(context.campaign().getExternalCampaignId(), "campaign ID");

    return apiClient
        .searchRows(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            query)
        .stream()
        .map(this::map)
        .filter(asset -> !"REMOVED".equals(asset.status()))
        .toList();
  }

  public List<GoogleAssetGroupAssetResponse> add(
      Long companyId,
      Long campaignId,
      String assetGroupId,
      GoogleAssetGroupAssetMutationRequest request,
      Long actorUserId) {
    Context context = context(companyId, campaignId, assetGroupId);
    AssetSpec spec = AssetSpec.from(request.fieldType());
    List<GoogleAssetGroupAssetResponse> before =
        list(companyId, campaignId, assetGroupId);

    long currentCount =
        before.stream()
            .filter(asset -> spec.name().equals(asset.fieldType()))
            .count();
    if (currentCount >= spec.maxCount) {
      throw new BadRequestException(
          "Performance Max "
              + spec.name()
              + " already has the maximum of "
              + spec.maxCount
              + " asset(s)");
    }

    String customerId = context.google().account().getExternalAccountId();
    String assetGroupResource =
        apiClient.customerResourceName(
            customerId, "assetGroups", context.assetGroupId());

    String existing = blank(request.existingAssetResourceName());
    if (existing != null) {
      validateExistingAsset(customerId, existing);

      apiClient.mutate(
          context.google().accessToken(),
          customerId,
          context.google().loginCustomerId(),
          "assetGroupAssets",
          List.of(
              Map.of(
                  "create",
                  Map.of(
                      "assetGroup", assetGroupResource,
                      "asset", existing,
                      "fieldType", spec.name(),
                      "status", "ENABLED"))));
    } else {
      String temporaryAsset =
          apiClient.customerResourceName(customerId, "assets", "-9001");

      Map<String, Object> asset = new java.util.LinkedHashMap<>();
      asset.put("resourceName", temporaryAsset);
      asset.put(
          "name",
          truncate(
              blank(request.name()) == null
                  ? "Reachlyo " + spec.name().toLowerCase().replace('_', ' ')
                  : request.name().trim(),
              128));
      spec.populateNewAsset(asset, request, this);

      List<Map<String, Object>> operations = new ArrayList<>();
      operations.add(Map.of("assetOperation", Map.of("create", asset)));
      operations.add(
          Map.of(
              "assetGroupAssetOperation",
              Map.of(
                  "create",
                  Map.of(
                      "assetGroup", assetGroupResource,
                      "asset", temporaryAsset,
                      "fieldType", spec.name(),
                      "status", "ENABLED"))));

      apiClient.mutateAll(
          context.google().accessToken(),
          customerId,
          context.google().loginCustomerId(),
          operations);
    }

    List<GoogleAssetGroupAssetResponse> after =
        list(companyId, campaignId, assetGroupId);

    activityLogService.record(
        companyId,
        context.campaign().getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_ASSET_GROUP_ASSET_ADDED",
        "ASSET_GROUP",
        context.assetGroupId(),
        before,
        after,
        Map.of(
            "fieldType", spec.name(),
            "reusedExistingAsset", existing != null,
            "campaignId", context.campaign().getExternalCampaignId()));

    return after;
  }

  public List<GoogleAssetGroupAssetResponse> remove(
      Long companyId,
      Long campaignId,
      String assetGroupId,
      GoogleAssetGroupAssetRemoveRequest request,
      Long actorUserId) {
    Context context = context(companyId, campaignId, assetGroupId);
    List<GoogleAssetGroupAssetResponse> before =
        list(companyId, campaignId, assetGroupId);

    GoogleAssetGroupAssetResponse target =
        before.stream()
            .filter(asset -> request.resourceName().trim().equals(asset.resourceName()))
            .findFirst()
            .orElseThrow(
                () -> new NotFoundException(
                    "Performance Max asset link was not found in this Asset Group"));

    AssetSpec spec = AssetSpec.from(target.fieldType());
    long currentCount =
        before.stream()
            .filter(asset -> spec.name().equals(asset.fieldType()))
            .count();

    if (currentCount <= spec.minCount) {
      throw new BadRequestException(
          "Cannot remove this "
              + spec.name()
              + " asset because Performance Max requires at least "
              + spec.minCount
              + " in the Asset Group");
    }

    validateLinkResource(
        context.google().account().getExternalAccountId(),
        context.assetGroupId(),
        request.resourceName());

    apiClient.mutate(
        context.google().accessToken(),
        context.google().account().getExternalAccountId(),
        context.google().loginCustomerId(),
        "assetGroupAssets",
        List.of(Map.of("remove", request.resourceName().trim())));

    List<GoogleAssetGroupAssetResponse> after =
        list(companyId, campaignId, assetGroupId);

    activityLogService.record(
        companyId,
        context.campaign().getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_ASSET_GROUP_ASSET_REMOVED",
        "ASSET_GROUP",
        context.assetGroupId(),
        before,
        after,
        Map.of(
            "fieldType", target.fieldType(),
            "assetResourceName", target.assetResourceName(),
            "campaignId", context.campaign().getExternalCampaignId()));

    return after;
  }

  private Context context(Long companyId, Long campaignId, String assetGroupId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));

    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !campaign.isAccessible()
        || !"PERFORMANCE_MAX".equalsIgnoreCase(campaign.getObjective())) {
      throw new BadRequestException(
          "Campaign is not an accessible Google Performance Max campaign");
    }

    var google = contextService.requireManaged(companyId, campaign.getAdAccountId());
    return new Context(
        campaign, google, numeric(assetGroupId, "asset group ID"));
  }

  private GoogleAssetGroupAssetResponse map(JsonNode row) {
    JsonNode link = row.path("assetGroupAsset");
    JsonNode asset = row.path("asset");
    JsonNode fullSize = asset.path("imageAsset").path("fullSize");
    List<String> primaryStatusReasons = new ArrayList<>();
    JsonNode reasonValues = link.path("primaryStatusReasons");
    if (reasonValues.isArray()) {
      reasonValues.forEach(value -> primaryStatusReasons.add(value.asText()));
    }
    return new GoogleAssetGroupAssetResponse(
        text(link, "resourceName"),
        text(asset, "resourceName"),
        text(asset, "id"),
        text(asset, "name"),
        text(asset, "type"),
        text(link, "fieldType"),
        text(link, "status"),
        text(link, "primaryStatus"),
        List.copyOf(primaryStatusReasons),
        text(link, "source"),
        text(link.path("policySummary"), "approvalStatus"),
        text(link.path("policySummary"), "reviewStatus"),
        text(asset.path("textAsset"), "text"),
        text(fullSize, "url"),
        longOrNull(fullSize, "widthPixels"),
        longOrNull(fullSize, "heightPixels"),
        text(asset.path("youtubeVideoAsset"), "youtubeVideoId"));
  }

  private void validateExistingAsset(String customerId, String resourceName) {
    var matcher = ASSET_RESOURCE_PATTERN.matcher(resourceName);
    String normalizedCustomer = customerId.replace("-", "");
    if (!matcher.matches() || !normalizedCustomer.equals(matcher.group(1))) {
      throw new BadRequestException(
          "Existing Google asset must belong to the selected ad account");
    }
  }

  private void validateLinkResource(
      String customerId, String assetGroupId, String resourceName) {
    String prefix =
        "customers/"
            + customerId.replace("-", "")
            + "/assetGroupAssets/"
            + assetGroupId
            + "~";
    if (resourceName == null || !resourceName.trim().startsWith(prefix)) {
      throw new BadRequestException(
          "Performance Max asset link does not belong to the selected Asset Group");
    }
  }

  private byte[] decodeImage(String encoded) {
    if (encoded == null || encoded.isBlank()) {
      throw new BadRequestException("Image data is required");
    }
    String value = encoded.trim();
    int comma = value.indexOf(',');
    if (value.startsWith("data:") && comma >= 0) {
      value = value.substring(comma + 1);
    }
    try {
      byte[] data = Base64.getDecoder().decode(value);
      if (data.length == 0 || data.length > MAX_IMAGE_BYTES) {
        throw new BadRequestException(
            "Google image assets must be non-empty and at most 5 MB");
      }
      return data;
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Google image data is not valid base64");
    }
  }

  private void validateImage(byte[] data, ImageRule rule) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
      if (image == null) {
        throw new BadRequestException(
            "Google image asset must be PNG, JPEG, or GIF");
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
        throw new BadRequestException(rule.label + " has an invalid aspect ratio");
      }
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("Unable to read Google image asset");
    }
  }

  private void validateText(String value, int maxLength, String label) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException(label + " is required");
    }
    if (value.trim().length() > maxLength) {
      throw new BadRequestException(
          label + " must be at most " + maxLength + " characters");
    }
  }

  private void validateYouTubeId(String value) {
    if (value == null || !value.trim().matches("[A-Za-z0-9_-]{6,32}")) {
      throw new BadRequestException("A valid YouTube video ID is required");
    }
  }

  private String numeric(String value, String label) {
    if (value == null || !value.trim().matches("\\d+")) {
      throw new BadRequestException("Invalid Google " + label);
    }
    return value.trim();
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String truncate(String value, int max) {
    String normalized = value == null ? "" : value.trim();
    return normalized.length() <= max
        ? normalized
        : normalized.substring(0, max);
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || value.asText().isBlank()) return null;
    return value.asText().trim();
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

  private record Context(
      AdCampaign campaign,
      GoogleAdsAccountContextService.Context google,
      String assetGroupId) {}

  private enum AssetSpec {
    HEADLINE(Source.TEXT, 3, 15, 30, null),
    LONG_HEADLINE(Source.TEXT, 1, 5, 90, null),
    DESCRIPTION(Source.TEXT, 2, 5, 90, null),
    BUSINESS_NAME(Source.TEXT, 1, 1, 25, null),
    MARKETING_IMAGE(Source.IMAGE, 1, 20, 0, ImageRule.LANDSCAPE_MARKETING),
    SQUARE_MARKETING_IMAGE(Source.IMAGE, 1, 20, 0, ImageRule.SQUARE_MARKETING),
    PORTRAIT_MARKETING_IMAGE(Source.IMAGE, 0, 20, 0, ImageRule.PORTRAIT_MARKETING),
    LOGO(Source.IMAGE, 1, 5, 0, ImageRule.SQUARE_LOGO),
    LANDSCAPE_LOGO(Source.IMAGE, 0, 20, 0, ImageRule.LANDSCAPE_LOGO),
    YOUTUBE_VIDEO(Source.YOUTUBE, 0, 15, 0, null);

    final Source source;
    final int minCount;
    final int maxCount;
    final int maxTextLength;
    final ImageRule imageRule;

    AssetSpec(
        Source source,
        int minCount,
        int maxCount,
        int maxTextLength,
        ImageRule imageRule) {
      this.source = source;
      this.minCount = minCount;
      this.maxCount = maxCount;
      this.maxTextLength = maxTextLength;
      this.imageRule = imageRule;
    }

    static AssetSpec from(String value) {
      if (value == null || value.isBlank()) {
        throw new BadRequestException("Performance Max asset field type is required");
      }
      try {
        return AssetSpec.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException exception) {
        throw new BadRequestException(
            "Unsupported Performance Max asset field type: " + value);
      }
    }

    void populateNewAsset(
        Map<String, Object> asset,
        GoogleAssetGroupAssetMutationRequest request,
        GoogleAdsAssetGroupAssetService service) {
      switch (source) {
        case TEXT -> {
          service.validateText(
              request.text(), maxTextLength, name().replace('_', ' '));
          asset.put("textAsset", Map.of("text", request.text().trim()));
        }
        case IMAGE -> {
          byte[] data = service.decodeImage(request.imageBase64());
          service.validateImage(data, imageRule);
          asset.put(
              "imageAsset",
              Map.of("data", Base64.getEncoder().encodeToString(data)));
        }
        case YOUTUBE -> {
          service.validateYouTubeId(request.youtubeVideoId());
          asset.put(
              "youtubeVideoAsset",
              Map.of("youtubeVideoId", request.youtubeVideoId().trim()));
        }
      }
    }
  }

  private enum Source {
    TEXT,
    IMAGE,
    YOUTUBE
  }

  private enum ImageRule {
    LANDSCAPE_MARKETING(
        "Landscape marketing image", 600, 314, 1.91d, 0.04d),
    SQUARE_MARKETING("Square marketing image", 300, 300, 1.0d, 0.02d),
    PORTRAIT_MARKETING(
        "Portrait marketing image", 480, 600, 0.8d, 0.02d),
    SQUARE_LOGO("Square logo", 128, 128, 1.0d, 0.02d),
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
