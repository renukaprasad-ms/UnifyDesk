package com.messaging.ads.dto;

import com.messaging.ads.entity.AdKeyword;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record AdKeywordResponse(
    String id,
    String companyId,
    String adAccountId,
    String campaignId,
    String adSetId,
    AdProvider provider,
    String externalKeywordId,
    String keywordText,
    String matchType,
    AdEntityStatus status,
    String providerStatus,
    Long bidAmountMicros,
    Integer qualityScore,
    String finalUrlsJson,
    boolean negative,
    boolean accessible,
    Instant lastSyncAt) {

  public static AdKeywordResponse from(AdKeyword keyword) {
    return new AdKeywordResponse(
        keyword.getId().toString(),
        keyword.getCompanyId().toString(),
        keyword.getAdAccountId().toString(),
        keyword.getCampaignId().toString(),
        keyword.getAdSetId().toString(),
        keyword.getProvider(),
        keyword.getExternalKeywordId(),
        keyword.getKeywordText(),
        keyword.getMatchType(),
        keyword.getStatus(),
        keyword.getProviderStatus(),
        keyword.getBidAmountMicros(),
        keyword.getQualityScore(),
        keyword.getFinalUrlsJson(),
        keyword.isNegative(),
        keyword.isAccessible(),
        keyword.getLastSyncAt());
  }
}
