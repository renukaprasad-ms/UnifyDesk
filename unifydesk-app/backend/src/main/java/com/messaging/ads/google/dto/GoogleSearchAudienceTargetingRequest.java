package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record GoogleSearchAudienceTargetingRequest(
    @NotBlank String mode,
    List<Long> includedAudienceIds,
    List<Long> excludedAudienceIds) {

  public GoogleSearchAudienceTargetingRequest {
    includedAudienceIds =
        includedAudienceIds == null ? List.of() : List.copyOf(includedAudienceIds);
    excludedAudienceIds =
        excludedAudienceIds == null ? List.of() : List.copyOf(excludedAudienceIds);
  }
}
