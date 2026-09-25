package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.campaign.enums.ReachlyoCampaignObjective;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
import java.time.Instant;

public record ReachlyoCampaignSummaryResponse(
    String id,
    String companyId,
    String name,
    ReachlyoCampaignObjective objective,
    CampaignBusinessGoal businessGoal,
    String landingPageUrl,
    ReachlyoCampaignStatus status,
    String currency,
    Instant startAt,
    Instant endAt,
    String createdByUserId,
    Instant createdAt,
    Instant updatedAt) {

  public static ReachlyoCampaignSummaryResponse from(ReachlyoCampaign campaign) {
    return new ReachlyoCampaignSummaryResponse(
        campaign.getId().toString(),
        campaign.getCompanyId().toString(),
        campaign.getName(),
        campaign.getObjective(),
        campaign.getBusinessGoal(),
        campaign.getLandingPageUrl(),
        campaign.getStatus(),
        campaign.getCurrency(),
        campaign.getStartAt(),
        campaign.getEndAt(),
        campaign.getCreatedByUserId().toString(),
        campaign.getCreatedAt(),
        campaign.getUpdatedAt());
  }
}
