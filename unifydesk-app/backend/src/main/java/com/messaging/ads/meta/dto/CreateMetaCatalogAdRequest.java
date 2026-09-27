package com.messaging.ads.meta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMetaCatalogAdRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank String pageId,
    String instagramActorId,
    @NotBlank String productSetId,
    @NotBlank String destinationUrl,
    String headline,
    String callToActionType,
    Boolean backgroundGeneration) {}
