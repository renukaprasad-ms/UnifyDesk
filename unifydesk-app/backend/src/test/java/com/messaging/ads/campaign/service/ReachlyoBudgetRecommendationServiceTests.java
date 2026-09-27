package com.messaging.ads.campaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.messaging.ads.campaign.dto.ReachlyoCampaignAnalyticsResponse;
import com.messaging.ads.campaign.entity.BudgetPlan;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.enums.BudgetAllocationMode;
import com.messaging.ads.campaign.enums.BudgetPeriod;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.enums.ProviderDeploymentStatus;
import com.messaging.ads.campaign.repository.BudgetPlanRepository;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.enums.AdProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReachlyoBudgetRecommendationServiceTests {

  @Mock private ReachlyoCampaignAnalyticsService analyticsService;
  @Mock private BudgetPlanRepository budgetPlanRepository;
  @Mock private CampaignChannelRepository channelRepository;

  private ReachlyoBudgetRecommendationService service;

  @BeforeEach
  void setUp() {
    service =
        new ReachlyoBudgetRecommendationService(
            analyticsService, budgetPlanRepository, channelRepository);
  }

  @Test
  void recommendsHistoricalAllocationWithExplorationFloor() {
    LocalDate from = LocalDate.of(2026, 9, 1);
    LocalDate to = LocalDate.of(2026, 9, 30);

    var strong =
        new ReachlyoCampaignAnalyticsResponse.DeploymentRow(
            "1", "11", AdProvider.GOOGLE_ADS, CampaignChannelType.GOOGLE_SEARCH,
            "20", "30", "900", "ENABLED", ProviderDeploymentStatus.ACTIVE,
            summary("600", "30", "3000"), new BigDecimal("60"), true, null);
    var weak =
        new ReachlyoCampaignAnalyticsResponse.DeploymentRow(
            "2", "12", AdProvider.META_ADS, CampaignChannelType.META_ADS,
            "21", "31", "901", "ACTIVE", ProviderDeploymentStatus.ACTIVE,
            summary("400", "10", "400"), new BigDecimal("40"), true, null);
    var analytics =
        new ReachlyoCampaignAnalyticsResponse(
            "100", "INR", from, to,
            summary("1000", "40", "3400"),
            List.of(),
            List.of(strong, weak),
            true,
            null);
    when(analyticsService.analytics(10L, 100L, from, to)).thenReturn(analytics);

    BudgetPlan budget = new BudgetPlan();
    budget.setCompanyId(10L);
    budget.setReachlyoCampaignId(100L);
    budget.setTotalBudgetMinor(100_000L);
    budget.setBudgetPeriod(BudgetPeriod.TOTAL);
    budget.setAllocationMode(BudgetAllocationMode.PERCENTAGE);
    when(budgetPlanRepository.findByCompanyIdAndReachlyoCampaignId(10L, 100L))
        .thenReturn(Optional.of(budget));

    CampaignChannel google = new CampaignChannel();
    google.setId(11L);
    google.setAllocationPercent(new BigDecimal("50"));
    CampaignChannel meta = new CampaignChannel();
    meta.setId(12L);
    meta.setAllocationPercent(new BigDecimal("50"));
    when(channelRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(10L, 100L))
        .thenReturn(List.of(google, meta));

    var result = service.recommend(10L, 100L, from, to);

    assertThat(result.actionable()).isTrue();
    assertThat(result.confidence()).isEqualTo("HIGH");
    assertThat(result.channels()).hasSize(2);
    assertThat(result.channels().stream().map(value -> value.recommendedPercent()).reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("100.00");
    assertThat(result.channels().stream()
        .filter(value -> value.provider() == AdProvider.GOOGLE_ADS)
        .findFirst().orElseThrow().recommendedPercent())
        .isGreaterThan(new BigDecimal("50"));
    assertThat(result.methodology()).contains("not a forecast");
  }

  private ReachlyoCampaignAnalyticsResponse.Summary summary(
      String spend, String conversions, String conversionValue) {
    BigDecimal spendValue = new BigDecimal(spend);
    BigDecimal conversionCount = new BigDecimal(conversions);
    BigDecimal value = new BigDecimal(conversionValue);
    return new ReachlyoCampaignAnalyticsResponse.Summary(
        spendValue,
        10_000L,
        1_000L,
        new BigDecimal("10"),
        conversionCount,
        value,
        spendValue.divide(conversionCount, 8, java.math.RoundingMode.HALF_UP),
        value.divide(spendValue, 8, java.math.RoundingMode.HALF_UP),
        conversionCount,
        conversionCount.divide(new BigDecimal("2"), 8, java.math.RoundingMode.HALF_UP));
  }
}
