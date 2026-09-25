package com.messaging.ads.dto;

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
        creative.getProviderCreatedAt(),
        creative.getProviderUpdatedAt(),
        creative.isAccessible(),
        creative.getLastSyncAt());
  }
}
