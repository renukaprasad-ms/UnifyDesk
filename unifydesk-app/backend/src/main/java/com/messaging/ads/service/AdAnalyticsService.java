package com.messaging.ads.service;

import com.messaging.ads.dto.AdAnalyticsResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdInsightDaily;
import com.messaging.ads.enums.AdInsightGranularity;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdInsightDailyRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdAnalyticsService {

  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private final AdAccountRepository accountRepository;
  private final AdInsightDailyRepository insightRepository;

  @Transactional(readOnly = true)
  public AdAnalyticsResponse analytics(
      Long companyId,
      Long accountId,
      LocalDate from,
      LocalDate to,
      AdInsightLevel entityLevel) {
    if (from == null || to == null || to.isBefore(from)) {
      throw new BadRequestException("A valid analytics date range is required");
    }

    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));

    List<AdInsightDaily> daily =
        insightRepository
            .findByCompanyIdAndAdAccountIdAndInsightLevelAndGranularityAndInsightDateBetweenOrderByInsightDateAsc(
                companyId,
                accountId,
                AdInsightLevel.ACCOUNT,
                AdInsightGranularity.DAILY,
                from,
                to);

    List<AdInsightDaily> accountRollups =
        insightRepository
            .findByCompanyIdAndAdAccountIdAndInsightLevelAndGranularityAndInsightDateAndPeriodEnd(
                companyId,
                accountId,
                AdInsightLevel.ACCOUNT,
                AdInsightGranularity.RANGE,
                from,
                to);

    AdInsightDaily exact = accountRollups.stream().findFirst().orElse(null);
    boolean exactAvailable = exact != null;

    AdAnalyticsResponse.Summary summary =
        exactAvailable ? summary(exact) : additiveSummary(account, daily);

    List<AdInsightDaily> entityRollups =
        insightRepository
            .findByCompanyIdAndAdAccountIdAndInsightLevelAndGranularityAndInsightDateAndPeriodEnd(
                companyId,
                accountId,
                entityLevel,
                AdInsightGranularity.RANGE,
                from,
                to);

    List<AdAnalyticsResponse.EntityRow> entities =
        entityRollups.stream()
            .map(this::entity)
            .sorted(
                Comparator.comparing(
                        AdAnalyticsResponse.EntityRow::spend,
                        Comparator.nullsFirst(BigDecimal::compareTo))
                    .reversed())
            .toList();

    List<AdAnalyticsResponse.DailyPoint> dailyPoints =
        daily.stream().map(this::daily).toList();

    Instant lastSyncedAt =
        entityRollups.stream()
            .map(AdInsightDaily::getLastSyncAt)
            .filter(value -> value != null)
            .max(Instant::compareTo)
            .orElseGet(
                () ->
                    daily.stream()
                        .map(AdInsightDaily::getLastSyncAt)
                        .filter(value -> value != null)
                        .max(Instant::compareTo)
                        .orElse(null));

    return new AdAnalyticsResponse(
        summary, dailyPoints, entities, exactAvailable, lastSyncedAt);
  }

  private AdAnalyticsResponse.Summary summary(AdInsightDaily value) {
    return new AdAnalyticsResponse.Summary(
        value.getCurrency(),
        nz(value.getSpend()),
        value.getImpressions(),
        value.getReach(),
        value.getClicks(),
        value.getCtr(),
        value.getCpc(),
        value.getCpm(),
        nz(value.getConversions()),
        nz(value.getConversionValue()),
        value.getCpa(),
        value.getRoas(),
        nz(value.getLeads()),
        nz(value.getPurchases()));
  }

  private AdAnalyticsResponse.Summary additiveSummary(
      AdAccount account, List<AdInsightDaily> daily) {
    BigDecimal spend = ZERO;
    long impressions = 0L;
    long clicks = 0L;
    BigDecimal conversions = ZERO;
    BigDecimal conversionValue = ZERO;
    BigDecimal leads = ZERO;
    BigDecimal purchases = ZERO;

    for (AdInsightDaily row : daily) {
      spend = spend.add(nz(row.getSpend()));
      impressions += row.getImpressions();
      clicks += row.getClicks();
      conversions = conversions.add(nz(row.getConversions()));
      conversionValue = conversionValue.add(nz(row.getConversionValue()));
      leads = leads.add(nz(row.getLeads()));
      purchases = purchases.add(nz(row.getPurchases()));
    }

    BigDecimal ctr =
        impressions == 0
            ? null
            : BigDecimal.valueOf(clicks)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(impressions), 8, RoundingMode.HALF_UP);
    BigDecimal cpc = divide(spend, BigDecimal.valueOf(clicks));
    BigDecimal cpm =
        impressions == 0
            ? null
            : spend
                .multiply(BigDecimal.valueOf(1000))
                .divide(BigDecimal.valueOf(impressions), 8, RoundingMode.HALF_UP);
    BigDecimal cpa = divide(spend, conversions);
    BigDecimal roas = divide(conversionValue, spend);

    return new AdAnalyticsResponse.Summary(
        account.getCurrency(),
        spend,
        impressions,
        0L,
        clicks,
        ctr,
        cpc,
        cpm,
        conversions,
        conversionValue,
        cpa,
        roas,
        leads,
        purchases);
  }

  private AdAnalyticsResponse.DailyPoint daily(AdInsightDaily value) {
    return new AdAnalyticsResponse.DailyPoint(
        value.getInsightDate(),
        nz(value.getSpend()),
        value.getImpressions(),
        value.getReach(),
        value.getClicks(),
        nz(value.getConversions()),
        nz(value.getConversionValue()),
        value.getRoas());
  }

  private AdAnalyticsResponse.EntityRow entity(AdInsightDaily value) {
    return new AdAnalyticsResponse.EntityRow(
        entityId(value),
        value.getEntityRef(),
        value.getEntityName(),
        nz(value.getSpend()),
        value.getImpressions(),
        value.getReach(),
        value.getClicks(),
        value.getCtr(),
        nz(value.getConversions()),
        nz(value.getConversionValue()),
        value.getCpa(),
        value.getRoas(),
        nz(value.getLeads()),
        nz(value.getPurchases()));
  }

  private String entityId(AdInsightDaily value) {
    return switch (value.getInsightLevel()) {
      case ACCOUNT -> value.getAdAccountId() == null ? null : value.getAdAccountId().toString();
      case CAMPAIGN -> value.getCampaignId() == null ? null : value.getCampaignId().toString();
      case AD_SET -> value.getAdSetId() == null ? null : value.getAdSetId().toString();
      case AD -> value.getAdId() == null ? null : value.getAdId().toString();
    };
  }

  private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
    if (numerator == null || denominator == null || denominator.signum() == 0) return null;
    return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
  }

  private BigDecimal nz(BigDecimal value) {
    return value == null ? ZERO : value;
  }
}
