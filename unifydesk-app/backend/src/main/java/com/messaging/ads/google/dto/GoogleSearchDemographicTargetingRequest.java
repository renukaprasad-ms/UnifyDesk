package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleSearchDemographicTargetingRequest(
    List<String> parentalStatusIncludes,
    List<String> parentalStatusExcludes,
    List<String> incomeRangeIncludes,
    List<String> incomeRangeExcludes,
    List<Long> detailedDemographicIds,
    List<Long> excludedDetailedDemographicIds) {

  public GoogleSearchDemographicTargetingRequest {
    parentalStatusIncludes =
        parentalStatusIncludes == null ? List.of() : List.copyOf(parentalStatusIncludes);
    parentalStatusExcludes =
        parentalStatusExcludes == null ? List.of() : List.copyOf(parentalStatusExcludes);
    incomeRangeIncludes =
        incomeRangeIncludes == null ? List.of() : List.copyOf(incomeRangeIncludes);
    incomeRangeExcludes =
        incomeRangeExcludes == null ? List.of() : List.copyOf(incomeRangeExcludes);
    detailedDemographicIds =
        detailedDemographicIds == null ? List.of() : List.copyOf(detailedDemographicIds);
    excludedDetailedDemographicIds =
        excludedDetailedDemographicIds == null
            ? List.of()
            : List.copyOf(excludedDetailedDemographicIds);
  }
}
