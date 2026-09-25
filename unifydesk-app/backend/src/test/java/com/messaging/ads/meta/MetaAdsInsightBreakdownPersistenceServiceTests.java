package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdInsightBreakdown;
import com.messaging.ads.enums.AdInsightBreakdownType;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.InsightSummary;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdInsightBreakdownRepository;
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
class MetaAdsInsightBreakdownPersistenceServiceTests {

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdInsightBreakdownRepository breakdownRepository;

  private ObjectMapper objectMapper;
  private MetaAdsInsightBreakdownPersistenceService service;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    service =
        new MetaAdsInsightBreakdownPersistenceService(
            accountRepository, breakdownRepository, objectMapper);
  }

  @Test
  void persistsAgeBreakdownWithNormalizedMetrics() throws Exception {
    when(accountRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(account()));

    InsightSummary snapshot =
        insight(
            objectMapper,
            "25-34",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    int count =
        service.persist(
            10L,
            20L,
            AdInsightBreakdownType.AGE,
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 30),
            List.of(snapshot));

    assertThat(count).isEqualTo(1);
    ArgumentCaptor<List<AdInsightBreakdown>> captor = ArgumentCaptor.forClass(List.class);
    org.mockito.Mockito.verify(breakdownRepository).saveAll(captor.capture());

    AdInsightBreakdown stored = captor.getValue().getFirst();
    assertThat(stored.getDimensionValue()).isEqualTo("25-34");
    assertThat(stored.getSpend()).isEqualByComparingTo("500");
    assertThat(stored.getPurchases()).isEqualByComparingTo("4");
    assertThat(stored.getLeads()).isEqualByComparingTo("2");
    assertThat(stored.getConversions()).isEqualByComparingTo("6");
    assertThat(stored.getConversionValue()).isEqualByComparingTo("1600");
  }

  @Test
  void expandsActionDeviceIntoActionSpecificRows() throws Exception {
    when(accountRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(account()));

    var actions =
        objectMapper.readTree(
            "[{\"action_type\":\"offsite_conversion.fb_pixel_purchase\","
                + "\"action_device\":\"mobile\",\"value\":\"4\"},"
                + "{\"action_type\":\"offsite_conversion.fb_pixel_lead\","
                + "\"action_device\":\"desktop\",\"value\":\"2\"}]");
    var actionValues =
        objectMapper.readTree(
            "[{\"action_type\":\"offsite_conversion.fb_pixel_purchase\","
                + "\"action_device\":\"mobile\",\"value\":\"1600\"}]");

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
            "2026-09-30",
            "500",
            "5000",
            "3500",
            "250",
            "220",
            "200",
            "5.0",
            "2.0",
            "100.0",
            "1.4",
            actions,
            actionValues,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    int count =
        service.persist(
            10L,
            20L,
            AdInsightBreakdownType.ACTION_DEVICE,
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 30),
            List.of(snapshot));

    assertThat(count).isEqualTo(2);
    ArgumentCaptor<List<AdInsightBreakdown>> captor = ArgumentCaptor.forClass(List.class);
    org.mockito.Mockito.verify(breakdownRepository).saveAll(captor.capture());
    assertThat(captor.getValue())
        .extracting(AdInsightBreakdown::getDimensionValue)
        .containsExactly("mobile", "desktop");
    assertThat(captor.getValue().getFirst().getActionValue()).isEqualByComparingTo("4");
    assertThat(captor.getValue().getFirst().getConversionValue()).isEqualByComparingTo("1600");
  }

  private AdAccount account() {
    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId("123456");
    account.setName("Acme Ads");
    account.setCurrency("INR");
    account.setAccessible(true);
    return account;
  }

  private InsightSummary insight(
      ObjectMapper mapper,
      String age,
      String gender,
      String country,
      String region,
      String publisherPlatform,
      String platformPosition,
      String impressionDevice,
      String devicePlatform,
      String conversionDestination)
      throws Exception {
    var actions =
        mapper.readTree(
            "[{\"action_type\":\"offsite_conversion.fb_pixel_purchase\",\"value\":\"4\"},"
                + "{\"action_type\":\"offsite_conversion.fb_pixel_lead\",\"value\":\"2\"}]");
    var actionValues =
        mapper.readTree(
            "[{\"action_type\":\"offsite_conversion.fb_pixel_purchase\",\"value\":\"1600\"}]");
    return new InsightSummary(
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
        "2026-09-30",
        "500",
        "5000",
        "3500",
        "250",
        "220",
        "200",
        "5.0",
        "2.0",
        "100.0",
        "1.4",
        actions,
        actionValues,
        actions,
        actionValues,
        null,
        null,
        age,
        gender,
        country,
        region,
        publisherPlatform,
        platformPosition,
        impressionDevice,
        devicePlatform,
        conversionDestination);
  }
}
