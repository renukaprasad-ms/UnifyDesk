package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdCampaignRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsSimulationServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private AdCampaignRepository campaignRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsSimulationService service;

  @BeforeEach
  void setUp() {
    service = new GoogleAdsSimulationService(contextService, apiClient, campaignRepository);
  }

  @Test
  void mapsBudgetSimulationPointsWithoutTreatingThemAsForecasts() throws Exception {
    AdCampaign campaign = new AdCampaign();
    campaign.setId(30L);
    campaign.setCompanyId(10L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setExternalCampaignId("900");
    campaign.setAccessible(true);
    when(campaignRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(Optional.of(campaign));

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("1234567890");
    var context =
        new GoogleAdsAccountContextService.Context(
            account, new AdProviderConnection(), "token", null, false, false);
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);

    var row =
        objectMapper.readTree(
            """
            {
              "campaignSimulation":{
                "resourceName":"customers/1234567890/campaignSimulations/1",
                "campaignId":"900",
                "type":"BUDGET",
                "modificationMethod":"DEFAULT",
                "startDate":"2026-09-01",
                "endDate":"2026-09-07",
                "budgetPointList":{
                  "points":[
                    {
                      "budgetAmountMicros":"10000000",
                      "impressions":"1000",
                      "clicks":"100",
                      "costMicros":"9000000",
                      "biddableConversions":"12.5",
                      "biddableConversionsValue":"50"
                    }
                  ]
                }
              }
            }
            """);
    when(apiClient.searchRows(eq("token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(List.of(row));

    var result = service.campaignSimulations(10L, 30L);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo("BUDGET");
    assertThat(result.getFirst().points()).hasSize(1);
    assertThat(result.getFirst().points().getFirst().keyMicros()).isEqualTo(10_000_000L);
    assertThat(result.getFirst().points().getFirst().clicks()).isEqualTo(100L);
  }
}
