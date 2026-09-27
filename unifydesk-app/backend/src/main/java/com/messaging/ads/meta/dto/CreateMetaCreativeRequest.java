package com.messaging.ads.meta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateMetaCreativeRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank String format,
    @NotBlank String pageId,
    String instagramActorId,
    String primaryText,
    String headline,
    String description,
    String callToActionType,
    String destinationUrl,
    String imageHash,
    String imageUrl,
    String videoId,
    String videoUrl,
    String urlTags,
    Boolean advantagePlusCreative,
    Boolean generativeText,
    Boolean imageExpansion,
    @Valid List<@Valid CarouselCard> carouselCards) {

  public CreateMetaCreativeRequest(
      String name,
      String format,
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
      String videoUrl,
      String urlTags,
      List<CarouselCard> carouselCards) {
    this(
        name,
        format,
        pageId,
        instagramActorId,
        primaryText,
        headline,
        description,
        callToActionType,
        destinationUrl,
        imageHash,
        imageUrl,
        videoId,
        videoUrl,
        urlTags,
        null,
        null,
        null,
        carouselCards);
  }

  public CreateMetaCreativeRequest(
      String name,
      String format,
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
      String videoUrl,
      String urlTags,
      Boolean advantagePlusCreative,
      List<CarouselCard> carouselCards) {
    this(
        name,
        format,
        pageId,
        instagramActorId,
        primaryText,
        headline,
        description,
        callToActionType,
        destinationUrl,
        imageHash,
        imageUrl,
        videoId,
        videoUrl,
        urlTags,
        advantagePlusCreative,
        null,
        null,
        carouselCards);
  }

  @AssertTrue(message = "Creative media does not match the selected format")
  public boolean hasValidMedia() {
    if (format == null) return false;
    return switch (format.trim().toUpperCase()) {
      case "SINGLE_IMAGE" -> hasText(imageHash) || hasText(imageUrl);
      case "VIDEO" -> hasText(videoId) || hasText(videoUrl);
      case "CAROUSEL" -> carouselCards != null && carouselCards.size() >= 2;
      default -> false;
    };
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public record CarouselCard(
      String headline,
      String description,
      String destinationUrl,
      String imageHash,
      String imageUrl,
      String callToActionType) {

    @AssertTrue(message = "Each carousel card requires an image hash or image URL")
    public boolean hasMedia() {
      return (imageHash != null && !imageHash.isBlank())
          || (imageUrl != null && !imageUrl.isBlank());
    }
  }
}
