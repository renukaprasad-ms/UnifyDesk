package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.campaign.enums.ReachlyoCampaignObjective;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateReachlyoCampaignRequest(
    @NotBlank @Size(max = 255) String name,
    @NotNull ReachlyoCampaignObjective objective,
    @NotNull CampaignBusinessGoal businessGoal,
    @Size(max = 2048) String landingPageUrl,
    @NotBlank @Pattern(regexp = "^[A-Za-z]{3,8}$") String currency,
    Instant startAt,
    Instant endAt) {

  @AssertTrue(message = "End time must be after start time")
  public boolean hasValidSchedule() {
    return startAt == null || endAt == null || endAt.isAfter(startAt);
  }
}
