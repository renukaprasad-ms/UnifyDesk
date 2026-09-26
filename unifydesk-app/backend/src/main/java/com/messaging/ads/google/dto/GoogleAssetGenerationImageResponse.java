package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleAssetGenerationImageResponse(
    List<GoogleGeneratedImageAsset> suggestions) {

  public GoogleAssetGenerationImageResponse {
    suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
  }
}
