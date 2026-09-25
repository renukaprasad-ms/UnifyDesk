package com.messaging.ads.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdInsightDaily;
import com.messaging.ads.enums.AdInsightGranularity;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdInsightDailyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdAnalyticsServiceTests {

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdInsightDailyRepository insightRepository;

  private AdAnalyticsService service;

  @BeforeEach
  void setUp() {
    service = new AdAnalyticsService(accountRepository, insightRepository);
  }

  @Test
  void usesExactRangeRollupForReachAndDailyRowsForTrend() {
    LocalDate from = LocalDate.of(2026, 9, 1);
    LocalDate to = LocalDate.of(2026, 9, 2);

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.META_ADS);
    account.setCurrency("INR");
    when(accountRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(account));

    AdInsightDaily dayOne = row(from, from, AdInsightGranularity.DAILY, 100, 80);
    AdInsightDaily dayTwo = row(to, to, AdInsightGranularity.DAILY, 120, 90);
    AdInsightDaily exact = row(from, to, AdInsightGranularity.RANGE, 220, 150);

    when(insightRepository
            .findByCompanyIdAndAdAccountIdAndInsightLevelAndGranularityAndInsightDateBetweenOrderByInsightDateAsc(
                10L,
                20L,
                AdInsightLevel.ACCOUNT,
                AdInsightGranularity.DAILY,
                from,
                to))
        .thenReturn(List.of(dayOne, dayTwo));
    when(insightRepository
            .findByCompanyIdAndAdAccountIdAndInsightLevelAndGranularityAndInsightDateAndPeriodEnd(
                10L,
                20L,
                AdInsightLevel.ACCOUNT,
                AdInsightGranularity.RANGE,
                from,
                to))
        .thenReturn(List.of(exact));
    when(insightRepository
            .findByCompanyIdAndAdAccountIdAndInsightLevelAndGranularityAndInsightDateAndPeriodEnd(
                10L,
                20L,
                AdInsightLevel.CAMPAIGN,
                AdInsightGranularity.RANGE,
                from,
                to))
        .thenReturn(List.of());

    var response =
        service.analytics(10L, 20L, from, to, AdInsightLevel.CAMPAIGN);

    assertThat(response.exactRangeAvailable()).isTrue();
    assertThat(response.summary().reach()).isEqualTo(150);
    assertThat(response.summary().spend()).isEqualByComparingTo("220");
    assertThat(response.daily()).hasSize(2);
  }

  private AdInsightDaily row(
      LocalDate start,
      LocalDate end,
      AdInsightGranularity granularity,
      long spend,
      long reach) {
    AdInsightDaily row = new AdInsightDaily();
    row.setAdAccountId(20L);
    row.setInsightLevel(AdInsightLevel.ACCOUNT);
    row.setInsightDate(start);
    row.setPeriodEnd(end);
    row.setGranularity(granularity);
    row.setCurrency("INR");
    row.setSpend(BigDecimal.valueOf(spend));
    row.setReach(reach);
    row.setImpressions(spend * 10);
    row.setClicks(spend);
    row.setConversions(BigDecimal.TEN);
    row.setConversionValue(BigDecimal.valueOf(spend * 2));
    row.setLeads(BigDecimal.ONE);
    row.setPurchases(BigDecimal.ONE);
    row.setLastSyncAt(Instant.parse("2026-09-23T08:00:00Z"));
    return row;
  }
}
