package com.messaging.ads.meta;

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
import com.messaging.ads.meta.MetaAdsApiClient.InsightSummary;
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
public class MetaAdsInsightPersistenceService {

  private static final BigDecimal ZERO = BigDecimal.ZERO;

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
      List<InsightSummary> snapshots) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS || !account.isAccessible()) {
      throw new BadRequestException("Meta ad account is not available for insights");
    }

    if (granularity == AdInsightGranularity.DAILY) {
      insightRepository
          .deleteByCompanyIdAndAdAccountIdAndProviderAndInsightLevelAndGranularityAndInsightDateBetween(
              companyId,
              accountId,
              AdProvider.META_ADS,
              level,
              granularity,
              from,
              to);
    } else {
      insightRepository
          .deleteByCompanyIdAndAdAccountIdAndProviderAndInsightLevelAndGranularityAndInsightDateAndPeriodEnd(
              companyId,
              accountId,
              AdProvider.META_ADS,
              level,
              granularity,
              from,
              to);
    }

    Instant syncedAt = Instant.now();
    int count = 0;
    for (InsightSummary snapshot : snapshots) {
      LocalDate date = parseDate(snapshot.dateStart());
      if (date == null || date.isBefore(from) || date.isAfter(to)) continue;

      AdInsightDaily insight = new AdInsightDaily();
      insight.setCompanyId(companyId);
      insight.setAdAccountId(accountId);
      insight.setProvider(AdProvider.META_ADS);
      insight.setInsightLevel(level);
      insight.setGranularity(granularity);
      insight.setInsightDate(granularity == AdInsightGranularity.RANGE ? from : date);
      insight.setPeriodEnd(granularity == AdInsightGranularity.RANGE ? to : date);
      insight.setCurrency(blank(snapshot.accountCurrency()) != null
          ? snapshot.accountCurrency().trim()
          : account.getCurrency());

      bindEntity(account, insight, snapshot, level);

      BigDecimal spend = decimal(snapshot.spend());
      long impressions = whole(snapshot.impressions());
      long reach = whole(snapshot.reach());
      long clicks = whole(snapshot.clicks());
      BigDecimal conversions = conversionCount(snapshot);
      BigDecimal conversionValue = conversionValue(snapshot);

      insight.setSpend(spend);
      insight.setImpressions(impressions);
      insight.setReach(reach);
      insight.setClicks(clicks);
      insight.setUniqueClicks(whole(snapshot.uniqueClicks()));
      insight.setInlineLinkClicks(whole(snapshot.inlineLinkClicks()));
      insight.setCtr(decimalOrNull(snapshot.ctr()));
      insight.setCpc(decimalOrComputed(snapshot.cpc(), spend, clicks));
      insight.setCpm(
          decimalOrComputed(
              snapshot.cpm(),
              spend.multiply(BigDecimal.valueOf(1000)),
              impressions));
      insight.setFrequency(decimalOrNull(snapshot.frequency()));
      insight.setConversions(conversions);
      insight.setConversionValue(conversionValue);
      insight.setLeads(preferredMetric(snapshot.actions(), "offsite_conversion.fb_pixel_lead", "lead"));
      insight.setPurchases(
          preferredMetric(
              snapshot.actions(),
              "offsite_conversion.fb_pixel_purchase",
              "omni_purchase",
              "purchase"));
      insight.setEngagements(preferredMetric(snapshot.actions(), "post_engagement"));
      insight.setVideoViews(preferredMetric(snapshot.actions(), "video_view"));
      insight.setCpa(divide(spend, conversions));
      insight.setRoas(roas(snapshot, spend, conversionValue));
      insight.setActionsJson(json(snapshot.actions()));
      insight.setActionValuesJson(json(snapshot.actionValues()));
      insight.setConversionsJson(json(snapshot.conversions()));
      insight.setConversionValuesJson(json(snapshot.conversionValues()));
      insight.setPurchaseRoasJson(
          json(snapshot.purchaseRoas() != null ? snapshot.purchaseRoas() : snapshot.websitePurchaseRoas()));
      insight.setProviderMetricsJson(json(snapshot));
      insight.setLastSyncAt(syncedAt);
      insightRepository.save(insight);
      count++;
    }
    return count;
  }

  private void bindEntity(
      AdAccount account,
      AdInsightDaily insight,
      InsightSummary snapshot,
      AdInsightLevel level) {
    switch (level) {
      case ACCOUNT -> {
        insight.setEntityRef(
            blank(snapshot.accountId()) != null
                ? snapshot.accountId().trim()
                : account.getExternalAccountId());
        insight.setEntityName(
            blank(snapshot.accountName()) != null
                ? snapshot.accountName().trim()
                : account.getName());
      }
      case CAMPAIGN -> {
        String externalId = required(snapshot.campaignId(), "campaign");
        insight.setEntityRef(externalId);
        insight.setEntityName(blank(snapshot.campaignName()));
        AdCampaign campaign =
            campaignRepository
                .findByCompanyIdAndAdAccountIdAndProviderAndExternalCampaignId(
                    account.getCompanyId(),
                    account.getId(),
                    AdProvider.META_ADS,
                    externalId)
                .orElse(null);
        if (campaign != null) insight.setCampaignId(campaign.getId());
      }
      case AD_SET -> {
        String externalId = required(snapshot.adSetId(), "ad set");
        insight.setEntityRef(externalId);
        insight.setEntityName(blank(snapshot.adSetName()));
        AdSet adSet =
            adSetRepository
                .findByCompanyIdAndAdAccountIdAndProviderAndExternalAdSetId(
                    account.getCompanyId(),
                    account.getId(),
                    AdProvider.META_ADS,
                    externalId)
                .orElse(null);
        if (adSet != null) {
          insight.setAdSetId(adSet.getId());
          insight.setCampaignId(adSet.getCampaignId());
        }
      }
      case AD -> {
        String externalId = required(snapshot.adId(), "ad");
        insight.setEntityRef(externalId);
        insight.setEntityName(blank(snapshot.adName()));
        Ad ad =
            adRepository
                .findByCompanyIdAndAdAccountIdAndProviderAndExternalAdId(
                    account.getCompanyId(),
                    account.getId(),
                    AdProvider.META_ADS,
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

  private BigDecimal conversionCount(InsightSummary snapshot) {
    BigDecimal broad =
        preferredMetric(snapshot.conversions(), "offsite_conversion", "omni_conversion");
    if (broad.signum() > 0) return broad;

    return sum(
        preferredMetric(
            snapshot.conversions(),
            "offsite_conversion.fb_pixel_purchase",
            "omni_purchase",
            "purchase"),
        preferredMetric(
            snapshot.conversions(), "offsite_conversion.fb_pixel_lead", "lead"),
        preferredMetric(
            snapshot.conversions(),
            "offsite_conversion.fb_pixel_complete_registration",
            "complete_registration"),
        preferredMetric(
            snapshot.conversions(),
            "offsite_conversion.fb_pixel_subscribe",
            "subscribe"),
        preferredMetric(
            snapshot.conversions(),
            "offsite_conversion.fb_pixel_start_trial",
            "start_trial"),
        preferredMetric(
            snapshot.conversions(),
            "offsite_conversion.fb_pixel_submit_application",
            "submit_application"));
  }

  private BigDecimal conversionValue(InsightSummary snapshot) {
    BigDecimal broad =
        preferredMetric(snapshot.conversionValues(), "offsite_conversion", "omni_conversion");
    if (broad.signum() > 0) return broad;
    return preferredMetric(
        snapshot.actionValues(),
        "offsite_conversion.fb_pixel_purchase",
        "omni_purchase",
        "purchase");
  }

  private BigDecimal roas(
      InsightSummary snapshot, BigDecimal spend, BigDecimal conversionValue) {
    BigDecimal provider =
        preferredMetric(
            snapshot.purchaseRoas(),
            "omni_purchase",
            "offsite_conversion.fb_pixel_purchase",
            "purchase");
    if (provider.signum() <= 0) {
      provider =
          preferredMetric(
              snapshot.websitePurchaseRoas(),
              "offsite_conversion.fb_pixel_purchase",
              "purchase");
    }
    return provider.signum() > 0 ? provider : divide(conversionValue, spend);
  }

  private BigDecimal preferredMetric(JsonNode metrics, String... actionTypes) {
    if (metrics == null || !metrics.isArray()) return ZERO;
    for (String actionType : actionTypes) {
      for (JsonNode item : metrics) {
        if (actionType.equals(item.path("action_type").asText())) {
          return decimal(item.path("value").asText(null));
        }
      }
    }
    return ZERO;
  }

  private BigDecimal sum(BigDecimal... values) {
    BigDecimal total = ZERO;
    for (BigDecimal value : values) total = total.add(value);
    return total;
  }

  private BigDecimal decimalOrComputed(
      String providerValue, BigDecimal numerator, long denominator) {
    BigDecimal value = decimalOrNull(providerValue);
    return value != null ? value : divide(numerator, BigDecimal.valueOf(denominator));
  }

  private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
    if (numerator == null
        || denominator == null
        || denominator.signum() == 0) {
      return null;
    }
    return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
  }

  private BigDecimal divide(BigDecimal numerator, long denominator) {
    return divide(numerator, BigDecimal.valueOf(denominator));
  }

  private BigDecimal decimal(String value) {
    BigDecimal parsed = decimalOrNull(value);
    return parsed == null ? ZERO : parsed;
  }

  private BigDecimal decimalOrNull(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return new BigDecimal(value.trim());
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private long whole(String value) {
    if (value == null || value.isBlank()) return 0L;
    try {
      return new BigDecimal(value.trim()).longValue();
    } catch (NumberFormatException ignored) {
      return 0L;
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

  private String required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException("Meta insights row is missing " + label + " ID");
    }
    return value.trim();
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String json(Object value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Meta insights payload", exception);
    }
  }
}
