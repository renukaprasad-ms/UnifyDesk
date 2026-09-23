package com.messaging.ads.dto;

import com.messaging.ads.enums.AdRecommendationCategory;
import com.messaging.ads.enums.AdRecommendationSeverity;
import com.messaging.ads.enums.AdRecommendationSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AdRecommendationsResponse(
    String adAccountId,
    BigDecimal metaOpportunityScore,
    Integer metaOpportunityScoreWeight,
    int criticalCount,
    int warningCount,
    int opportunityCount,
    int infoCount,
    Instant generatedAt,
    Instant healthLastSyncAt,
    Instant billingLastSyncAt,
    Instant analyticsLastSyncAt,
    List<Item> items) {

  public record Item(
      String key,
      AdRecommendationSeverity severity,
      AdRecommendationCategory category,
      AdRecommendationSource source,
      String title,
      String description,
      String entityType,
      String entityId,
      String actionLabel,
      String actionPath,
      Map<String, Object> evidence) {}
}
