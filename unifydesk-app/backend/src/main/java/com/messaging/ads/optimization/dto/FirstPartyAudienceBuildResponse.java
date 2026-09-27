package com.messaging.ads.optimization.dto;

import com.messaging.ads.enums.AdProvider;
import java.util.List;

public record FirstPartyAudienceBuildResponse(
    int submittedMembers,
    List<TargetResult> targets) {

  public FirstPartyAudienceBuildResponse {
    targets = targets == null ? List.of() : List.copyOf(targets);
  }

  public record TargetResult(
      AdProvider provider,
      String adAccountId,
      boolean success,
      String localAudienceId,
      String externalAudienceId,
      int providerAccepted,
      int providerRejected,
      List<String> warnings,
      String error) {
    public TargetResult {
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }
}
