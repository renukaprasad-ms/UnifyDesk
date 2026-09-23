package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccountBilling;
import com.messaging.ads.meta.MetaAdsApiClient.AdAccountBillingSummary;
import com.messaging.ads.meta.MetaAdsApiClient.FundingSourceDetails;
import com.messaging.ads.repository.AdAccountBillingRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaAdsBillingPersistenceServiceTests {

  @Mock private AdAccountBillingRepository billingRepository;

  private MetaAdsBillingPersistenceService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsBillingPersistenceService(
            billingRepository, new ObjectMapper());
  }

  @Test
  void normalizesMetaBillingFieldsAndRemainingSpendCap() {
    when(billingRepository.findByAdAccountIdAndProvider(
            20L, com.messaging.ads.enums.AdProvider.META_ADS))
        .thenReturn(Optional.empty());
    when(billingRepository.save(any(AdAccountBilling.class)))
        .thenAnswer(
            invocation -> {
              AdAccountBilling billing = invocation.getArgument(0);
              billing.setId(99L);
              return billing;
            });

    AdAccountBillingSummary summary =
        new AdAccountBillingSummary(
            "act_123456",
            "123456",
            "Acme Ads",
            "INR",
            1,
            "5000",
            "12500",
            "20000",
            "funding-1",
            new FundingSourceDetails(
                "funding-1", 1, "Visa •••• 4242"),
            null,
            null,
            true,
            true,
            "1000",
            500L,
            1,
            "GST",
            null,
            "IN");

    var response = service.persist(10L, 20L, summary);

    assertThat(response.synced()).isTrue();
    assertThat(response.balanceMinor()).isEqualTo(12500L);
    assertThat(response.amountSpentMinor()).isEqualTo(5000L);
    assertThat(response.spendCapMinor()).isEqualTo(20000L);
    assertThat(response.remainingSpendCapMinor()).isEqualTo(15000L);
    assertThat(response.spendCapEnabled()).isTrue();
    assertThat(response.fundingSourceDisplay()).isEqualTo("Visa •••• 4242");
    assertThat(response.prepayAccount()).isTrue();

    ArgumentCaptor<AdAccountBilling> captor =
        ArgumentCaptor.forClass(AdAccountBilling.class);
    org.mockito.Mockito.verify(billingRepository).save(captor.capture());
    assertThat(captor.getValue().getMinDailyBudgetMinor()).isEqualTo(500L);
    assertThat(captor.getValue().getTaxIdType()).isEqualTo("GST");
    assertThat(captor.getValue().getBusinessCountryCode()).isEqualTo("IN");
    assertThat(captor.getValue().getProviderPayloadJson()).contains("amount_spent");
  }
}
