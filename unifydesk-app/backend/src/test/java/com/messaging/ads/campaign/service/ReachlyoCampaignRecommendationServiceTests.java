package com.messaging.ads.campaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.messaging.ads.campaign.dto.ReachlyoCampaignAnalyticsResponse;
import com.messaging.ads.campaign.entity.ProviderDeployment;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.enums.ProviderDeploymentStatus;
import com.messaging.ads.campaign.repository.ProviderDeploymentRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.enums.AdRecommendationSeverity;
import com.messaging.ads.google.GoogleAdsNativeRecommendationService;
import com.messaging.ads.service.AdRecommendationLifecycleService;
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
class ReachlyoCampaignRecommendationServiceTests {

  @Mock private ReachlyoCampaignRepository campaignRepository;
  @Mock private ProviderDeploymentRepository deploymentRepository;
  @Mock private ReachlyoCampaignAnalyticsService analyticsService;
  @Mock private AdRecommendationLifecycleService recommendationLifecycleService;
  @Mock private GoogleAdsNativeRecommendationService googleNativeRecommendationService;

  private ReachlyoCampaignRecommendationService service;

  @BeforeEach
  void setUp() {
    service =
        new ReachlyoCampaignRecommendationService(
            campaignRepository,
            deploymentRepository,
            analyticsService,
            recommendationLifecycleService,
            googleNativeRecommendationService);
  }

  @Test
  void surfacesFailedDeploymentAndCrossProviderCpaDivergence() {
    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(10L);
    campaign.setCompanyId(1L);
    campaign.setName("Launch campaign");
    when(campaignRepository.findByIdAndCompanyId(10L, 1L))
        .thenReturn(Optional.of(campaign));

    ProviderDeployment google = deployment(
        20L,
        AdProvider.GOOGLE_ADS,
        30L,
        100L,
        "111",
        ProviderDeploymentStatus.ACTIVE);
    ProviderDeployment meta = deployment(
        21L,
        AdProvider.META_ADS,
        31L,
        200L,
        "222",
        ProviderDeploymentStatus.PARTIAL_FAILURE);
    meta.setLastErrorCode("META_CREATE_FAILED");
    meta.setLastErrorMessage("Meta rejected one remote object");

    when(deploymentRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(1L, 10L))
        .thenReturn(List.of(google, meta));

    LocalDate to = LocalDate.now(java.time.ZoneOffset.UTC);
    LocalDate from = to.minusDays(6);
    when(analyticsService.analytics(1L, 10L, from, to))
        .thenReturn(
            new ReachlyoCampaignAnalyticsResponse(
                "10",
                "INR",
                from,
                to,
                summary("700", "14", "1400"),
                List.of(),
                List.of(
                    deploymentRow(
                        google,
                        CampaignChannelType.GOOGLE_SEARCH,
                        summary("300", "10", "900")),
                    deploymentRow(
                        meta,
                        CampaignChannelType.META_ADS,
                        summary("400", "4", "500"))),
                true,
                Instant.now()));

    when(recommendationLifecycleService.feed(1L, 30L))
        .thenReturn(emptyFeed("30"));
    when(recommendationLifecycleService.feed(1L, 31L))
        .thenReturn(emptyFeed("31"));
    when(googleNativeRecommendationService.list(1L, 30L))
        .thenReturn(List.of());

    var result = service.recommendations(1L, 10L);

    assertThat(result.items())
        .anySatisfy(
            item -> {
              assertThat(item.key()).contains("DEPLOYMENT_PARTIAL_FAILURE");
              assertThat(item.severity()).isEqualTo(AdRecommendationSeverity.CRITICAL);
              assertThat(item.provider()).isEqualTo(AdProvider.META_ADS);
            });
    assertThat(result.items())
        .anySatisfy(
            item -> {
              assertThat(item.key()).isEqualTo("CROSS_PROVIDER_CPA_DIVERGENCE");
              assertThat(item.severity()).isEqualTo(AdRecommendationSeverity.OPPORTUNITY);
              assertThat(item.evidence()).containsKey("cpaRatio");
            });
  }

  @Test
  void doesNotCreateCpaDivergenceFromTinyConversionSamples() {
    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(10L);
    campaign.setCompanyId(1L);
    campaign.setName("Small sample campaign");
    when(campaignRepository.findByIdAndCompanyId(10L, 1L))
        .thenReturn(Optional.of(campaign));

    ProviderDeployment google = deployment(
        20L,
        AdProvider.GOOGLE_ADS,
        30L,
        100L,
        "111",
        ProviderDeploymentStatus.ACTIVE);
    ProviderDeployment meta = deployment(
        21L,
        AdProvider.META_ADS,
        31L,
        200L,
        "222",
        ProviderDeploymentStatus.ACTIVE);
    when(deploymentRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(1L, 10L))
        .thenReturn(List.of(google, meta));

    LocalDate to = LocalDate.now(java.time.ZoneOffset.UTC);
    LocalDate from = to.minusDays(6);
    when(analyticsService.analytics(1L, 10L, from, to))
        .thenReturn(
            new ReachlyoCampaignAnalyticsResponse(
                "10",
                "INR",
                from,
                to,
                summary("300", "3", "500"),
                List.of(),
                List.of(
                    deploymentRow(
                        google,
                        CampaignChannelType.GOOGLE_SEARCH,
                        summary("100", "2", "300")),
                    deploymentRow(
                        meta,
                        CampaignChannelType.META_ADS,
                        summary("200", "1", "200"))),
                true,
                Instant.now()));

    when(recommendationLifecycleService.feed(1L, 30L))
        .thenReturn(emptyFeed("30"));
    when(recommendationLifecycleService.feed(1L, 31L))
        .thenReturn(emptyFeed("31"));
    when(googleNativeRecommendationService.list(1L, 30L))
        .thenReturn(List.of());

    var result = service.recommendations(1L, 10L);

    assertThat(result.items())
        .noneMatch(item -> item.key().equals("CROSS_PROVIDER_CPA_DIVERGENCE"));
  }

  private ProviderDeployment deployment(
      Long id,
      AdProvider provider,
      Long accountId,
      Long localCampaignId,
      String externalCampaignId,
      ProviderDeploymentStatus status) {
    ProviderDeployment deployment = new ProviderDeployment();
    deployment.setId(id);
    deployment.setCompanyId(1L);
    deployment.setReachlyoCampaignId(10L);
    deployment.setCampaignChannelId(id + 100L);
    deployment.setProvider(provider);
    deployment.setAdAccountId(accountId);
    deployment.setLocalCampaignId(localCampaignId);
    deployment.setExternalCampaignId(externalCampaignId);
    deployment.setDeploymentStatus(status);
    return deployment;
  }

  private ReachlyoCampaignAnalyticsResponse.DeploymentRow deploymentRow(
      ProviderDeployment deployment,
      CampaignChannelType type,
      ReachlyoCampaignAnalyticsResponse.Summary summary) {
    return new ReachlyoCampaignAnalyticsResponse.DeploymentRow(
        deployment.getId().toString(),
        deployment.getCampaignChannelId().toString(),
        deployment.getProvider(),
        type,
        deployment.getAdAccountId().toString(),
        deployment.getLocalCampaignId().toString(),
        deployment.getExternalCampaignId(),
        "ACTIVE",
        deployment.getDeploymentStatus(),
        summary,
        BigDecimal.valueOf(50),
        true,
        Instant.now());
  }

  private ReachlyoCampaignAnalyticsResponse.Summary summary(
      String spend, String conversions, String value) {
    BigDecimal spendValue = new BigDecimal(spend);
    BigDecimal conversionValue = new BigDecimal(conversions);
    return new ReachlyoCampaignAnalyticsResponse.Summary(
        spendValue,
        1000,
        100,
        BigDecimal.TEN,
        conversionValue,
        new BigDecimal(value),
        conversionValue.signum() == 0
            ? null
            : spendValue.divide(conversionValue, 8, java.math.RoundingMode.HALF_UP),
        spendValue.signum() == 0
            ? null
            : new BigDecimal(value).divide(spendValue, 8, java.math.RoundingMode.HALF_UP),
        conversionValue,
        BigDecimal.ZERO);
  }

  private com.messaging.ads.dto.AdRecommendationsResponse emptyFeed(String accountId) {
    return new com.messaging.ads.dto.AdRecommendationsResponse(
        accountId,
        null,
        null,
        0,
        0,
        0,
        0,
        0,
        0,
        Instant.now(),
        null,
        null,
        Instant.now(),
        List.of(),
        List.of());
  }
}
