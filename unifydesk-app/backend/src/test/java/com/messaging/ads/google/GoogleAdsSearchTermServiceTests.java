package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
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
class GoogleAdsSearchTermServiceTests {

  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdSetRepository adSetRepository;
  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsSearchTermService service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsSearchTermService(
            campaignRepository, adSetRepository, contextService, apiClient);
  }

  @Test
  void returnsSearchTermMetricsWithLocalAdGroup() throws Exception {
    AdCampaign campaign = campaign();
    AdAccount account = account();
    when(campaignRepository.findByIdAndCompanyId(10L, 1L))
        .thenReturn(Optional.of(campaign));
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "token", null, false, false));

    AdSet adGroup = new AdSet();
    adGroup.setId(30L);
    adGroup.setCompanyId(1L);
    adGroup.setAdAccountId(20L);
    adGroup.setCampaignId(10L);
    adGroup.setProvider(AdProvider.GOOGLE_ADS);
    adGroup.setExternalAdSetId("222");
    adGroup.setName("Main ad group");
    adGroup.setAccessible(true);
    when(adSetRepository.findByCompanyIdAndAdAccountIdAndProviderAndExternalAdSetId(
            1L, 20L, AdProvider.GOOGLE_ADS, "222"))
        .thenReturn(Optional.of(adGroup));

    when(apiClient.searchRows(eq("token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(
            List.of(
                objectMapper.readTree(
                    """
                    {
                      "searchTermView": {
                        "searchTerm": "free crm software",
                        "status": "NONE"
                      },
                      "adGroup": {
                        "id": "222",
                        "name": "Main ad group"
                      },
                      "segments": {
                        "keyword": {
                          "info": {
                            "text": "crm software",
                            "matchType": "BROAD"
                          }
                        },
                        "searchTermMatchType": "NEAR_PHRASE",
                        "searchTermMatchSource": "ADVERTISER_PROVIDED_KEYWORD"
                      },
                      "customer": {
                        "currencyCode": "INR"
                      },
                      "metrics": {
                        "impressions": "100",
                        "clicks": "10",
                        "ctr": "10",
                        "costMicros": "25000000",
                        "conversions": "2",
                        "conversionsValue": "100"
                      }
                    }
                    """)));

    var result =
        service.report(
            1L,
            10L,
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 7));

    assertThat(result.currency()).isEqualTo("INR");
    assertThat(result.rows()).singleElement().satisfies(
        row -> {
          assertThat(row.searchTerm()).isEqualTo("free crm software");
          assertThat(row.targetingStatus()).isEqualTo("NONE");
          assertThat(row.adGroupId()).isEqualTo("30");
          assertThat(row.adGroupName()).isEqualTo("Main ad group");
          assertThat(row.matchedKeyword()).isEqualTo("crm software");
          assertThat(row.matchedKeywordMatchType()).isEqualTo("BROAD");
          assertThat(row.searchTermMatchType()).isEqualTo("NEAR_PHRASE");
          assertThat(row.searchTermMatchSource()).isEqualTo("ADVERTISER_PROVIDED_KEYWORD");
          assertThat(row.spend()).isEqualByComparingTo("25");
          assertThat(row.conversions()).isEqualByComparingTo("2");
          assertThat(row.cpa()).isEqualByComparingTo("12.5");
          assertThat(row.roas()).isEqualByComparingTo("4");
        });

    ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
    verify(apiClient)
        .searchRows(eq("token"), eq("1234567890"), eq(null), query.capture());
    assertThat(query.getValue()).contains("FROM search_term_view");
    assertThat(query.getValue()).contains("segments.keyword.info.text");
    assertThat(query.getValue()).contains("segments.search_term_match_source");
  }

  @Test
  void rejectsNonSearchCampaign() {
    AdCampaign campaign = campaign();
    campaign.setObjective("PERFORMANCE_MAX");
    when(campaignRepository.findByIdAndCompanyId(10L, 1L))
        .thenReturn(Optional.of(campaign));

    assertThatThrownBy(
            () ->
                service.report(
                    1L,
                    10L,
                    LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 9, 7)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Search campaigns");
  }

  @Test
  void rejectsReportLongerThanNinetyDays() {
    assertThatThrownBy(
            () ->
                service.report(
                    1L,
                    10L,
                    LocalDate.of(2026, 5, 1),
                    LocalDate.of(2026, 9, 1)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("90 days");
  }

  private AdCampaign campaign() {
    AdCampaign campaign = new AdCampaign();
    campaign.setId(10L);
    campaign.setCompanyId(1L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setExternalCampaignId("111");
    campaign.setObjective("SEARCH");
    campaign.setAccessible(true);
    return campaign;
  }

  private AdAccount account() {
    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(1L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("1234567890");
    account.setCurrency("INR");
    account.setManaged(true);
    account.setAccessible(true);
    return account;
  }
}
