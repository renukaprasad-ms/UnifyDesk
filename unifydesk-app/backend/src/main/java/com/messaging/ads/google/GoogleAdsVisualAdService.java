package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.dto.AdResponse;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleDemandGenVideoAdRequest;
import com.messaging.ads.google.dto.CreateGoogleResponsiveDisplayAdRequest;
import com.messaging.ads.google.dto.CreateGoogleShoppingProductAdRequest;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
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
public class GoogleAdsVisualAdService {

  private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

  private final AdSetRepository adSetRepository;
  private final AdCampaignRepository campaignRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsAdSyncService adSyncService;
  private final GoogleAdsAdPersistenceService adPersistenceService;
  private final AdActivityLogService activityLogService;

  public AdResponse createResponsiveDisplay(
      Long companyId,
      Long adGroupId,
      CreateGoogleResponsiveDisplayAdRequest request,
      Long actorUserId) {
    Context ctx = require(companyId, adGroupId, "DISPLAY");
    validateUrl(request.finalUrl());
    validateText(request.businessName(), 25, "Business name");
    validateText(request.longHeadline(), 90, "Long headline");
    validateTextList(request.headlines(), 1, 5, 30, "headline");
    validateTextList(request.descriptions(), 1, 5, 90, "description");

    String marketing =
        createImageAsset(
            ctx,
            request.name() + " landscape",
            request.marketingImageBase64(),
            ImageRule.LANDSCAPE_MARKETING);
    String square =
        createImageAsset(
            ctx,
            request.name() + " square",
            request.squareMarketingImageBase64(),
            ImageRule.SQUARE_MARKETING);
    String logo =
        request.squareLogoImageBase64() == null
                || request.squareLogoImageBase64().isBlank()
            ? null
            : createImageAsset(
                ctx,
                request.name() + " square logo",
                request.squareLogoImageBase64(),
                ImageRule.SQUARE_LOGO);

    Map<String, Object> responsive = new LinkedHashMap<>();
    responsive.put("marketingImages", List.of(Map.of("asset", marketing)));
    responsive.put("squareMarketingImages", List.of(Map.of("asset", square)));
    responsive.put(
        "headlines",
        request.headlines().stream()
            .map(value -> Map.<String, Object>of("text", value.trim()))
            .toList());
    responsive.put("longHeadline", Map.of("text", request.longHeadline().trim()));
    responsive.put(
        "descriptions",
        request.descriptions().stream()
            .map(value -> Map.<String, Object>of("text", value.trim()))
            .toList());
    responsive.put("businessName", request.businessName().trim());
    responsive.put("allowFlexibleColor", true);
    if (logo != null) {
      responsive.put("squareLogoImages", List.of(Map.of("asset", logo)));
    }

    Map<String, Object> ad = new LinkedHashMap<>();
    ad.put("name", request.name().trim());
    ad.put("finalUrls", List.of(request.finalUrl().trim()));
    ad.put("responsiveDisplayAd", responsive);

    return createAd(
        ctx,
        ad,
        actorUserId,
        "GOOGLE_RESPONSIVE_DISPLAY_AD_CREATED");
  }

  public AdResponse createDemandGenVideo(
      Long companyId,
      Long adGroupId,
      CreateGoogleDemandGenVideoAdRequest request,
      Long actorUserId) {
    Context ctx = require(companyId, adGroupId, "DEMAND_GEN");
    validateUrl(request.finalUrl());
    validateText(request.businessName(), 25, "Business name");
    validateTextList(request.headlines(), 1, 5, 40, "headline");
    validateTextList(request.longHeadlines(), 1, 5, 90, "long headline");
    validateTextList(request.descriptions(), 1, 5, 90, "description");

    String video = createYouTubeAsset(ctx, request.name(), request.youtubeVideoId());
    String logo =
        createImageAsset(
            ctx,
            request.name() + " logo",
            request.logoImageBase64(),
            ImageRule.SQUARE_LOGO);

    Map<String, Object> creative = new LinkedHashMap<>();
    creative.put("businessName", Map.of("text", request.businessName().trim()));
    creative.put("videos", List.of(Map.of("asset", video)));
    creative.put("logoImages", List.of(Map.of("asset", logo)));
    creative.put(
        "headlines",
        request.headlines().stream()
            .map(value -> Map.<String, Object>of("text", value.trim()))
            .toList());
    creative.put(
        "longHeadlines",
        request.longHeadlines().stream()
            .map(value -> Map.<String, Object>of("text", value.trim()))
            .toList());
    creative.put(
        "descriptions",
        request.descriptions().stream()
            .map(value -> Map.<String, Object>of("text", value.trim()))
            .toList());

    Map<String, Object> ad = new LinkedHashMap<>();
    ad.put("name", request.name().trim());
    ad.put("finalUrls", List.of(request.finalUrl().trim()));
    ad.put("demandGenVideoResponsiveAd", creative);

    return createAd(
        ctx,
        ad,
        actorUserId,
        "GOOGLE_DEMAND_GEN_VIDEO_AD_CREATED");
  }

  public AdResponse createShoppingProductAd(
      Long companyId,
      Long adGroupId,
      CreateGoogleShoppingProductAdRequest request,
      Long actorUserId) {
    Context ctx = require(companyId, adGroupId, "SHOPPING");
    Map<String, Object> ad = new LinkedHashMap<>();
    if (request.name() != null && !request.name().isBlank()) {
      ad.put("name", request.name().trim());
    }
    ad.put("shoppingProductAd", Map.of());

    return createAd(
        ctx,
        ad,
        actorUserId,
        "GOOGLE_SHOPPING_PRODUCT_AD_CREATED");
  }

  private AdResponse createAd(
      Context ctx,
      Map<String, Object> ad,
      Long actorUserId,
      String activityAction) {
    Map<String, Object> adGroupAd = new LinkedHashMap<>();
    adGroupAd.put(
        "adGroup",
        apiClient.customerResourceName(
            ctx.context().account().getExternalAccountId(),
            "adGroups",
            ctx.adGroup().getExternalAdSetId()));
    adGroupAd.put("status", "PAUSED");
    adGroupAd.put("ad", ad);

    JsonNode response =
        apiClient.mutate(
            ctx.context().accessToken(),
            ctx.context().account().getExternalAccountId(),
            ctx.context().loginCustomerId(),
            "adGroupAds",
            List.of(Map.of("create", adGroupAd)));

    String resourceName = apiClient.firstMutatedResourceName(response);
    String externalAdId = suffix(resourceName);
    JsonNode row =
        adSyncService.ad(
            ctx.context(), ctx.adGroup().getExternalAdSetId(), externalAdId);
    AdResponse created =
        AdResponse.from(
            adPersistenceService.persistOne(
                ctx.campaign().getCompanyId(), ctx.adGroup().getId(), row));

    activityLogService.record(
        ctx.campaign().getCompanyId(),
        ctx.adGroup().getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        activityAction,
        "AD",
        externalAdId,
        null,
        created,
        Map.of(
            "campaignChannel", ctx.campaign().getObjective(),
            "initialStatus", "PAUSED"));

    return created;
  }

  private String createImageAsset(
      Context ctx, String name, String encoded, ImageRule rule) {
    byte[] data = decodeImage(encoded);
    validateImage(data, rule);

    Map<String, Object> asset = new LinkedHashMap<>();
    asset.put("name", truncate(name, 128));
    asset.put(
        "imageAsset",
        Map.of("data", Base64.getEncoder().encodeToString(data)));

    JsonNode response =
        apiClient.mutate(
            ctx.context().accessToken(),
            ctx.context().account().getExternalAccountId(),
            ctx.context().loginCustomerId(),
            "assets",
            List.of(Map.of("create", asset)));
    String resourceName = apiClient.firstMutatedResourceName(response);
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException(
          "Google Ads did not return the created image asset");
    }
    return resourceName;
  }

  private String createYouTubeAsset(Context ctx, String name, String videoId) {
    if (videoId == null || !videoId.trim().matches("[A-Za-z0-9_-]{6,32}")) {
      throw new BadRequestException("A valid YouTube video ID is required");
    }

    Map<String, Object> asset = new LinkedHashMap<>();
    asset.put("name", truncate(name.trim() + " video", 128));
    asset.put(
        "youtubeVideoAsset",
        Map.of("youtubeVideoId", videoId.trim()));

    JsonNode response =
        apiClient.mutate(
            ctx.context().accessToken(),
            ctx.context().account().getExternalAccountId(),
            ctx.context().loginCustomerId(),
            "assets",
            List.of(Map.of("create", asset)));
    String resourceName = apiClient.firstMutatedResourceName(response);
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException(
          "Google Ads did not return the created YouTube video asset");
    }
    return resourceName;
  }

  private Context require(Long companyId, Long adGroupId, String requiredChannel) {
    AdSet adGroup =
        adSetRepository
            .findByIdAndCompanyId(adGroupId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising ad group not found"));

    if (adGroup.getProvider() != AdProvider.GOOGLE_ADS || !adGroup.isAccessible()) {
      throw new BadRequestException("Google Ads ad group is not available");
    }

    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(adGroup.getCampaignId(), companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));

    if (!requiredChannel.equalsIgnoreCase(campaign.getObjective())) {
      throw new BadRequestException(
          "This ad type requires a Google " + requiredChannel + " campaign");
    }

    return new Context(
        adGroup,
        campaign,
        contextService.requireManaged(companyId, adGroup.getAdAccountId()));
  }

  private void validateTextList(
      List<String> values,
      int min,
      int max,
      int maxLength,
      String label) {
    if (values == null || values.size() < min || values.size() > max) {
      throw new BadRequestException(
          "Google " + label + " count must be between " + min + " and " + max);
    }
    values.forEach(value -> validateText(value, maxLength, label));
  }

  private void validateText(String value, int max, String label) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException(label + " is required");
    }
    if (value.trim().length() > max) {
      throw new BadRequestException(
          label + " must be at most " + max + " characters");
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
          "Google ad final URL must be a valid HTTP or HTTPS URL");
    }
  }

  private byte[] decodeImage(String encoded) {
    if (encoded == null || encoded.isBlank()) {
      throw new BadRequestException("Google ad image data is required");
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
            "Google ad images must be non-empty and at most 5 MB");
      }
      return data;
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Google ad image data is not valid base64");
    }
  }

  private void validateImage(byte[] data, ImageRule rule) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
      if (image == null) {
        throw new BadRequestException(
            "Google ad image must be PNG, JPEG, or GIF");
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
      throw new BadRequestException("Unable to read Google ad image");
    }
  }

  private String suffix(String resourceName) {
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException("Google Ads did not return the created ad");
    }
    int slash = resourceName.lastIndexOf('/');
    if (slash < 0 || slash == resourceName.length() - 1) {
      throw new BadRequestException("Invalid Google Ads ad resource name");
    }
    String value = resourceName.substring(slash + 1);
    int separator = value.lastIndexOf('~');
    return separator >= 0 ? value.substring(separator + 1) : value;
  }

  private String truncate(String value, int max) {
    String normalized = value == null ? "" : value.trim();
    return normalized.length() <= max
        ? normalized
        : normalized.substring(0, max);
  }

  private record Context(
      AdSet adGroup,
      AdCampaign campaign,
      GoogleAdsAccountContextService.Context context) {}

  private enum ImageRule {
    LANDSCAPE_MARKETING("Landscape marketing image", 600, 314, 1.91d, 0.04d),
    SQUARE_MARKETING("Square marketing image", 300, 300, 1.0d, 0.02d),
    SQUARE_LOGO("Square logo", 128, 128, 1.0d, 0.02d);

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
