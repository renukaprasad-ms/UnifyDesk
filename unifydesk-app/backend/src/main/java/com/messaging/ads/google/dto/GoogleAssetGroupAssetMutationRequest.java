package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleAssetGroupAssetMutationRequest(
    @NotBlank String fieldType,
    String existingAssetResourceName,
    String name,
    String text,
    String imageBase64,
    String youtubeVideoId) {}
