package com.messaging.ads.meta.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateMetaCampaignRequest(
    @Size(max = 255) String name,
    String bidStrategy,
    @Positive Long dailyBudgetMinor,
    @Positive Long lifetimeBudgetMinor) {

  @AssertTrue(message = "Choose either a daily budget or a lifetime budget, not both")
  public boolean hasValidBudgetChoice() {
    return dailyBudgetMinor == null || lifetimeBudgetMinor == null;
  }
}
