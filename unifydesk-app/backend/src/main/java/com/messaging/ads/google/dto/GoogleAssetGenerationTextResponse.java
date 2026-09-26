package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleAssetGenerationTextResponse(
    List<GoogleGeneratedTextAsset> suggestions) {

  public GoogleAssetGenerationTextResponse {
    suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
  }
}
