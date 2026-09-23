package com.messaging.ads.dto;

import java.util.List;

public record AdRecommendationSummaryResponse(
    int managedAccountCount,
    int criticalCount,
    int warningCount,
    int opportunityCount,
    int infoCount,
    int attentionCount,
    int dismissedCount,
    int snoozedCount,
    List<AccountItem> topItems) {

  public record AccountItem(
      String adAccountId,
      String accountName,
      AdRecommendationsResponse.Item item) {}
}
