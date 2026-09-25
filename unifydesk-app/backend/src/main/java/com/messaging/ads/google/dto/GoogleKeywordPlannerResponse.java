package com.messaging.ads.google.dto;

import java.math.BigDecimal;
import java.util.List;

public record GoogleKeywordPlannerResponse(
    boolean cached,
    String currency,
    String network,
    String languageResource,
    List<String> geoTargetResources,
    List<Row> rows,
    String nextPageToken,
    long totalSize) {

  public GoogleKeywordPlannerResponse {
    geoTargetResources =
        geoTargetResources == null ? List.of() : List.copyOf(geoTargetResources);
    rows = rows == null ? List.of() : List.copyOf(rows);
  }

  public GoogleKeywordPlannerResponse asCached() {
    return new GoogleKeywordPlannerResponse(
        true,
        currency,
        network,
        languageResource,
        geoTargetResources,
        rows,
        nextPageToken,
        totalSize);
  }

  public record Row(
      String text,
      Long averageMonthlySearches,
      String competition,
      Long competitionIndex,
      BigDecimal lowTopOfPageBid,
      BigDecimal highTopOfPageBid,
      BigDecimal averageCpc,
      List<MonthlyVolume> monthlyVolumes,
      List<String> closeVariants,
      List<Concept> concepts) {

    public Row {
      monthlyVolumes = monthlyVolumes == null ? List.of() : List.copyOf(monthlyVolumes);
      closeVariants = closeVariants == null ? List.of() : List.copyOf(closeVariants);
      concepts = concepts == null ? List.of() : List.copyOf(concepts);
    }
  }

  public record MonthlyVolume(long year, String month, Long searches) {}

  public record Concept(String name, String groupName, String groupType) {}
}
