package com.messaging.ads.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAccountBilling;
import com.messaging.ads.entity.AdAccountHealth;
import com.messaging.ads.entity.AdAccountResource;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdConversion;
import com.messaging.ads.entity.AdInsightDaily;
import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdInsightGranularity;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAccountBillingRepository;
import com.messaging.ads.repository.AdAccountHealthRepository;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAccountResourceRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdConversionRepository;
import com.messaging.ads.repository.AdInsightDailyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdRecommendationServiceTests {

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdAccountHealthRepository healthRepository;
  @Mock private AdAccountBillingRepository billingRepository;
  @Mock private AdAccountResourceRepository resourceRepository;
  @Mock private AdConversionRepository conversionRepository;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdInsightDailyRepository insightRepository;

  private AdRecommendationService service;

  @BeforeEach
  void setUp() {
    service =
        new AdRecommendationService(
            accountRepository,
            healthRepository,
            billingRepository,
            resourceRepository,
            conversionRepository,
            campaignRepository,
            insightRepository,
            new ObjectMapper());
  }

  @Test
  void surfacesProviderHealthAndSpendCapRisks() {
    AdAccount account = account();
    when(accountRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(account));

    AdAccountHealth health = new AdAccountHealth();
    health.setCompanyId(10L);
    health.setAdAccountId(20L);
    health.setProvider(AdProvider.META_ADS);
    health.setDisableReason(7);
    health.setPageAuthorized(false);
    health.setOffsitePixelsTosAccepted(false);
    health.setAccessExpiresAt(Instant.now().plusSeconds(2 * 24 * 60 * 60));
    health.setFailedDeliveryChecksJson(
        "[{\"check_name\":\"PAYMENT_METHOD\","
            + "\"summary\":\"Payment method needs attention\","
            + "\"description\":\"Resolve billing in Meta.\"}]");
    health.setLastSyncAt(Instant.now());
    when(healthRepository.findByCompanyIdAndAdAccountId(10L, 20L))
        .thenReturn(Optional.of(health));

    AdAccountBilling billing = new AdAccountBilling();
    billing.setCompanyId(10L);
    billing.setAdAccountId(20L);
    billing.setProvider(AdProvider.META_ADS);
    billing.setSpendCapMinor(10000L);
    billing.setAmountSpentMinor(9500L);
    billing.setLastSyncAt(Instant.now());
    when(billingRepository.findByCompanyIdAndAdAccountId(10L, 20L))
        .thenReturn(Optional.of(billing));

    when(resourceRepository.findByCompanyIdAndAdAccountId(10L, 20L))
        .thenReturn(
            List.of(
                resource(AdAccountResourceType.FACEBOOK_PAGE),
                resource(AdAccountResourceType.INSTAGRAM_ACCOUNT),
                resource(AdAccountResourceType.META_PIXEL)));
    when(conversionRepository.findByCompanyIdAndAdAccountIdOrderByLastFiredAtDesc(
            10L, 20L))
        .thenReturn(List.of(conversion(Instant.now())));
    when(campaignRepository.findByCompanyIdAndAdAccountId(10L, 20L))
        .thenReturn(List.of());
    whenRecentInsights(List.of());

    var response = service.recommendations(10L, 20L);

    assertThat(response.items())
        .extracting(item -> item.key())
        .contains(
            "META_ADS_DELIVERY_PAYMENT_METHOD",
            "META_DISABLE_REASON_7",
            "PAGE_NOT_AUTHORIZED",
            "PIXEL_TERMS_NOT_ACCEPTED",
            "ACCESS_EXPIRING",
            "SPEND_CAP_LOW");
    assertThat(response.criticalCount()).isGreaterThanOrEqualTo(2);
    assertThat(response.warningCount()).isGreaterThanOrEqualTo(4);
  }

  @Test
  void flagsConversionCampaignSpendWithoutConversionsOnlyWhenAnalyticsExist() {
    AdAccount account = account();
    when(accountRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(account));
    when(healthRepository.findByCompanyIdAndAdAccountId(10L, 20L))
        .thenReturn(Optional.of(cleanHealth()));
    when(billingRepository.findByCompanyIdAndAdAccountId(10L, 20L))
        .thenReturn(Optional.of(cleanBilling()));
    when(resourceRepository.findByCompanyIdAndAdAccountId(10L, 20L))
        .thenReturn(
            List.of(
                resource(AdAccountResourceType.FACEBOOK_PAGE),
                resource(AdAccountResourceType.INSTAGRAM_ACCOUNT),
                resource(AdAccountResourceType.META_PIXEL)));
    when(conversionRepository.findByCompanyIdAndAdAccountIdOrderByLastFiredAtDesc(
            10L, 20L))
        .thenReturn(List.of(conversion(Instant.now())));

    AdCampaign campaign = campaign();
    when(campaignRepository.findByCompanyIdAndAdAccountId(10L, 20L))
        .thenReturn(List.of(campaign));

    AdInsightDaily row = new AdInsightDaily();
    row.setCompanyId(10L);
    row.setAdAccountId(20L);
    row.setCampaignId(30L);
    row.setProvider(AdProvider.META_ADS);
    row.setInsightLevel(AdInsightLevel.CAMPAIGN);
    row.setGranularity(AdInsightGranularity.DAILY);
    row.setInsightDate(LocalDate.now(ZoneOffset.UTC));
    row.setPeriodEnd(LocalDate.now(ZoneOffset.UTC));
    row.setEntityRef("campaign-external");
    row.setSpend(new BigDecimal("120.00"));
    row.setClicks(18L);
    row.setConversions(BigDecimal.ZERO);
    row.setLastSyncAt(Instant.now());
    whenRecentInsights(List.of(row));

    var response = service.recommendations(10L, 20L);

    assertThat(response.items())
        .extracting(item -> item.key())
        .contains("SPEND_WITHOUT_CONVERSIONS_30");
  }

  @Test
  void doesNotInferBadPerformanceWhenAnalyticsAreMissing() {
    AdAccount account = account();
    when(accountRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(account));
    when(healthRepository.findByCompanyIdAndAdAccountId(10L, 20L))
        .thenReturn(Optional.of(cleanHealth()));
    when(billingRepository.findByCompanyIdAndAdAccountId(10L, 20L))
        .thenReturn(Optional.of(cleanBilling()));
    when(resourceRepository.findByCompanyIdAndAdAccountId(10L, 20L))
        .thenReturn(
            List.of(
                resource(AdAccountResourceType.FACEBOOK_PAGE),
                resource(AdAccountResourceType.INSTAGRAM_ACCOUNT),
                resource(AdAccountResourceType.META_PIXEL)));
    when(conversionRepository.findByCompanyIdAndAdAccountIdOrderByLastFiredAtDesc(
            10L, 20L))
        .thenReturn(List.of(conversion(Instant.now())));
    when(campaignRepository.findByCompanyIdAndAdAccountId(10L, 20L))
        .thenReturn(List.of(campaign()));
    whenRecentInsights(List.of());

    var response = service.recommendations(10L, 20L);

    assertThat(response.items())
        .extracting(item -> item.key())
        .doesNotContain("SPEND_WITHOUT_CONVERSIONS_30");
  }

  private void whenRecentInsights(List<AdInsightDaily> rows) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    when(
            insightRepository
                .findByCompanyIdAndAdAccountIdAndInsightLevelAndGranularityAndInsightDateBetweenOrderByInsightDateAsc(
                    10L,
                    20L,
                    AdInsightLevel.CAMPAIGN,
                    AdInsightGranularity.DAILY,
                    today.minusDays(6),
                    today))
        .thenReturn(rows);
  }

  private AdAccount account() {
    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId("123456");
    account.setName("Acme Ads");
    account.setStatus(AdAccountStatus.ACTIVE);
    account.setManaged(true);
    account.setAccessible(true);
    return account;
  }

  private AdAccountHealth cleanHealth() {
    AdAccountHealth health = new AdAccountHealth();
    health.setCompanyId(10L);
    health.setAdAccountId(20L);
    health.setProvider(AdProvider.META_ADS);
    health.setPageAuthorized(true);
    health.setOffsitePixelsTosAccepted(true);
    health.setLastSyncAt(Instant.now());
    return health;
  }

  private AdAccountBilling cleanBilling() {
    AdAccountBilling billing = new AdAccountBilling();
    billing.setCompanyId(10L);
    billing.setAdAccountId(20L);
    billing.setProvider(AdProvider.META_ADS);
    billing.setLastSyncAt(Instant.now());
    return billing;
  }

  private AdAccountResource resource(AdAccountResourceType type) {
    AdAccountResource resource = new AdAccountResource();
    resource.setCompanyId(10L);
    resource.setAdAccountId(20L);
    resource.setProvider(AdProvider.META_ADS);
    resource.setResourceType(type);
    resource.setExternalResourceId(type.name());
    resource.setAccessible(true);
    return resource;
  }

  private AdConversion conversion(Instant lastFiredAt) {
    AdConversion conversion = new AdConversion();
    conversion.setCompanyId(10L);
    conversion.setAdAccountId(20L);
    conversion.setProvider(AdProvider.META_ADS);
    conversion.setExternalConversionId("conversion-1");
    conversion.setName("Purchase");
    conversion.setAccessible(true);
    conversion.setArchived(false);
    conversion.setUnavailable(false);
    conversion.setLastFiredAt(lastFiredAt);
    return conversion;
  }

  private AdCampaign campaign() {
    AdCampaign campaign = new AdCampaign();
    campaign.setId(30L);
    campaign.setCompanyId(10L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.META_ADS);
    campaign.setExternalCampaignId("campaign-external");
    campaign.setName("Sales campaign");
    campaign.setObjective("OUTCOME_SALES");
    campaign.setStatus(AdEntityStatus.ACTIVE);
    campaign.setEffectiveStatus("ACTIVE");
    campaign.setAccessible(true);
    return campaign;
  }
}
