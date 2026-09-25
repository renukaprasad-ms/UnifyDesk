package com.messaging.ads.campaign.dto;

import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.enums.AdRecommendationCategory;
import com.messaging.ads.enums.AdRecommendationSeverity;
import com.messaging.ads.enums.AdRecommendationSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ReachlyoCampaignRecommendationsResponse(
    String campaignId,
    int criticalCount,
    int warningCount,
    int opportunityCount,
    int infoCount,
    Instant generatedAt,
    Instant analyticsLastSyncAt,
    List<Item> items) {

  public record Item(
      String key,
      AdRecommendationSeverity severity,
      AdRecommendationCategory category,
      AdRecommendationSource source,
      AdProvider provider,
      String adAccountId,
      String deploymentId,
      String localCampaignId,
      String title,
      String description,
      String actionLabel,
      String actionPath,
      Map<String, Object> evidence,
      String providerRecommendationResourceName) {}
}
