package com.messaging.ads.campaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.ProviderDeployment;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.enums.ProviderDeploymentStatus;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.ProviderDeploymentRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.entity.AdInsightDaily;
import com.messaging.ads.enums.AdInsightGranularity;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsInsightSyncService;
import com.messaging.ads.meta.MetaAdsInsightSyncService;
import com.messaging.ads.repository.AdInsightDailyRepository;
import com.messaging.common.exception.BadRequestException;
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
class ReachlyoCampaignAnalyticsServiceTests {

  @Mock private ReachlyoCampaignRepository campaignRepository;
  @Mock private ProviderDeploymentRepository deploymentRepository;
  @Mock private CampaignChannelRepository channelRepository;
  @Mock private AdInsightDailyRepository insightRepository;
  @Mock private GoogleAdsInsightSyncService googleInsightSyncService;
  @Mock private MetaAdsInsightSyncService metaInsightSyncService;

  private ReachlyoCampaignAnalyticsService service;
  private final LocalDate from = LocalDate.of(2026, 9, 1);
  private final LocalDate to = LocalDate.of(2026, 9, 7);

  @BeforeEach
  void setUp() {
    service =
        new ReachlyoCampaignAnalyticsService(
            campaignRepository,
            deploymentRepository,
            channelRepository,
            insightRepository,
            googleInsightSyncService,
            metaInsightSyncService);
  }

  @Test
  void aggregatesExactProviderCampaignRollupsAndSpendShare() {
    stubCampaignGraph();

    AdInsightDaily googleRange =
        insight(
            100L,
            AdProvider.GOOGLE_ADS,
            from,
            to,
            AdInsightGranularity.RANGE,
            "100",
            "60",
            1000,
            100,
            "10",
            "120");
    AdInsightDaily metaRange =
        insight(
            200L,
            AdProvider.META_ADS,
            from,
            to,
            AdInsightGranularity.RANGE,
            "200",
            "40",
            2000,
            80,
            "20",
            "160");
    when(insightRepository
            .findByCompanyIdAndCampaignIdInAndInsightLevelAndGranularityAndInsightDateBetweenOrderByInsightDateAsc(
                1L,
                List.of(100L, 200L),
                AdInsightLevel.CAMPAIGN,
                AdInsightGranularity.DAILY,
                from,
                to))
        .thenReturn(
            List.of(
                insight(
                    100L,
                    AdProvider.GOOGLE_ADS,
                    from,
                    from,
                    AdInsightGranularity.DAILY,
                    "20",
                    "10",
                    100,
                    20,
                    "2",
                    "24"),
                insight(
                    200L,
                    AdProvider.META_ADS,
                    from,
                    from,
                    AdInsightGranularity.DAILY,
                    "40",
                    "8",
                    200,
                    10,
                    "4",
                    "32")));
    when(insightRepository
            .findByCompanyIdAndCampaignIdInAndInsightLevelAndGranularityAndInsightDateAndPeriodEnd(
                1L,
                List.of(100L, 200L),
                AdInsightLevel.CAMPAIGN,
                AdInsightGranularity.RANGE,
                from,
                to))
        .thenReturn(List.of(googleRange, metaRange));

    var result = service.analytics(1L, 10L, from, to);

    assertThat(result.exactRangeAvailable()).isTrue();
    assertThat(result.summary().spend()).isEqualByComparingTo("300");
    assertThat(result.summary().conversions()).isEqualByComparingTo("30");
    assertThat(result.summary().conversionValue()).isEqualByComparingTo("280");
    assertThat(result.summary().cpa()).isEqualByComparingTo("10");
    assertThat(result.deployments()).hasSize(2);
    assertThat(result.deployments().getFirst().provider()).isEqualTo(AdProvider.META_ADS);
    assertThat(result.deployments().getFirst().spendSharePercent())
        .isEqualByComparingTo("66.6667");
    assertThat(result.daily()).singleElement().satisfies(
        point -> assertThat(point.spend()).isEqualByComparingTo("60"));
  }

  @Test
  void fallsBackToDailyWhenAnyDeploymentLacksExactRange() {
    stubCampaignGraph();
    when(insightRepository
            .findByCompanyIdAndCampaignIdInAndInsightLevelAndGranularityAndInsightDateBetweenOrderByInsightDateAsc(
                any(), any(), any(), any(), any(), any()))
        .thenReturn(
            List.of(
                insight(
                    100L,
                    AdProvider.GOOGLE_ADS,
                    from,
                    from,
                    AdInsightGranularity.DAILY,
                    "15",
                    "3",
                    100,
                    10,
                    "1",
                    "20"),
                insight(
                    200L,
                    AdProvider.META_ADS,
                    from,
                    from,
                    AdInsightGranularity.DAILY,
                    "25",
                    "5",
                    150,
                    15,
                    "2",
                    "30")));
    when(insightRepository
            .findByCompanyIdAndCampaignIdInAndInsightLevelAndGranularityAndInsightDateAndPeriodEnd(
                any(), any(), any(), any(), any(), any()))
        .thenReturn(
            List.of(
                insight(
                    100L,
                    AdProvider.GOOGLE_ADS,
                    from,
                    to,
                    AdInsightGranularity.RANGE,
                    "100",
                    "10",
                    1000,
                    100,
                    "10",
                    "200")));

    var result = service.analytics(1L, 10L, from, to);

    assertThat(result.exactRangeAvailable()).isFalse();
    assertThat(result.summary().spend()).isEqualByComparingTo("40");
    assertThat(result.summary().conversions()).isEqualByComparingTo("3");
  }

  @Test
  void rejectsMixedCurrencyAggregation() {
    stubCampaignGraph();
    AdInsightDaily row =
        insight(
            100L,
            AdProvider.GOOGLE_ADS,
            from,
            from,
            AdInsightGranularity.DAILY,
            "10",
            "2",
            100,
            10,
            "1",
            "5");
    row.setCurrency("USD");
    when(insightRepository
            .findByCompanyIdAndCampaignIdInAndInsightLevelAndGranularityAndInsightDateBetweenOrderByInsightDateAsc(
                any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(row));
    when(insightRepository
            .findByCompanyIdAndCampaignIdInAndInsightLevelAndGranularityAndInsightDateAndPeriodEnd(
                any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.analytics(1L, 10L, from, to))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("mixed currencies");
  }

  private void stubCampaignGraph() {
    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(10L);
    campaign.setCompanyId(1L);
    campaign.setCurrency("INR");
    when(campaignRepository.findByIdAndCompanyId(10L, 1L))
        .thenReturn(Optional.of(campaign));

    CampaignChannel google = new CampaignChannel();
    google.setId(20L);
    google.setChannelType(CampaignChannelType.GOOGLE_SEARCH);
    CampaignChannel meta = new CampaignChannel();
    meta.setId(21L);
    meta.setChannelType(CampaignChannelType.META_ADS);
    when(channelRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(1L, 10L))
        .thenReturn(List.of(google, meta));

    ProviderDeployment googleDeployment = new ProviderDeployment();
    googleDeployment.setId(30L);
    googleDeployment.setCompanyId(1L);
    googleDeployment.setReachlyoCampaignId(10L);
    googleDeployment.setCampaignChannelId(20L);
    googleDeployment.setProvider(AdProvider.GOOGLE_ADS);
    googleDeployment.setAdAccountId(40L);
    googleDeployment.setLocalCampaignId(100L);
    googleDeployment.setExternalCampaignId("g-100");
    googleDeployment.setDeploymentStatus(ProviderDeploymentStatus.ACTIVE);

    ProviderDeployment metaDeployment = new ProviderDeployment();
    metaDeployment.setId(31L);
    metaDeployment.setCompanyId(1L);
    metaDeployment.setReachlyoCampaignId(10L);
    metaDeployment.setCampaignChannelId(21L);
    metaDeployment.setProvider(AdProvider.META_ADS);
    metaDeployment.setAdAccountId(41L);
    metaDeployment.setLocalCampaignId(200L);
    metaDeployment.setExternalCampaignId("m-200");
    metaDeployment.setDeploymentStatus(ProviderDeploymentStatus.ACTIVE);

    when(deploymentRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(1L, 10L))
        .thenReturn(List.of(googleDeployment, metaDeployment));
  }

  private AdInsightDaily insight(
      Long campaignId,
      AdProvider provider,
      LocalDate date,
      LocalDate periodEnd,
      AdInsightGranularity granularity,
      String spend,
      String conversions,
      long impressions,
      long clicks,
      String leads,
      String conversionValue) {
    AdInsightDaily row = new AdInsightDaily();
    row.setCampaignId(campaignId);
    row.setProvider(provider);
    row.setInsightLevel(AdInsightLevel.CAMPAIGN);
    row.setGranularity(granularity);
    row.setInsightDate(date);
    row.setPeriodEnd(periodEnd);
    row.setCurrency("INR");
    row.setSpend(new BigDecimal(spend));
    row.setConversions(new BigDecimal(conversions));
    row.setConversionValue(new BigDecimal(conversionValue));
    row.setLeads(new BigDecimal(leads));
    row.setPurchases(BigDecimal.ZERO);
    row.setImpressions(impressions);
    row.setClicks(clicks);
    row.setLastSyncAt(Instant.parse("2026-09-25T00:00:00Z"));
    return row;
  }
}
