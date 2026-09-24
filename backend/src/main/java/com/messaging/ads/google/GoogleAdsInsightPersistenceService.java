package com.messaging.ads.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.Ad;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdInsightDaily;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdInsightGranularity;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdInsightDailyRepository;
import com.messaging.ads.repository.AdRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleAdsInsightPersistenceService {

  private final AdAccountRepository accountRepository;
  private final AdCampaignRepository campaignRepository;
  private final AdSetRepository adSetRepository;
  private final AdRepository adRepository;
  private final AdInsightDailyRepository insightRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public int persist(
      Long companyId,
      Long accountId,
      AdInsightLevel level,
      AdInsightGranularity granularity,
      LocalDate from,
      LocalDate to,
      List<JsonNode> rows) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.GOOGLE_ADS || !account.isAccessible()) {
      throw new BadRequestException("Google Ads account is not available for analytics");
    }

    if (granularity == AdInsightGranularity.DAILY) {
      insightRepository
          .deleteByCompanyIdAndAdAccountIdAndProviderAndInsightLevelAndGranularityAndInsightDateBetween(
              companyId,
              accountId,
              AdProvider.GOOGLE_ADS,
              level,
              granularity,
              from,
              to);
    } else {
      insightRepository
          .deleteByCompanyIdAndAdAccountIdAndProviderAndInsightLevelAndGranularityAndInsightDateAndPeriodEnd(
              companyId,
              accountId,
              AdProvider.GOOGLE_ADS,
              level,
              granularity,
              from,
              to);
    }

    Instant syncedAt = Instant.now();
    int count = 0;
    for (JsonNode row : rows) {
      LocalDate date =
          granularity == AdInsightGranularity.RANGE
              ? from
              : parseDate(row.path("segments").path("date").asText(null));
      if (date == null || date.isBefore(from) || date.isAfter(to)) continue;

      JsonNode metrics = row.path("metrics");
      AdInsightDaily insight = new AdInsightDaily();
      insight.setCompanyId(companyId);
      insight.setAdAccountId(accountId);
      insight.setProvider(AdProvider.GOOGLE_ADS);
      insight.setInsightLevel(level);
      insight.setGranularity(granularity);
      insight.setInsightDate(date);
      insight.setPeriodEnd(
          granularity == AdInsightGranularity.RANGE ? to : date);
      insight.setCurrency(
          first(
              text(row.path("customer"), "currencyCode"),
              account.getCurrency()));

      bindEntity(account, insight, row, level);

      long impressions = longValue(metrics, "impressions");
      long clicks = longValue(metrics, "clicks");
      long costMicros = longValue(metrics, "costMicros");
      BigDecimal spend = GoogleAdsMoney.microsToMajor(costMicros);
      BigDecimal conversions = decimal(metrics, "conversions");
      BigDecimal conversionValue = decimal(metrics, "conversionsValue");

      insight.setSpend(spend);
      insight.setImpressions(impressions);
      insight.setReach(longValue(metrics, "uniqueUsers"));
      insight.setClicks(clicks);
      insight.setUniqueClicks(0L);
      insight.setInlineLinkClicks(clicks);
      insight.setCtr(decimalOrComputed(metrics, "ctr", clicks, impressions));
      insight.setCpc(
          microsMetricOrComputed(metrics, "averageCpc", spend, clicks));
      insight.setCpm(
          microsMetricOrComputed(
              metrics,
              "averageCpm",
              spend.multiply(BigDecimal.valueOf(1000)),
              impressions));
      insight.setFrequency(null);
      insight.setConversions(conversions);
      insight.setConversionValue(conversionValue);
      insight.setLeads(BigDecimal.ZERO);
      insight.setPurchases(BigDecimal.ZERO);
      insight.setEngagements(decimal(metrics, "engagements"));
      insight.setVideoViews(decimal(metrics, "videoViews"));
      insight.setCpa(divide(spend, conversions));
      insight.setRoas(divide(conversionValue, spend));
      insight.setProviderMetricsJson(json(row));
      insight.setLastSyncAt(syncedAt);
      insightRepository.save(insight);
      count++;
    }

    return count;
  }

  private void bindEntity(
      AdAccount account,
      AdInsightDaily insight,
      JsonNode row,
      AdInsightLevel level) {
    switch (level) {
      case ACCOUNT -> {
        insight.setEntityRef(account.getExternalAccountId());
        insight.setEntityName(account.getName());
      }
      case CAMPAIGN -> {
        JsonNode node = row.path("campaign");
        String externalId = required(text(node, "id"), "campaign");
        insight.setEntityRef(externalId);
        insight.setEntityName(text(node, "name"));
        AdCampaign campaign =
            campaignRepository
                .findByCompanyIdAndAdAccountIdAndProviderAndExternalCampaignId(
                    account.getCompanyId(),
                    account.getId(),
                    AdProvider.GOOGLE_ADS,
                    externalId)
                .orElse(null);
        if (campaign != null) insight.setCampaignId(campaign.getId());
      }
      case AD_SET -> {
        JsonNode node = row.path("adGroup");
        String externalId = required(text(node, "id"), "ad group");
        insight.setEntityRef(externalId);
        insight.setEntityName(text(node, "name"));
        AdSet adSet =
            adSetRepository
                .findByCompanyIdAndAdAccountIdAndProviderAndExternalAdSetId(
                    account.getCompanyId(),
                    account.getId(),
                    AdProvider.GOOGLE_ADS,
                    externalId)
                .orElse(null);
        if (adSet != null) {
          insight.setAdSetId(adSet.getId());
          insight.setCampaignId(adSet.getCampaignId());
        }
      }
      case AD -> {
        JsonNode adGroupAd = row.path("adGroupAd");
        String externalId = resourceSuffix(text(adGroupAd, "resourceName"));
        if (externalId == null) {
          externalId = required(text(adGroupAd.path("ad"), "id"), "ad");
        }
        insight.setEntityRef(externalId);
        insight.setEntityName(text(adGroupAd.path("ad"), "name"));
        Ad ad =
            adRepository
                .findByCompanyIdAndAdAccountIdAndProviderAndExternalAdId(
                    account.getCompanyId(),
                    account.getId(),
                    AdProvider.GOOGLE_ADS,
                    externalId)
                .orElse(null);
        if (ad != null) {
          insight.setAdId(ad.getId());
          insight.setAdSetId(ad.getAdSetId());
          insight.setCampaignId(ad.getCampaignId());
        }
      }
    }
  }

  private BigDecimal microsMetricOrComputed(
      JsonNode metrics,
      String field,
      BigDecimal numerator,
      long denominator) {
    Long micros = nullableLong(metrics, field);
    return micros == null
        ? divide(numerator, BigDecimal.valueOf(denominator))
        : GoogleAdsMoney.microsToMajor(micros);
  }

  private BigDecimal decimalOrComputed(
      JsonNode metrics, String field, long numerator, long denominator) {
    BigDecimal value = nullableDecimal(metrics, field);
    if (value != null) return value;
    if (denominator == 0) return null;
    return BigDecimal.valueOf(numerator)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(denominator), 8, RoundingMode.HALF_UP);
  }

  private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
    if (numerator == null || denominator == null || denominator.signum() == 0) return null;
    return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
  }

  private BigDecimal divide(BigDecimal numerator, long denominator) {
    if (denominator == 0) return null;
    return divide(numerator, BigDecimal.valueOf(denominator));
  }

  private BigDecimal decimal(JsonNode node, String field) {
    BigDecimal value = nullableDecimal(node, field);
    return value == null ? BigDecimal.ZERO : value;
  }

  private BigDecimal nullableDecimal(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null) return null;
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private long longValue(JsonNode node, String field) {
    Long value = nullableLong(node, field);
    return value == null ? 0L : value;
  }

  private Long nullableLong(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null) return null;
    try {
      return new BigDecimal(value).longValue();
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalDate.parse(value.trim());
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private String resourceSuffix(String resourceName) {
    if (resourceName == null || resourceName.isBlank()) return null;
    int slash = resourceName.lastIndexOf('/');
    return slash >= 0 && slash < resourceName.length() - 1
        ? resourceName.substring(slash + 1)
        : resourceName;
  }

  private String required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException("Google Ads analytics row is missing " + label + " ID");
    }
    return value;
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) return null;
    String value = node.path(field).asText(null);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String first(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String json(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Google Ads analytics row", exception);
    }
  }
}
