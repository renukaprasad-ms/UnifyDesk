package com.messaging.ads.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdCreative;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record AdCreativeResponse(
    String id,
    String companyId,
    String adAccountId,
    AdProvider provider,
    String externalCreativeId,
    String name,
    String creativeFormat,
    String pageId,
    String instagramActorId,
    String primaryText,
    String headline,
    String description,
    String callToActionType,
    String destinationUrl,
    String imageHash,
    String imageUrl,
    String videoId,
    String urlTags,
    String objectStorySpecJson,
    String carouselJson,
    Boolean advantagePlusCreative,
    Boolean generativeText,
    Boolean imageExpansion,
    String existingPostId,
    Instant providerCreatedAt,
    Instant providerUpdatedAt,
    boolean accessible,
    Instant lastSyncAt) {

  public static AdCreativeResponse from(AdCreative creative) {
    return new AdCreativeResponse(
        creative.getId().toString(),
        creative.getCompanyId().toString(),
        creative.getAdAccountId().toString(),
        creative.getProvider(),
        creative.getExternalCreativeId(),
        creative.getName(),
        creative.getCreativeFormat(),
        creative.getPageId(),
        creative.getInstagramActorId(),
        creative.getPrimaryText(),
        creative.getHeadline(),
        creative.getDescription(),
        creative.getCallToActionType(),
        creative.getDestinationUrl(),
        creative.getImageHash(),
        creative.getImageUrl(),
        creative.getVideoId(),
        creative.getUrlTags(),
        creative.getObjectStorySpecJson(),
        creative.getCarouselJson(),
        creativeFeatureEnabled(creative.getProviderPayloadJson(), "standard_enhancements"),
        creativeFeatureEnabled(creative.getProviderPayloadJson(), "text_generation"),
        creativeFeatureEnabled(creative.getProviderPayloadJson(), "image_uncrop"),
        providerText(creative.getProviderPayloadJson(), "objectStoryId", "object_story_id"),
        creative.getProviderCreatedAt(),
        creative.getProviderUpdatedAt(),
        creative.isAccessible(),
        creative.getLastSyncAt());
  }

  private static String providerText(
      String providerPayloadJson, String camelCaseField, String snakeCaseField) {
    if (providerPayloadJson == null || providerPayloadJson.isBlank()) return null;
    try {
      JsonNode root = new ObjectMapper().readTree(providerPayloadJson);
      JsonNode value = root.get(camelCaseField);
      if (value == null || value.isNull() || value.asText("").isBlank()) {
        value = root.get(snakeCaseField);
      }
      return value == null || value.isNull() || value.asText("").isBlank()
          ? null
          : value.asText();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Boolean creativeFeatureEnabled(
      String providerPayloadJson, String feature) {
    if (providerPayloadJson == null || providerPayloadJson.isBlank()) return null;
    try {
      JsonNode root = new ObjectMapper().readTree(providerPayloadJson);
      JsonNode status =
          root.path("degreesOfFreedomSpec")
              .path("creative_features_spec")
              .path(feature)
              .path("enroll_status");
      if (status.isMissingNode() || status.isNull() || status.asText("").isBlank()) {
        status =
            root.path("degrees_of_freedom_spec")
                .path("creative_features_spec")
                .path(feature)
                .path("enroll_status");
      }
      String value = status.asText("").trim().toUpperCase();
      if ("OPT_IN".equals(value)) return true;
      if ("OPT_OUT".equals(value)) return false;
      return null;
    } catch (Exception ignored) {
      return null;
    }
  }

}
