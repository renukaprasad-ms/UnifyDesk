package com.messaging.ads.google.dto;

import java.util.List;

public record GooglePerformanceMaxListingGroupReplaceRequest(
    String dimensionType,
    List<GooglePerformanceMaxListingGroupValueRequest> values,
    boolean includeOtherProducts) {

  public GooglePerformanceMaxListingGroupReplaceRequest {
    values = values == null ? List.of() : List.copyOf(values);
  }
}
