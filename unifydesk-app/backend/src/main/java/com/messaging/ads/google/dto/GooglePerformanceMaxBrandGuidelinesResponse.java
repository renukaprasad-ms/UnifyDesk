package com.messaging.ads.google.dto;

import java.util.List;

public record GooglePerformanceMaxBrandGuidelinesResponse(
    boolean enabled,
    String mainColor,
    String accentColor,
    String fontFamily,
    List<GooglePerformanceMaxBrandAssetResponse> brandAssets,
    boolean canEnable,
    boolean canDisable) {

  public GooglePerformanceMaxBrandGuidelinesResponse {
    brandAssets = brandAssets == null ? List.of() : List.copyOf(brandAssets);
  }
}
