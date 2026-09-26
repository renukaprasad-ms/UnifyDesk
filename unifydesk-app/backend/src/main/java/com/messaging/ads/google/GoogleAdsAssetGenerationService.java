package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.capability.AdFeatureCapability;
import com.messaging.ads.capability.AdFeatureCapabilityService;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleAssetGenerationImageRequest;
import com.messaging.ads.google.dto.GoogleAssetGenerationImageResponse;
import com.messaging.ads.google.dto.GoogleAssetGenerationTextRequest;
import com.messaging.ads.google.dto.GoogleAssetGenerationTextResponse;
import com.messaging.ads.google.dto.GoogleGeneratedImageAsset;
import com.messaging.ads.google.dto.GoogleGeneratedTextAsset;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsAssetGenerationService {

  private static final Set<String> CHANNEL_TYPES =
      Set.of("SEARCH", "PERFORMANCE_MAX", "DISPLAY", "DEMAND_GEN");

  private static final Set<String> TEXT_FIELD_TYPES =
      Set.of("HEADLINE", "LONG_HEADLINE", "DESCRIPTION");

  private static final Set<String> IMAGE_FIELD_TYPES =
      Set.of(
          "MARKETING_IMAGE",
          "SQUARE_MARKETING_IMAGE",
          "PORTRAIT_MARKETING_IMAGE",
          "TALL_PORTRAIT_MARKETING_IMAGE");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdFeatureCapabilityService capabilityService;
  private final AdActivityLogService activityLogService;

  public GoogleAssetGenerationTextResponse generateText(
      Long companyId,
      Long accountId,
      GoogleAssetGenerationTextRequest request,
      Long actorUserId) {
    var context = requireEligibleContext(companyId, accountId);
    String channel = enumValue(request.advertisingChannelType(), CHANNEL_TYPES, "advertising channel");
    List<String> fieldTypes =
        normalizeEnums(request.assetFieldTypes(), TEXT_FIELD_TYPES, "text asset field types", true);

    validateHttpUrl(request.finalUrl(), "Final URL");
    validatePrompt(request.freeformPrompt());

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("customerId", context.account().getExternalAccountId());
    body.put("advertisingChannelType", channel);
    body.put("assetFieldTypes", fieldTypes);
    body.put("finalUrl", request.finalUrl().trim());
    if (hasText(request.freeformPrompt())) {
      body.put("freeformPrompt", request.freeformPrompt().trim());
    }
    if (request.keywords() != null) {
      List<String> keywords =
          request.keywords().stream()
              .filter(this::hasText)
              .map(String::trim)
              .distinct()
              .limit(100)
              .toList();
      if (!keywords.isEmpty()) body.put("keywords", keywords);
    }

    JsonNode response =
        apiClient.customerColonAction(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "generateText",
            body);

    List<GoogleGeneratedTextAsset> suggestions = new ArrayList<>();
    JsonNode generated = response.path("generatedTexts");
    if (generated.isArray()) {
      for (JsonNode item : generated) {
        String text = text(item, "text");
        String fieldType = text(item, "assetFieldType");
        if (text != null && fieldType != null) {
          suggestions.add(new GoogleGeneratedTextAsset(text, fieldType));
        }
      }
    }

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_AI_TEXT_GENERATED",
        "AD_ACCOUNT",
        accountId.toString(),
        null,
        Map.of("suggestionCount", suggestions.size()),
        Map.of("channel", channel, "fieldTypes", fieldTypes));

    return new GoogleAssetGenerationTextResponse(suggestions);
  }

  public GoogleAssetGenerationImageResponse generateImages(
      Long companyId,
      Long accountId,
      GoogleAssetGenerationImageRequest request,
      Long actorUserId) {
    var context = requireEligibleContext(companyId, accountId);
    String channel = enumValue(request.advertisingChannelType(), CHANNEL_TYPES, "advertising channel");
    List<String> fieldTypes =
        normalizeEnums(
            request.assetFieldTypes(), IMAGE_FIELD_TYPES, "image asset field types", false);

    String mode =
        request.generationMode() == null
            ? ""
            : request.generationMode().trim().toUpperCase(Locale.ROOT);
    if (!Set.of("FINAL_URL", "FREEFORM").contains(mode)) {
      throw new BadRequestException(
          "Google image generation mode must be FINAL_URL or FREEFORM");
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("customerId", context.account().getExternalAccountId());
    body.put("advertisingChannelType", channel);
    if (!fieldTypes.isEmpty()) body.put("assetFieldTypes", fieldTypes);

    if ("FINAL_URL".equals(mode)) {
      validateHttpUrl(request.finalUrl(), "Image generation final URL");
      body.put("finalUrlGeneration", Map.of("finalUrl", request.finalUrl().trim()));
    } else {
      validatePrompt(request.freeformPrompt());
      if (!hasText(request.freeformPrompt())) {
        throw new BadRequestException("Freeform image generation prompt is required");
      }
      body.put("freeformGeneration", Map.of("freeformPrompt", request.freeformPrompt().trim()));
    }

    JsonNode response =
        apiClient.customerColonAction(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "generateImages",
            body);

    List<GoogleGeneratedImageAsset> suggestions = new ArrayList<>();
    JsonNode generated = response.path("generatedImages");
    if (generated.isArray()) {
      for (JsonNode item : generated) {
        String fieldType = text(item, "assetFieldType");
        String url = text(item.path("image"), "fullSizeImageUrl");
        if (url == null) url = text(item, "imageTemporaryUrl");
        if (url != null) {
          suggestions.add(new GoogleGeneratedImageAsset(url, fieldType));
        }
      }
    }

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_AI_IMAGES_GENERATED",
        "AD_ACCOUNT",
        accountId.toString(),
        null,
        Map.of("suggestionCount", suggestions.size()),
        Map.of(
            "channel", channel,
            "fieldTypes", fieldTypes,
            "generationMode", mode));

    return new GoogleAssetGenerationImageResponse(suggestions);
  }

  private GoogleAdsAccountContextService.Context requireEligibleContext(
      Long companyId, Long accountId) {
    var capability =
        capabilityService.accountCapability(
            companyId, accountId, AdFeatureCapability.GOOGLE_ASSET_GENERATION);
    if (!capability.supported()) {
      throw new BadRequestException(
          capability.reason() == null
              ? "Google AI asset generation is not available for this account"
              : capability.reason());
    }
    var context = contextService.requireManaged(companyId, accountId);
    if (context.managerAccount()) {
      throw new BadRequestException(
          "Google AI asset generation must run against a managed advertising customer account");
    }
    return context;
  }

  private List<String> normalizeEnums(
      List<String> values, Set<String> allowed, String label, boolean required) {
    if (values == null) {
      if (required) throw new BadRequestException(label + " are required");
      return List.of();
    }
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (String value : values) {
      result.add(enumValue(value, allowed, label));
    }
    if (required && result.isEmpty()) {
      throw new BadRequestException(label + " are required");
    }
    return List.copyOf(result);
  }

  private String enumValue(String value, Set<String> allowed, String label) {
    if (!hasText(value)) throw new BadRequestException(label + " is required");
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!allowed.contains(normalized)) {
      throw new BadRequestException("Unsupported Google " + label + ": " + normalized);
    }
    return normalized;
  }

  private void validatePrompt(String prompt) {
    if (prompt == null) return;
    String normalized = prompt.trim();
    if (!normalized.isEmpty() && (normalized.length() < 1 || normalized.length() > 1500)) {
      throw new BadRequestException("Google AI prompt must be between 1 and 1500 characters");
    }
  }

  private void validateHttpUrl(String value, String label) {
    if (!hasText(value)) throw new BadRequestException(label + " is required");
    try {
      URI uri = URI.create(value.trim());
      if (!"http".equalsIgnoreCase(uri.getScheme())
          && !"https".equalsIgnoreCase(uri.getScheme())) {
        throw new IllegalArgumentException();
      }
      if (uri.getHost() == null) throw new IllegalArgumentException();
    } catch (RuntimeException exception) {
      throw new BadRequestException(label + " must be a valid HTTP or HTTPS URL");
    }
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isNull()) return null;
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    String result = value.asText(null);
    return hasText(result) ? result.trim() : null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
