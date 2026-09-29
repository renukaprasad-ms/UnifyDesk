package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleDetailedDemographicCatalogResponse(
    List<GoogleDetailedDemographicOption> demographics) {
  public GoogleDetailedDemographicCatalogResponse {
    demographics = demographics == null ? List.of() : List.copyOf(demographics);
  }
}
