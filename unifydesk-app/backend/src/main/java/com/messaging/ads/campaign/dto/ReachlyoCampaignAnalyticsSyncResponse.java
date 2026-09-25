package com.messaging.ads.campaign.dto;

import com.messaging.ads.enums.AdProvider;
import java.util.List;

public record ReachlyoCampaignAnalyticsSyncResponse(
    int synchronizedAccounts,
    List<SyncFailure> failures,
    ReachlyoCampaignAnalyticsResponse analytics) {

  public record SyncFailure(
      AdProvider provider,
      String adAccountId,
      String message) {}
}
