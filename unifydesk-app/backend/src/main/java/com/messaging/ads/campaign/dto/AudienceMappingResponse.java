package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.entity.AudienceMapping;
import com.messaging.ads.campaign.enums.AudienceMappingStatus;
import com.messaging.ads.enums.AdProvider;

public record AudienceMappingResponse(
    String id,
    String campaignChannelId,
    AdProvider provider,
    AudienceMappingStatus mappingStatus,
    String localAudienceId,
    String externalAudienceId,
    String providerCriteriaJson,
    String notes) {
  public static AudienceMappingResponse from(AudienceMapping mapping) {
    return new AudienceMappingResponse(
        mapping.getId().toString(),
        mapping.getCampaignChannelId().toString(),
        mapping.getProvider(),
        mapping.getMappingStatus(),
        mapping.getLocalAudienceId() == null ? null : mapping.getLocalAudienceId().toString(),
        mapping.getExternalAudienceId(),
        mapping.getProviderCriteriaJson(),
        mapping.getNotes());
  }
}
