package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.campaign.enums.ReachlyoCampaignObjective;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateReachlyoCampaignRequest(
    @Size(min = 1, max = 255) String name,
    ReachlyoCampaignObjective objective,
    CampaignBusinessGoal businessGoal,
    @Size(max = 2048) String landingPageUrl,
    @Pattern(regexp = "^[A-Za-z]{3,8}$") String currency,
    Instant startAt,
    Instant endAt) {}
