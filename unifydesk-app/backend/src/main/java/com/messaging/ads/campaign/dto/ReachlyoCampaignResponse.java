package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.entity.BudgetPlan;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.ProviderDeployment;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.campaign.enums.ReachlyoCampaignObjective;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
import java.time.Instant;
import java.util.List;

public record ReachlyoCampaignResponse(
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
    BudgetPlanResponse budgetPlan,
    List<CampaignChannelResponse> channels,
    List<ProviderDeploymentResponse> deployments,
    Instant createdAt,
    Instant updatedAt) {

  public static ReachlyoCampaignResponse from(
      ReachlyoCampaign campaign,
      BudgetPlan budgetPlan,
      List<CampaignChannel> channels,
      List<ProviderDeployment> deployments) {
    return new ReachlyoCampaignResponse(
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
        budgetPlan == null ? null : BudgetPlanResponse.from(budgetPlan, campaign.getCurrency()),
        channels.stream().map(CampaignChannelResponse::from).toList(),
        deployments.stream().map(ProviderDeploymentResponse::from).toList(),
        campaign.getCreatedAt(),
        campaign.getUpdatedAt());
  }
}
