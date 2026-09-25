package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotNull;

public record GoogleAssetLinkRequest(
    @NotNull Level level,
    Long targetId) {

  public enum Level {
    CUSTOMER,
    CAMPAIGN,
    AD_GROUP
  }
}
