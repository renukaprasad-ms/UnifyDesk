package com.messaging.ads.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdInsightBreakdown;
import com.messaging.ads.enums.AdInsightBreakdownType;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdInsightBreakdownRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleAdsInsightBreakdownPersistenceService {

  private final AdAccountRepository accountRepository;
  private final AdInsightBreakdownRepository repository;
  private final ObjectMapper objectMapper;

  @Transactional
  public int persist(
      Long companyId,
      Long accountId,
      AdInsightBreakdownType type,
      LocalDate from,
      LocalDate to,
      List<JsonNode> snapshots) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.GOOGLE_ADS || !account.isAccessible()) {
      throw new BadRequestException("Google Ads account is not available for analytics");
    }

    repository
        .deleteByCompanyIdAndAdAccountIdAndProviderAndInsightLevelAndBreakdownTypeAndRangeStartAndRangeEnd(
            companyId,
            accountId,
            AdProvider.GOOGLE_ADS,
            AdInsightLevel.ACCOUNT,
            type,
            from,
            to);

    Map<Key, Aggregate> aggregates = new LinkedHashMap<>();
    for (JsonNode snapshot : snapshots) {
      Key key = key(snapshot, type);
      if (key == null) continue;
      aggregates.computeIfAbsent(key, ignored -> new Aggregate()).add(snapshot);
    }

    Instant syncedAt = Instant.now();
    List<AdInsightBreakdown> rows = new ArrayList<>();

    for (var entry : aggregates.entrySet()) {
      Key key = entry.getKey();
      Aggregate aggregate = entry.getValue();

      AdInsightBreakdown row = new AdInsightBreakdown();
      row.setCompanyId(companyId);
      row.setAdAccountId(accountId);
      row.setProvider(AdProvider.GOOGLE_ADS);
      row.setInsightLevel(AdInsightLevel.ACCOUNT);
      row.setEntityRef(account.getExternalAccountId());
      row.setEntityName(account.getName());
      row.setBreakdownType(type);
      row.setDimensionValue(key.dimension());
      row.setDimensionValue2(key.secondary());
      row.setActionType(key.actionType());
      row.setRangeStart(from);
      row.setRangeEnd(to);
      row.setCurrency(account.getCurrency());
      row.setSpend(GoogleAdsMoney.microsToMajor(aggregate.costMicros));
      row.setImpressions(aggregate.impressions);
      row.setReach(aggregate.uniqueUsers);
      row.setClicks(aggregate.clicks);
      row.setCtr(percent(aggregate.clicks, aggregate.impressions));
      row.setCpc(divide(GoogleAdsMoney.microsToMajor(aggregate.costMicros), aggregate.clicks));
      row.setCpm(
          divide(
              GoogleAdsMoney.microsToMajor(aggregate.costMicros)
                  .multiply(BigDecimal.valueOf(1000)),
              aggregate.impressions));
      row.setConversions(aggregate.conversions);
      row.setConversionValue(aggregate.conversionValue);
      row.setLeads(
          key.actionType().contains("LEAD") ? aggregate.conversions : BigDecimal.ZERO);
      row.setPurchases(
          key.actionType().contains("PURCHASE") ? aggregate.conversions : BigDecimal.ZERO);
      row.setActionValue(
          type == AdInsightBreakdownType.ACTION_TYPE
                  || type == AdInsightBreakdownType.ACTION_DEVICE
              ? aggregate.conversions
              : BigDecimal.ZERO);
      row.setProviderMetricsJson(json(aggregate.providerRows));
      row.setLastSyncAt(syncedAt);
      rows.add(row);
    }

    repository.saveAll(rows);
    return rows.size();
  }

  private Key key(JsonNode row, AdInsightBreakdownType type) {
    JsonNode segments = row.path("segments");
    JsonNode criterion = row.path("adGroupCriterion");

    String primary;
    String secondary = "";
    String action = "";

    switch (type) {
      case AGE -> primary = text(criterion.path("ageRange"), "type");
      case GENDER -> primary = text(criterion.path("gender"), "type");
      case COUNTRY -> primary = resourceSuffix(text(segments, "geoTargetCountry"));
      case REGION -> primary = resourceSuffix(text(segments, "geoTargetRegion"));
      case PUBLISHER_PLATFORM -> primary = text(segments, "adNetworkType");
      case PLATFORM_POSITION -> primary = text(segments, "slot");
      case IMPRESSION_DEVICE, DEVICE_PLATFORM -> primary = text(segments, "device");
      case CONVERSION_DESTINATION -> primary = text(segments, "adDestinationType");
      case ACTION_TYPE -> {
        primary = text(segments, "conversionActionCategory");
        action = primary == null ? "" : primary;
      }
      case ACTION_DEVICE -> {
        primary = text(segments, "device");
        secondary = text(segments, "conversionActionCategory");
        action = secondary == null ? "" : secondary;
      }
      default -> primary = null;
    }

    if (primary == null || primary.isBlank()) return null;
    return new Key(primary, secondary == null ? "" : secondary, action);
  }

  private String resourceSuffix(String value) {
    if (value == null || value.isBlank()) return null;
    int slash = value.lastIndexOf('/');
    return slash >= 0 && slash + 1 < value.length() ? value.substring(slash + 1) : value;
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || value.asText().isBlank()) return null;
    return value.asText().trim();
  }

  private BigDecimal percent(long numerator, long denominator) {
    if (denominator <= 0) return null;
    return BigDecimal.valueOf(numerator)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(denominator), 8, RoundingMode.HALF_UP);
  }

  private BigDecimal divide(BigDecimal numerator, long denominator) {
    if (denominator <= 0) return null;
    return numerator.divide(BigDecimal.valueOf(denominator), 8, RoundingMode.HALF_UP);
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Unable to serialize Google Ads breakdown metrics", exception);
    }
  }

  private record Key(String dimension, String secondary, String actionType) {}

  private static final class Aggregate {
    long costMicros;
    long impressions;
    long clicks;
    long uniqueUsers;
    BigDecimal conversions = BigDecimal.ZERO;
    BigDecimal conversionValue = BigDecimal.ZERO;
    final List<JsonNode> providerRows = new ArrayList<>();

    void add(JsonNode row) {
      JsonNode metrics = row.path("metrics");
      costMicros += longValue(metrics, "costMicros");
      impressions += longValue(metrics, "impressions");
      clicks += longValue(metrics, "clicks");
      uniqueUsers += longValue(metrics, "uniqueUsers");
      conversions = conversions.add(decimal(metrics, "conversions"));
      conversionValue = conversionValue.add(decimal(metrics, "conversionsValue"));
      providerRows.add(row);
    }

    private static long longValue(JsonNode node, String field) {
      String value = node.path(field).asText("0");
      try {
        return new BigDecimal(value).longValue();
      } catch (NumberFormatException ignored) {
        return 0L;
      }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
      String value = node.path(field).asText("0");
      try {
        return new BigDecimal(value);
      } catch (NumberFormatException ignored) {
        return BigDecimal.ZERO;
      }
    }
  }
}
