package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.entity.ProviderDeployment;
import com.messaging.ads.campaign.enums.ProviderDeploymentStatus;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record ProviderDeploymentResponse(
    String id,
    String campaignId,
    String channelId,
    AdProvider provider,
    String adAccountId,
    String localCampaignId,
    String externalCampaignId,
    String providerStatus,
    ProviderDeploymentStatus deploymentStatus,
    String lastErrorCode,
    String lastErrorMessage,
    Instant lastSyncAt,
    Instant createdAt,
    Instant updatedAt) {

  public static ProviderDeploymentResponse from(ProviderDeployment deployment) {
    return new ProviderDeploymentResponse(
        deployment.getId().toString(),
        deployment.getReachlyoCampaignId().toString(),
        deployment.getCampaignChannelId().toString(),
        deployment.getProvider(),
        deployment.getAdAccountId().toString(),
        deployment.getLocalCampaignId() == null ? null : deployment.getLocalCampaignId().toString(),
        deployment.getExternalCampaignId(),
        deployment.getProviderStatus(),
        deployment.getDeploymentStatus(),
        deployment.getLastErrorCode(),
        deployment.getLastErrorMessage(),
        deployment.getLastSyncAt(),
        deployment.getCreatedAt(),
        deployment.getUpdatedAt());
  }
}
