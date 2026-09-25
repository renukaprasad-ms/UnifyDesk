package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.dto.AdResponse;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleDemandGenCarouselAdRequest;
import com.messaging.ads.google.dto.CreateGoogleDemandGenMultiAssetAdRequest;
import com.messaging.ads.google.dto.GoogleDemandGenCarouselCardRequest;
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
public class GoogleAdsDemandGenCreativeService {

  private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

  private final AdSetRepository adSetRepository;
  private final AdCampaignRepository campaignRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsAdSyncService adSyncService;
  private final GoogleAdsAdPersistenceService adPersistenceService;
  private final AdActivityLogService activityLogService;

  public AdResponse createMultiAsset(
      Long companyId,
      Long adGroupId,
      CreateGoogleDemandGenMultiAssetAdRequest request,
      Long actorUserId) {
    Context context = requireDemandGen(companyId, adGroupId);
    validateUrl(request.finalUrl());
    validateText(request.businessName(), 25, "Business name");
    validateTextList(request.headlines(), 1, 5, 30, "headline");
    validateTextList(request.descriptions(), 1, 5, 90, "description");

    if (blank(request.marketingImageBase64()) == null
        && blank(request.squareMarketingImageBase64()) == null) {
      throw new BadRequestException(
          "Demand Gen Multi-Asset ads require a landscape or square marketing image");
    }

    String logo =
        createImageAsset(
            context,
            request.name() + " logo",
            request.logoImageBase64(),
            ImageRule.SQUARE_LOGO);

    Map<String, Object> creative = new LinkedHashMap<>();
    creative.put("businessName", request.businessName().trim());
    creative.put(
        "headlines",
        request.headlines().stream()
            .map(value -> Map.<String, Object>of("text", value.trim()))
            .toList());
    creative.put(
        "descriptions",
        request.descriptions().stream()
            .map(value -> Map.<String, Object>of("text", value.trim()))
            .toList());
    creative.put("logoImages", List.of(Map.of("asset", logo)));
    putTextIfPresent(creative, "callToActionText", request.callToActionText());

    List<Map<String, Object>> landscape = new ArrayList<>();
    List<Map<String, Object>> square = new ArrayList<>();
    List<Map<String, Object>> portrait = new ArrayList<>();
    List<Map<String, Object>> tallPortrait = new ArrayList<>();

    if (blank(request.marketingImageBase64()) != null) {
      landscape.add(
          Map.of(
              "asset",
              createImageAsset(
                  context,
                  request.name() + " landscape",
                  request.marketingImageBase64(),
                  ImageRule.LANDSCAPE_MARKETING)));
    }
    if (blank(request.squareMarketingImageBase64()) != null) {
      square.add(
          Map.of(
              "asset",
              createImageAsset(
                  context,
                  request.name() + " square",
                  request.squareMarketingImageBase64(),
                  ImageRule.SQUARE_MARKETING)));
    }
    if (blank(request.portraitMarketingImageBase64()) != null) {
      portrait.add(
          Map.of(
              "asset",
              createImageAsset(
                  context,
                  request.name() + " portrait",
                  request.portraitMarketingImageBase64(),
                  ImageRule.PORTRAIT_MARKETING)));
    }
    if (blank(request.tallPortraitMarketingImageBase64()) != null) {
      tallPortrait.add(
          Map.of(
              "asset",
              createImageAsset(
                  context,
                  request.name() + " tall portrait",
                  request.tallPortraitMarketingImageBase64(),
                  ImageRule.TALL_PORTRAIT_MARKETING)));
    }

    if (!landscape.isEmpty()) creative.put("marketingImages", landscape);
    if (!square.isEmpty()) creative.put("squareMarketingImages", square);
    if (!portrait.isEmpty()) creative.put("portraitMarketingImages", portrait);
    if (!tallPortrait.isEmpty()) {
      creative.put("tallPortraitMarketingImages", tallPortrait);
    }

    Map<String, Object> ad = new LinkedHashMap<>();
    ad.put("name", request.name().trim());
    ad.put("finalUrls", List.of(request.finalUrl().trim()));
    ad.put("demandGenMultiAssetAd", creative);

    return createAd(
        context,
        ad,
        actorUserId,
        "GOOGLE_DEMAND_GEN_MULTI_ASSET_AD_CREATED");
  }

  public AdResponse createCarousel(
      Long companyId,
      Long adGroupId,
      CreateGoogleDemandGenCarouselAdRequest request,
      Long actorUserId) {
    Context context = requireDemandGen(companyId, adGroupId);
    validateUrl(request.finalUrl());
    validateText(request.businessName(), 25, "Business name");
    validateText(request.headline(), 40, "Carousel headline");
    validateText(request.description(), 90, "Carousel description");

    if (request.cards() == null
        || request.cards().size() < 2
        || request.cards().size() > 10) {
      throw new BadRequestException(
          "Demand Gen carousel ads require between 2 and 10 cards");
    }

    String logo =
        createImageAsset(
            context,
            request.name() + " logo",
            request.logoImageBase64(),
            ImageRule.SQUARE_LOGO);

    List<String> cardAssets = new ArrayList<>();
    for (int i = 0; i < request.cards().size(); i++) {
      cardAssets.add(
          createCarouselCard(
              context,
              request.name(),
              request.cards().get(i),
              i + 1));
    }

    Map<String, Object> carousel = new LinkedHashMap<>();
    carousel.put("businessName", request.businessName().trim());
    carousel.put("headline", Map.of("text", request.headline().trim()));
    carousel.put("description", Map.of("text", request.description().trim()));
    carousel.put("logoImage", Map.of("asset", logo));
    carousel.put(
        "carouselCards",
        cardAssets.stream()
            .map(resource -> Map.<String, Object>of("asset", resource))
            .toList());
    putTextIfPresent(carousel, "callToActionText", request.callToActionText());

    Map<String, Object> ad = new LinkedHashMap<>();
    ad.put("name", request.name().trim());
    ad.put("finalUrls", List.of(request.finalUrl().trim()));
    ad.put("demandGenCarouselAd", carousel);

    return createAd(
        context,
        ad,
        actorUserId,
        "GOOGLE_DEMAND_GEN_CAROUSEL_AD_CREATED");
  }

  private String createCarouselCard(
      Context context,
      String adName,
      GoogleDemandGenCarouselCardRequest card,
      int index) {
    validateText(card.headline(), 40, "Carousel card headline");

    String landscape = null;
    String square = null;
    String portrait = null;

    if (blank(card.marketingImageBase64()) != null) {
      landscape =
          createImageAsset(
              context,
              adName + " card " + index + " landscape",
              card.marketingImageBase64(),
              ImageRule.LANDSCAPE_MARKETING);
    }
    if (blank(card.squareMarketingImageBase64()) != null) {
      square =
          createImageAsset(
              context,
              adName + " card " + index + " square",
              card.squareMarketingImageBase64(),
              ImageRule.SQUARE_MARKETING);
    }
    if (blank(card.portraitMarketingImageBase64()) != null) {
      portrait =
          createImageAsset(
              context,
              adName + " card " + index + " portrait",
              card.portraitMarketingImageBase64(),
              ImageRule.PORTRAIT_MARKETING);
    }
    if (landscape == null && square == null) {
      throw new BadRequestException(
          "Each Demand Gen carousel card requires a landscape or square image");
    }

    Map<String, Object> cardAsset = new LinkedHashMap<>();
    cardAsset.put("headline", card.headline().trim());
    putTextIfPresent(cardAsset, "callToActionText", card.callToActionText());
    if (landscape != null) cardAsset.put("marketingImageAsset", landscape);
    if (square != null) cardAsset.put("squareMarketingImageAsset", square);
    if (portrait != null) cardAsset.put("portraitMarketingImageAsset", portrait);

    Map<String, Object> asset = new LinkedHashMap<>();
    asset.put("name", truncate(adName + " carousel card " + index, 128));
    asset.put("demandGenCarouselCardAsset", cardAsset);

    JsonNode response =
        apiClient.mutate(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            "assets",
            List.of(Map.of("create", asset)));

    String resourceName = apiClient.firstMutatedResourceName(response);
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException(
          "Google Ads did not return the created Demand Gen carousel card asset");
    }
    return resourceName;
  }

  private AdResponse createAd(
      Context context,
      Map<String, Object> ad,
      Long actorUserId,
      String action) {
    Map<String, Object> adGroupAd = new LinkedHashMap<>();
    adGroupAd.put(
        "adGroup",
        apiClient.customerResourceName(
            context.google().account().getExternalAccountId(),
            "adGroups",
            context.adGroup().getExternalAdSetId()));
    adGroupAd.put("status", "PAUSED");
    adGroupAd.put("ad", ad);

    JsonNode response =
        apiClient.mutate(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            "adGroupAds",
            List.of(Map.of("create", adGroupAd)));

    String resourceName = apiClient.firstMutatedResourceName(response);
    String externalAdId = suffix(resourceName);

    JsonNode row =
        adSyncService.ad(
            context.google(),
            context.adGroup().getExternalAdSetId(),
            externalAdId);
    AdResponse created =
        AdResponse.from(
            adPersistenceService.persistOne(
                context.campaign().getCompanyId(),
                context.adGroup().getId(),
                row));

    activityLogService.record(
        context.campaign().getCompanyId(),
        context.adGroup().getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        action,
        "AD",
        externalAdId,
        null,
        created,
        Map.of(
            "campaignChannel", "DEMAND_GEN",
            "initialStatus", "PAUSED"));

    return created;
  }

  private String createImageAsset(
      Context context,
      String name,
      String encoded,
      ImageRule rule) {
    byte[] data = decodeImage(encoded);
    validateImage(data, rule);

    Map<String, Object> asset = new LinkedHashMap<>();
    asset.put("name", truncate(name, 128));
    asset.put(
        "imageAsset",
        Map.of("data", Base64.getEncoder().encodeToString(data)));

    JsonNode response =
        apiClient.mutate(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            "assets",
            List.of(Map.of("create", asset)));
    String resourceName = apiClient.firstMutatedResourceName(response);
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException(
          "Google Ads did not return the created image asset");
    }
    return resourceName;
  }

  private Context requireDemandGen(Long companyId, Long adGroupId) {
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
    if (!"DEMAND_GEN".equalsIgnoreCase(campaign.getObjective())) {
      throw new BadRequestException(
          "Demand Gen creatives require a Google Demand Gen campaign");
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
          "Demand Gen " + label + " count must be between " + min + " and " + max);
    }
    values.forEach(value -> validateText(value, maxLength, label));
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

  private void validateUrl(String value) {
    try {
      URI uri = URI.create(value.trim());
      if (!"http".equalsIgnoreCase(uri.getScheme())
          && !"https".equalsIgnoreCase(uri.getScheme())) {
        throw new IllegalArgumentException();
      }
    } catch (RuntimeException exception) {
      throw new BadRequestException(
          "Demand Gen final URL must be a valid HTTP or HTTPS URL");
    }
  }

  private byte[] decodeImage(String encoded) {
    if (encoded == null || encoded.isBlank()) {
      throw new BadRequestException("Demand Gen image data is required");
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
            "Demand Gen image assets must be non-empty and at most 5 MB");
      }
      return data;
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Demand Gen image data is not valid base64");
    }
  }

  private void validateImage(byte[] data, ImageRule rule) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
      if (image == null) {
        throw new BadRequestException(
            "Demand Gen image must be PNG, JPEG, or GIF");
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
      throw new BadRequestException("Unable to read Demand Gen image");
    }
  }

  private void putTextIfPresent(
      Map<String, Object> target, String key, String value) {
    if (value != null && !value.isBlank()) target.put(key, value.trim());
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
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
      GoogleAdsAccountContextService.Context google) {}

  private enum ImageRule {
    LANDSCAPE_MARKETING(
        "Landscape marketing image", 600, 314, 1.91d, 0.04d),
    SQUARE_MARKETING("Square marketing image", 300, 300, 1.0d, 0.02d),
    PORTRAIT_MARKETING(
        "Portrait marketing image", 480, 600, 0.8d, 0.02d),
    TALL_PORTRAIT_MARKETING(
        "Tall portrait marketing image", 600, 1067, 9.0d / 16.0d, 0.02d),
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
