package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleSearchInterestCatalogResponse(
    List<GoogleSearchInterestOption> interests,
    boolean customSegmentsSupported,
    String customSegmentsNote) {

  public GoogleSearchInterestCatalogResponse {
    interests = interests == null ? List.of() : List.copyOf(interests);
  }
}
