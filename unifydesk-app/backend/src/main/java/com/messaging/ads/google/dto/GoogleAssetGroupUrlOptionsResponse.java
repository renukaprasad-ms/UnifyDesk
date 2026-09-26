package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleAssetGroupUrlOptionsResponse(
    String assetGroupId,
    String trackingUrlTemplate,
    String finalUrlSuffix,
    List<GoogleUrlCustomParameter> customParameters) {

  public GoogleAssetGroupUrlOptionsResponse {
    customParameters =
        customParameters == null ? List.of() : List.copyOf(customParameters);
  }
}
