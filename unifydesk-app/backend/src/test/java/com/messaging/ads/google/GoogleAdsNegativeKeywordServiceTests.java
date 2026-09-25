package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleNegativeKeywordBatchRequest;
import com.messaging.ads.google.dto.GoogleNegativeKeywordInput;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsNegativeKeywordServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsNegativeKeywordService service;
  private AdAccount account;
  private AdCampaign campaign;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsNegativeKeywordService(
            contextService, apiClient, campaignRepository, activityLogService);

    account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(1L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("1234567890");

    campaign = new AdCampaign();
    campaign.setId(30L);
    campaign.setCompanyId(1L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setObjective("SEARCH");
    campaign.setExternalCampaignId("900");
    campaign.setAccessible(true);
  }

  @Test
  void validatesConfiguredCampaignNegativesAndSharedLists() throws Exception {
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "access-token", null, false, false));

    var sharedSetRow =
        objectMapper.readTree(
            """
            {
              "sharedSet": {
                "resourceName": "customers/1234567890/sharedSets/77",
                "id": "77",
                "name": "Brand safety",
                "type": "NEGATIVE_KEYWORDS",
                "status": "ENABLED",
                "memberCount": "3",
                "referenceCount": "1"
              }
            }
            """);
    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(List.of(sharedSetRow));

    var result =
        service.validateLaunchConfiguration(
            1L,
            20L,
            objectMapper.readTree(
                """
                {
                  "negativeKeywords": [
                    {"text":"free","matchType":"PHRASE"},
                    {"text":"jobs","matchType":"EXACT"}
                  ],
                  "negativeKeywordSharedSetIds": ["77"]
                }
                """));

    assertThat(result.campaignNegativeKeywordCount()).isEqualTo(2);
    assertThat(result.sharedListCount()).isEqualTo(1);
  }

  @Test
  void rejectsOversizedNegativeKeywordBeforeAnyProviderRead() throws Exception {
    String text = "x".repeat(81);

    assertThatThrownBy(
            () ->
                service.validateLaunchConfiguration(
                    1L,
                    20L,
                    objectMapper.readTree(
                        """
                        {"negativeKeywords":[{"text":"%s","matchType":"PHRASE"}]}
                        """
                            .formatted(text))))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("80 characters");

    verify(contextService, never()).requireManaged(1L, 20L);
  }

  @Test
  void addsOnlyMissingCampaignNegativeKeywords() throws Exception {
    when(campaignRepository.findByIdAndCompanyId(30L, 1L))
        .thenReturn(Optional.of(campaign));
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "access-token", null, false, false));

    var existing =
        objectMapper.readTree(
            """
            {
              "campaignCriterion": {
                "resourceName": "customers/1234567890/campaignCriteria/900~1",
                "criterionId": "1",
                "status": "ENABLED",
                "negative": true,
                "keyword": {
                  "text": "free",
                  "matchType": "PHRASE"
                }
              }
            }
            """);
    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(List.of(existing));
    when(apiClient.customerResourceName("1234567890", "campaigns", "900"))
        .thenReturn("customers/1234567890/campaigns/900");

    var result =
        service.addCampaignNegatives(
            1L,
            30L,
            new GoogleNegativeKeywordBatchRequest(
                List.of(
                    new GoogleNegativeKeywordInput("free", "PHRASE"),
                    new GoogleNegativeKeywordInput("jobs", "EXACT"))),
            9L);

    assertThat(result.requestedCount()).isEqualTo(2);
    assertThat(result.createdCount()).isEqualTo(1);
    assertThat(result.reusedCount()).isEqualTo(1);
    verify(apiClient)
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("campaignCriteria"),
            anyList());
  }
}
