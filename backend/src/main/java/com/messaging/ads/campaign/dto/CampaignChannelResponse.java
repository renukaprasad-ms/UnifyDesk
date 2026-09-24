package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.enums.CampaignChannelStatus;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.enums.AdProvider;
import java.math.BigDecimal;
import java.time.Instant;

public record CampaignChannelResponse(
    String id,
    String campaignId,
    AdProvider provider,
    CampaignChannelType channelType,
    boolean enabled,
    Long budgetAllocationMinor,
    BigDecimal allocationPercent,
    String providerOverrideJson,
    CampaignChannelStatus status,
    Instant createdAt,
    Instant updatedAt) {

  public static CampaignChannelResponse from(CampaignChannel channel) {
    return new CampaignChannelResponse(
        channel.getId().toString(),
        channel.getReachlyoCampaignId().toString(),
        channel.getProvider(),
        channel.getChannelType(),
        channel.isEnabled(),
        channel.getBudgetAllocationMinor(),
        channel.getAllocationPercent(),
        channel.getProviderOverrideJson(),
        channel.getStatus(),
        channel.getCreatedAt(),
        channel.getUpdatedAt());
  }
}
