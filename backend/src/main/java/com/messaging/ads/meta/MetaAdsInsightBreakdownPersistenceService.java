package com.messaging.ads.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdInsightBreakdown;
import com.messaging.ads.enums.AdInsightBreakdownType;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.InsightSummary;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdInsightBreakdownRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetaAdsInsightBreakdownPersistenceService {

  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private final AdAccountRepository accountRepository;
  private final AdInsightBreakdownRepository breakdownRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public int persist(
      Long companyId,
      Long accountId,
      AdInsightBreakdownType type,
      LocalDate from,
      LocalDate to,
      List<InsightSummary> snapshots) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS || !account.isAccessible()) {
      throw new BadRequestException("Meta ad account is not available for analytics");
    }

    breakdownRepository
        .deleteByCompanyIdAndAdAccountIdAndProviderAndInsightLevelAndBreakdownTypeAndRangeStartAndRangeEnd(
            companyId,
            accountId,
            AdProvider.META_ADS,
            AdInsightLevel.ACCOUNT,
            type,
            from,
            to);

    Instant syncedAt = Instant.now();
    List<AdInsightBreakdown> rows = new ArrayList<>();

    if (type == AdInsightBreakdownType.ACTION_DEVICE
        || type == AdInsightBreakdownType.ACTION_TYPE) {
      snapshots.forEach(
          snapshot ->
              rows.addAll(actionRows(account, type, from, to, snapshot, syncedAt)));
    } else {
      for (InsightSummary snapshot : snapshots) {
        String dimension = dimension(snapshot, type);
        if (dimension == null) continue;
        rows.add(baseRow(account, type, from, to, snapshot, syncedAt, dimension));
      }
    }

    breakdownRepository.saveAll(rows);
    return rows.size();
  }

  private AdInsightBreakdown baseRow(
      AdAccount account,
      AdInsightBreakdownType type,
      LocalDate from,
      LocalDate to,
      InsightSummary snapshot,
      Instant syncedAt,
      String dimension) {
    AdInsightBreakdown row = new AdInsightBreakdown();
    row.setCompanyId(account.getCompanyId());
    row.setAdAccountId(account.getId());
    row.setProvider(AdProvider.META_ADS);
    row.setInsightLevel(AdInsightLevel.ACCOUNT);
    row.setEntityRef(account.getExternalAccountId());
    row.setEntityName(account.getName());
    row.setBreakdownType(type);
    row.setDimensionValue(dimension);
    row.setRangeStart(from);
    row.setRangeEnd(to);
    row.setCurrency(
        blank(snapshot.accountCurrency()) != null
            ? snapshot.accountCurrency().trim()
            : account.getCurrency());

    BigDecimal spend = decimal(snapshot.spend());
    long impressions = whole(snapshot.impressions());
    long clicks = whole(snapshot.clicks());
    BigDecimal conversions = conversionCount(snapshot);
    BigDecimal conversionValue = conversionValue(snapshot);

    row.setSpend(spend);
    row.setImpressions(impressions);
    row.setReach(whole(snapshot.reach()));
    row.setClicks(clicks);
    row.setCtr(decimalOrNull(snapshot.ctr()));
    row.setCpc(decimalOrComputed(snapshot.cpc(), spend, clicks));
    row.setCpm(
        decimalOrComputed(
            snapshot.cpm(),
            spend.multiply(BigDecimal.valueOf(1000)),
            impressions));
    row.setConversions(conversions);
    row.setConversionValue(conversionValue);
    row.setLeads(
        preferredMetric(
            snapshot.actions(), "offsite_conversion.fb_pixel_lead", "lead"));
    row.setPurchases(
        preferredMetric(
            snapshot.actions(),
            "offsite_conversion.fb_pixel_purchase",
            "omni_purchase",
            "purchase"));
    row.setActionsJson(json(snapshot.actions()));
    row.setActionValuesJson(json(snapshot.actionValues()));
    row.setProviderMetricsJson(json(snapshot));
    row.setLastSyncAt(syncedAt);
    return row;
  }

  private List<AdInsightBreakdown> actionRows(
      AdAccount account,
      AdInsightBreakdownType type,
      LocalDate from,
      LocalDate to,
      InsightSummary snapshot,
      Instant syncedAt) {
    if (snapshot.actions() == null || !snapshot.actions().isArray()) {
      return List.of();
    }

    List<AdInsightBreakdown> rows = new ArrayList<>();
    for (JsonNode action : snapshot.actions()) {
      String device = blank(action.path("action_device").asText(null));
      String actionType = blank(action.path("action_type").asText(null));
      if (actionType == null) continue;
      if (type == AdInsightBreakdownType.ACTION_DEVICE && device == null) continue;

      AdInsightBreakdown row = new AdInsightBreakdown();
      row.setCompanyId(account.getCompanyId());
      row.setAdAccountId(account.getId());
      row.setProvider(AdProvider.META_ADS);
      row.setInsightLevel(AdInsightLevel.ACCOUNT);
      row.setEntityRef(account.getExternalAccountId());
      row.setEntityName(account.getName());
      row.setBreakdownType(type);
      row.setDimensionValue(
          type == AdInsightBreakdownType.ACTION_DEVICE ? device : actionType);
      row.setActionType(actionType);
      row.setRangeStart(from);
      row.setRangeEnd(to);
      row.setCurrency(
          blank(snapshot.accountCurrency()) != null
              ? snapshot.accountCurrency().trim()
              : account.getCurrency());
      row.setActionValue(decimal(action.path("value").asText(null)));
      row.setConversionValue(
          matchingActionValue(
              snapshot.actionValues(),
              actionType,
              type == AdInsightBreakdownType.ACTION_DEVICE ? device : null));
      row.setActionsJson(json(action));
      row.setActionValuesJson(json(snapshot.actionValues()));
      row.setProviderMetricsJson(json(snapshot));
      row.setLastSyncAt(syncedAt);
      rows.add(row);
    }
    return rows;
  }

  private BigDecimal matchingActionValue(
      JsonNode values, String actionType, String actionDevice) {
    if (values == null || !values.isArray()) return ZERO;
    for (JsonNode item : values) {
      if (!actionType.equals(item.path("action_type").asText())) continue;
      if (actionDevice != null
          && !actionDevice.equals(item.path("action_device").asText())) {
        continue;
      }
      return decimal(item.path("value").asText(null));
    }
    return ZERO;
  }

  private String dimension(InsightSummary snapshot, AdInsightBreakdownType type) {
    return switch (type) {
      case AGE -> blank(snapshot.age());
      case GENDER -> blank(snapshot.gender());
      case COUNTRY -> blank(snapshot.country());
      case REGION -> blank(snapshot.region());
      case PUBLISHER_PLATFORM -> blank(snapshot.publisherPlatform());
      case PLATFORM_POSITION -> blank(snapshot.platformPosition());
      case IMPRESSION_DEVICE -> blank(snapshot.impressionDevice());
      case DEVICE_PLATFORM -> blank(snapshot.devicePlatform());
      case CONVERSION_DESTINATION -> blank(snapshot.conversionDestination());
      case ACTION_TYPE, ACTION_DEVICE -> null;
    };
  }

  private BigDecimal conversionCount(InsightSummary snapshot) {
    BigDecimal broad =
        preferredMetric(snapshot.conversions(), "offsite_conversion", "omni_conversion");
    if (broad.signum() > 0) return broad;

    return preferredMetric(
            snapshot.conversions(),
            "offsite_conversion.fb_pixel_purchase",
            "omni_purchase",
            "purchase")
        .add(
            preferredMetric(
                snapshot.conversions(),
                "offsite_conversion.fb_pixel_lead",
                "lead"))
        .add(
            preferredMetric(
                snapshot.conversions(),
                "offsite_conversion.fb_pixel_complete_registration",
                "complete_registration"))
        .add(
            preferredMetric(
                snapshot.conversions(),
                "offsite_conversion.fb_pixel_subscribe",
                "subscribe"));
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

  private BigDecimal decimalOrComputed(
      String providerValue, BigDecimal numerator, long denominator) {
    BigDecimal provider = decimalOrNull(providerValue);
    return provider != null
        ? provider
        : divide(numerator, BigDecimal.valueOf(denominator));
  }

  private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
    if (numerator == null || denominator == null || denominator.signum() == 0) {
      return null;
    }
    return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
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

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String json(Object value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Unable to serialize Meta insight breakdown payload", exception);
    }
  }
}
