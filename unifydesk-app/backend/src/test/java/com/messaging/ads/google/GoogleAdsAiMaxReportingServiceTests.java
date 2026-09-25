package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdCampaignRepository;
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
class GoogleAdsAiMaxReportingServiceTests {

  @Mock private AdCampaignRepository campaignRepository;
  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsAiMaxReportingService service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsAiMaxReportingService(
            campaignRepository, contextService, apiClient);
  }

  @Test
  void mapsAiMaxSearchTermHeadlineAndLandingPageMetrics() throws Exception {
    AdCampaign campaign = new AdCampaign();
    campaign.setId(10L);
    campaign.setCompanyId(1L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setExternalCampaignId("111");
    campaign.setObjective("SEARCH");
    campaign.setAccessible(true);
    when(campaignRepository.findByIdAndCompanyId(10L, 1L))
        .thenReturn(Optional.of(campaign));

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(1L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("1234567890");
    account.setCurrency("INR");
    account.setManaged(true);
    account.setAccessible(true);
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "token", null, false, false));

    when(apiClient.searchRows(eq("token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(
            List.of(
                objectMapper.readTree(
                    """
                    {
                      "aiMaxSearchTermAdCombinationView": {
                        "searchTerm": "best customer support crm",
                        "headline": "Unify your customer conversations",
                        "landingPage": "https://example.com/crm"
                      },
                      "adGroup": {
                        "id": "222",
                        "name": "Main"
                      },
                      "customer": {
                        "currencyCode": "INR"
                      },
                      "metrics": {
                        "impressions": "100",
                        "clicks": "10",
                        "costMicros": "50000000",
                        "conversions": "5",
                        "conversionsValue": "250"
                      }
                    }
                    """)));

    var result =
        service.report(
            1L,
            10L,
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 7));

    assertThat(result.rows()).singleElement().satisfies(
        row -> {
          assertThat(row.searchTerm()).isEqualTo("best customer support crm");
          assertThat(row.headline()).isEqualTo("Unify your customer conversations");
          assertThat(row.landingPage()).isEqualTo("https://example.com/crm");
          assertThat(row.spend()).isEqualByComparingTo("50");
          assertThat(row.cpa()).isEqualByComparingTo("10");
          assertThat(row.roas()).isEqualByComparingTo("5");
        });

    ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
    verify(apiClient)
        .searchRows(eq("token"), eq("1234567890"), eq(null), query.capture());
    assertThat(query.getValue()).contains("FROM ai_max_search_term_ad_combination_view");
    assertThat(query.getValue()).contains("ai_max_search_term_ad_combination_view.headline");
    assertThat(query.getValue()).contains("ai_max_search_term_ad_combination_view.landing_page");
  }
}
