package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleSearchInterestTargetingRequest(
    List<String> includedResourceNames,
    List<String> excludedResourceNames) {

  public GoogleSearchInterestTargetingRequest {
    includedResourceNames =
        includedResourceNames == null ? List.of() : List.copyOf(includedResourceNames);
    excludedResourceNames =
        excludedResourceNames == null ? List.of() : List.copyOf(excludedResourceNames);
  }
}
