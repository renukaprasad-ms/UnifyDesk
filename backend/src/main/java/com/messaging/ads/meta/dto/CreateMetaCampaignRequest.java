package com.messaging.ads.meta.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateMetaCampaignRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank String objective,
    String buyingType,
    String bidStrategy,
    @Positive Long dailyBudgetMinor,
    @Positive Long lifetimeBudgetMinor,
    @NotEmpty List<@NotBlank String> specialAdCategories) {

  @AssertTrue(message = "Choose either a daily budget or a lifetime budget, not both")
  public boolean hasValidBudgetChoice() {
    return dailyBudgetMinor == null || lifetimeBudgetMinor == null;
  }
}
