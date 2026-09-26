package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotNull;

public record GoogleAssetLinkRequest(
    @NotNull Level level,
    Long targetId,
    String fieldType) {

  public GoogleAssetLinkRequest(Level level, Long targetId) {
    this(level, targetId, null);
  }

  public enum Level {
    CUSTOMER,
    CAMPAIGN,
    AD_GROUP
  }
}
