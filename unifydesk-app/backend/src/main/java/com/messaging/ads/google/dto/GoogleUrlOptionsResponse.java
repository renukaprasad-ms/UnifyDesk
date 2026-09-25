package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleUrlOptionsResponse(
    String level,
    String targetId,
    String trackingUrlTemplate,
    String finalUrlSuffix,
    List<GoogleUrlCustomParameter> customParameters) {

  public GoogleUrlOptionsResponse {
    customParameters =
        customParameters == null ? List.of() : List.copyOf(customParameters);
  }
}
