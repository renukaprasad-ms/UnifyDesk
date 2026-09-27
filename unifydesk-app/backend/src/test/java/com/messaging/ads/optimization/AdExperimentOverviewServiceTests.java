package com.messaging.ads.optimization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsExperimentService;
import com.messaging.ads.google.dto.GoogleExperimentResponse;
import com.messaging.ads.google.dto.GoogleExperimentResponse.Metrics;
import com.messaging.ads.meta.MetaAdsExperimentService;
import com.messaging.ads.meta.dto.MetaAdStudyResponse;
import com.messaging.ads.repository.AdAccountRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdExperimentOverviewServiceTests {

  @Mock private AdAccountRepository accountRepository;
  @Mock private GoogleAdsExperimentService googleExperimentService;
  @Mock private MetaAdsExperimentService metaExperimentService;

  private AdExperimentOverviewService service;

  @BeforeEach
  void setUp() {
    service =
        new AdExperimentOverviewService(
            accountRepository, googleExperimentService, metaExperimentService);
  }

  @Test
  void combinesGoogleManagedAndMetaReadOnlyExperiments() {
    AdAccount google = account(20L, AdProvider.GOOGLE_ADS);
    AdAccount meta = account(21L, AdProvider.META_ADS);
    when(accountRepository.findByCompanyIdOrderByProviderAscNameAsc(10L))
        .thenReturn(List.of(google, meta));

    when(googleExperimentService.list(10L, 20L))
        .thenReturn(
            List.of(
                new GoogleExperimentResponse(
                    "55",
                    "customers/1/experiments/55",
                    "Search test",
                    null,
                    "SEARCH_CUSTOM",
                    "ENABLED",
                    null,
                    "2026-09-01",
                    "2026-09-30",
                    null,
                    null,
                    List.of(),
                    new Metrics(
                        1000L, 500L, 100L, 40L, 10_000_000L, 4_000_000L,
                        new BigDecimal("12"), new BigDecimal("5"),
                        new BigDecimal("120"), new BigDecimal("40"),
                        null, null, null, null, null, null))));
    when(metaExperimentService.studies(10L, 21L))
        .thenReturn(
            List.of(
                new MetaAdStudyResponse(
                    "77", "Meta split test", null, "SPLIT_TEST",
                    1_780_000_000L, 1_781_000_000L, null, null, "95", null, null)));

    var result = service.overview(10L);

    assertThat(result.experiments()).hasSize(2);
    assertThat(result.experiments()).anyMatch(
        value -> value.provider() == AdProvider.GOOGLE_ADS
            && "API_MANAGED".equals(value.managementMode())
            && value.clicks() == 100L);
    assertThat(result.experiments()).anyMatch(
        value -> value.provider() == AdProvider.META_ADS
            && "READ_ONLY".equals(value.managementMode()));
    assertThat(result.failures()).isEmpty();
  }

  private AdAccount account(Long id, AdProvider provider) {
    AdAccount account = new AdAccount();
    account.setId(id);
    account.setCompanyId(10L);
    account.setProvider(provider);
    account.setManaged(true);
    account.setAccessible(true);
    return account;
  }
}
