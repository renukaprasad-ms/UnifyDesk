package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.entity.ConversionGoalMapping;
import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.campaign.enums.ConversionTargetType;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record ConversionGoalMappingResponse(
    String id,
    String campaignId,
    CampaignBusinessGoal businessGoal,
    AdProvider provider,
    String adAccountId,
    ConversionTargetType targetType,
    String adConversionId,
    String providerEventName,
    String externalConversionId,
    boolean validated,
    Instant createdAt,
    Instant updatedAt) {
  public static ConversionGoalMappingResponse from(ConversionGoalMapping mapping) {
    return new ConversionGoalMappingResponse(
        mapping.getId().toString(),
        mapping.getReachlyoCampaignId().toString(),
        mapping.getBusinessGoal(),
        mapping.getProvider(),
        mapping.getAdAccountId().toString(),
        mapping.getTargetType(),
        mapping.getAdConversionId() == null ? null : mapping.getAdConversionId().toString(),
        mapping.getProviderEventName(),
        mapping.getExternalConversionId(),
        mapping.isValidated(),
        mapping.getCreatedAt(),
        mapping.getUpdatedAt());
  }
}
