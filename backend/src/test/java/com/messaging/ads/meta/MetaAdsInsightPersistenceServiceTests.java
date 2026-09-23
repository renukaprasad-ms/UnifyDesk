package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdInsightDaily;
import com.messaging.ads.enums.AdInsightGranularity;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.InsightSummary;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdInsightDailyRepository;
import com.messaging.ads.repository.AdRepository;
import com.messaging.ads.repository.AdSetRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaAdsInsightPersistenceServiceTests {

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdSetRepository adSetRepository;
  @Mock private AdRepository adRepository;
  @Mock private AdInsightDailyRepository insightRepository;

  private ObjectMapper objectMapper;
  private MetaAdsInsightPersistenceService service;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    service =
        new MetaAdsInsightPersistenceService(
            accountRepository,
            campaignRepository,
            adSetRepository,
            adRepository,
            insightRepository,
            objectMapper);
  }

  @Test
  void normalizesMetaActionsAndRangeMetrics() throws Exception {
    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId("123456");
    account.setName("Acme Ads");
    account.setCurrency("INR");
    account.setAccessible(true);

    when(accountRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(account));
    when(insightRepository.save(any(AdInsightDaily.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    InsightSummary snapshot =
        new InsightSummary(
            "123456",
            "Acme Ads",
            "INR",
            null,
            null,
            null,
            null,
            null,
            null,
            "2026-09-01",
            "2026-09-07",
            "1000.00",
            "10000",
            "7500",
            "500",
            "450",
            "400",
            "5.0",
            "2.0",
            "100.0",
            "1.3333",
            objectMapper.readTree(
                "[{\"action_type\":\"offsite_conversion.fb_pixel_purchase\",\"value\":\"10\"},"
                    + "{\"action_type\":\"offsite_conversion.fb_pixel_lead\",\"value\":\"5\"}]"),
            objectMapper.readTree(
                "[{\"action_type\":\"offsite_conversion.fb_pixel_purchase\",\"value\":\"4000\"}]"),
            objectMapper.readTree(
                "[{\"action_type\":\"offsite_conversion.fb_pixel_purchase\",\"value\":\"10\"},"
                    + "{\"action_type\":\"offsite_conversion.fb_pixel_lead\",\"value\":\"5\"}]"),
            objectMapper.readTree(
                "[{\"action_type\":\"offsite_conversion.fb_pixel_purchase\",\"value\":\"4000\"}]"),
            objectMapper.readTree(
                "[{\"action_type\":\"omni_purchase\",\"value\":\"4.0\"}]"),
            null);

    int count =
        service.persist(
            10L,
            20L,
            AdInsightLevel.ACCOUNT,
            AdInsightGranularity.RANGE,
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 7),
            List.of(snapshot));

    assertThat(count).isEqualTo(1);
    ArgumentCaptor<AdInsightDaily> captor = ArgumentCaptor.forClass(AdInsightDaily.class);
    org.mockito.Mockito.verify(insightRepository).save(captor.capture());

    AdInsightDaily stored = captor.getValue();
    assertThat(stored.getInsightDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(stored.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 9, 7));
    assertThat(stored.getGranularity()).isEqualTo(AdInsightGranularity.RANGE);
    assertThat(stored.getSpend()).isEqualByComparingTo("1000.00");
    assertThat(stored.getPurchases()).isEqualByComparingTo("10");
    assertThat(stored.getLeads()).isEqualByComparingTo("5");
    assertThat(stored.getConversions()).isEqualByComparingTo("15");
    assertThat(stored.getConversionValue()).isEqualByComparingTo("4000");
    assertThat(stored.getRoas()).isEqualByComparingTo(new BigDecimal("4.0"));
  }
}
