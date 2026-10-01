package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdProvider;
import com.messaging.common.exception.BadRequestException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsProviderReportingServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsProviderReportingService service;
  private GoogleAdsAccountContextService.Context context;

  @BeforeEach
  void setUp() {
    service = new GoogleAdsProviderReportingService(contextService, apiClient);

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("1234567890");
    account.setCurrency("INR");
    account.setManaged(true);
    account.setAccessible(true);

    context =
        new GoogleAdsAccountContextService.Context(
            account, new AdProviderConnection(), "access-token", null, false, false);
  }

  @Test
  void rejectsRangesLongerThanNinetyDays() {
    assertThatThrownBy(
            () ->
                service.report(
                    10L,
                    20L,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 4, 15),
                    null))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("90 days");
  }

  @Test
  void mapsProviderNativeRowsAndAppliesCampaignFilter() throws Exception {
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerResourceName("1234567890", "campaigns", "777"))
        .thenReturn("customers/1234567890/campaigns/777");

    var landing =
        objectMapper.readTree(
            """
            {
              "campaign":{"id":"777","name":"Search"},
              "adGroup":{"id":"888","name":"Core"},
              "landingPageView":{"unexpandedFinalUrl":"https://example.com"},
              "metrics":{"impressions":"100","clicks":"12","costMicros":"5000000","conversions":"3","conversionsValue":"9"}
            }
            """);
    var placement =
        objectMapper.readTree(
            """
            {
              "campaign":{"id":"777","name":"Display"},
              "adGroup":{"id":"889","name":"Placements"},
              "groupPlacementView":{"placement":"youtube.com/channel/x","displayName":"Channel X","placementType":"YOUTUBE_CHANNEL","targetUrl":"https://youtube.com/channel/x"},
              "segments":{"adNetworkType":"YOUTUBE"},
              "metrics":{"impressions":"200","clicks":"20","costMicros":"6000000","conversions":"4","conversionsValue":"12"}
            }
            """);
    var auction =
        objectMapper.readTree(
            """
            {
              "campaign":{"id":"777","name":"Search"},
              "segments":{"auctionInsightDomain":"competitor.example"},
              "metrics":{"auctionInsightSearchImpressionShare":"0.42","auctionInsightSearchOverlapRate":"0.31"}
            }
            """);
    var conversion =
        objectMapper.readTree(
            """
            {
              "campaign":{"id":"777","name":"Search"},
              "segments":{"conversionActionName":"Purchase","conversionActionCategory":"PURCHASE","externalConversionSource":"WEBPAGE"},
              "metrics":{"conversions":"5","conversionsValue":"100","allConversions":"6","allConversionsValue":"110"}
            }
            """);
    var change =
        objectMapper.readTree(
            """
            {
              "changeEvent":{
                "changeDateTime":"2026-09-25 12:00:00",
                "userEmail":"user@example.com",
                "clientType":"GOOGLE_ADS_API",
                "changeResourceType":"CAMPAIGN",
                "resourceChangeOperation":"UPDATE",
                "changeResourceName":"customers/1234567890/campaigns/777",
                "changedFields":{"paths":["status","name"]},
                "campaign":"customers/1234567890/campaigns/777"
              }
            }
            """);

    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenAnswer(
            invocation -> {
              String query = invocation.getArgument(3);
              if (query.contains("FROM change_event")) {
                assertThat(query)
                    .contains("change_event.campaign = 'customers/1234567890/campaigns/777'");
                return List.of(change);
              }
              assertThat(query).contains("campaign.id = 777");
              if (query.contains("FROM landing_page_view")) return List.of(landing);
              if (query.contains("FROM group_placement_view")) return List.of(placement);
              if (query.contains("auction_insight_domain")) return List.of(auction);
              if (query.contains("conversion_action_name")) return List.of(conversion);
              return List.of();
            });

    var report =
        service.report(
            10L,
            20L,
            LocalDate.of(2026, 9, 20),
            LocalDate.of(2026, 9, 26),
            "777");

    assertThat(report.landingPages()).hasSize(1);
    assertThat(report.placements()).hasSize(1);
    assertThat(report.auctionInsights()).hasSize(1);
    assertThat(report.conversionAttribution()).hasSize(1);
    assertThat(report.changeEvents()).hasSize(1);
    assertThat(report.placements().getFirst().placementType()).isEqualTo("YOUTUBE_CHANNEL");
    assertThat(report.auctionInsights().getFirst().domain()).isEqualTo("competitor.example");
    assertThat(report.changeEvents().getFirst().changedFields()).containsExactly("status", "name");
  }
}
