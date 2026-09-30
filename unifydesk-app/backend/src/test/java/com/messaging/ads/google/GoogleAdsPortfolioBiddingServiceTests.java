package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGooglePortfolioBiddingStrategyRequest;
import com.messaging.ads.google.dto.GoogleCampaignPortfolioAssignmentRequest;
import com.messaging.ads.google.dto.GooglePortfolioAlignmentRequest;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsPortfolioBiddingServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsPortfolioBiddingService service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsPortfolioBiddingService(
            contextService, apiClient, campaignRepository, activityLogService);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void createsTargetCpaPortfolioStrategyUsingAccountCurrency() throws Exception {
    var context = context(10L, 20L, "1234567890", "USD");
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("biddingStrategies"),
            anyList()))
        .thenReturn(objectMapper.readTree("{\"results\":[]}"));
    when(apiClient.firstMutatedResourceName(any()))
        .thenReturn("customers/1234567890/biddingStrategies/77");
    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(List.of());

    var result =
        service.createStrategy(
            10L,
            20L,
            new CreateGooglePortfolioBiddingStrategyRequest(
                "Lead portfolio", "TARGET_CPA", 2500L, null),
            99L);

    assertThat(result.id()).isEqualTo("77");
    assertThat(result.targetCpaMicros()).isEqualTo(25_000_000L);

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("biddingStrategies"),
            operations.capture());

    Map<String, Object> create =
        (Map<String, Object>) ((Map<String, Object>) operations.getValue().getFirst()).get("create");
    Map<String, Object> targetCpa = (Map<String, Object>) create.get("targetCpa");
    assertThat(create.get("name")).isEqualTo("Lead portfolio");
    assertThat(targetCpa.get("targetCpaMicros")).isEqualTo(25_000_000L);
  }

  @Test
  void rejectsPortfolioAssignmentForPerformanceMaxBeforeProviderMutation() {
    AdCampaign campaign = campaign(10L, 20L, 30L, "404", "PERFORMANCE_MAX");
    when(campaignRepository.findByIdAndCompanyId(30L, 10L)).thenReturn(Optional.of(campaign));

    assertThatThrownBy(
            () ->
                service.assign(
                    10L,
                    30L,
                    new GoogleCampaignPortfolioAssignmentRequest("77", "88"),
                    99L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Search, Display, and Shopping");

    verify(contextService, never()).requireManaged(10L, 20L);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void alignsStrategyAndBudgetOnlyWhenCampaignSetsMatch() throws Exception {
    var context = context(10L, 20L, "1234567890", "USD");
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerResourceName("1234567890", "biddingStrategies", "77"))
        .thenReturn("customers/1234567890/biddingStrategies/77");
    when(apiClient.customerResourceName("1234567890", "campaignBudgets", "88"))
        .thenReturn("customers/1234567890/campaignBudgets/88");

    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenAnswer(
            invocation -> {
              String query = invocation.getArgument(3);
              if (query.contains("FROM bidding_strategy")) {
                return List.of(
                    objectMapper.readTree(
                        """
                        {
                          "biddingStrategy": {
                            "id": "77",
                            "resourceName": "customers/1234567890/biddingStrategies/77",
                            "name": "Lead portfolio",
                            "type": "MAXIMIZE_CONVERSIONS",
                            "status": "ENABLED"
                          },
                          "metrics": {}
                        }
                        """));
              }
              if (query.contains("FROM campaign_budget")) {
                return List.of(
                    objectMapper.readTree(
                        """
                        {
                          "campaignBudget": {
                            "id": "88",
                            "resourceName": "customers/1234567890/campaignBudgets/88",
                            "name": "Shared leads",
                            "status": "ENABLED",
                            "deliveryMethod": "STANDARD",
                            "amountMicros": "50000000",
                            "referenceCount": "2"
                          },
                          "metrics": {}
                        }
                        """));
              }
              if (query.contains("campaign.bidding_strategy")) {
                return List.of(
                    objectMapper.readTree("{\"campaign\":{\"id\":\"100\"}}"),
                    objectMapper.readTree("{\"campaign\":{\"id\":\"101\"}}"));
              }
              if (query.contains("campaign.campaign_budget")) {
                return List.of(
                    objectMapper.readTree("{\"campaign\":{\"id\":\"101\"}}"),
                    objectMapper.readTree("{\"campaign\":{\"id\":\"100\"}}"));
              }
              return List.of();
            });

    service.align(
        10L, 20L, new GooglePortfolioAlignmentRequest("77", "88"), 99L);

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutateAll(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            operations.capture());
    assertThat(operations.getValue()).hasSize(2);
  }

  @Test
  void rejectsAlignmentWhenCampaignSetsDiffer() throws Exception {
    var context = context(10L, 20L, "1234567890", "USD");
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerResourceName("1234567890", "biddingStrategies", "77"))
        .thenReturn("customers/1234567890/biddingStrategies/77");
    when(apiClient.customerResourceName("1234567890", "campaignBudgets", "88"))
        .thenReturn("customers/1234567890/campaignBudgets/88");

    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenAnswer(
            invocation -> {
              String query = invocation.getArgument(3);
              if (query.contains("FROM bidding_strategy")) {
                return List.of(
                    objectMapper.readTree(
                        "{\"biddingStrategy\":{\"id\":\"77\",\"resourceName\":\"customers/1234567890/biddingStrategies/77\",\"name\":\"Lead portfolio\",\"type\":\"MAXIMIZE_CONVERSIONS\",\"status\":\"ENABLED\"},\"metrics\":{}}"));
              }
              if (query.contains("FROM campaign_budget")) {
                return List.of(
                    objectMapper.readTree(
                        "{\"campaignBudget\":{\"id\":\"88\",\"resourceName\":\"customers/1234567890/campaignBudgets/88\",\"name\":\"Shared leads\",\"status\":\"ENABLED\",\"deliveryMethod\":\"STANDARD\",\"amountMicros\":\"50000000\"},\"metrics\":{}}"));
              }
              if (query.contains("campaign.bidding_strategy")) {
                return List.of(objectMapper.readTree("{\"campaign\":{\"id\":\"100\"}}"));
              }
              if (query.contains("campaign.campaign_budget")) {
                return List.of(objectMapper.readTree("{\"campaign\":{\"id\":\"101\"}}"));
              }
              return List.of();
            });

    assertThatThrownBy(
            () ->
                service.align(
                    10L, 20L, new GooglePortfolioAlignmentRequest("77", "88"), 99L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("exact same campaign set");

    verify(apiClient, never()).mutateAll(anyString(), anyString(), any(), anyList());
  }

  private GoogleAdsAccountContextService.Context context(
      Long companyId, Long accountId, String externalAccountId, String currency) {
    AdAccount account = new AdAccount();
    account.setId(accountId);
    account.setCompanyId(companyId);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId(externalAccountId);
    account.setCurrency(currency);
    account.setManaged(true);
    account.setAccessible(true);
    return new GoogleAdsAccountContextService.Context(
        account,
        new AdProviderConnection(),
        "access-token",
        null,
        false,
        false);
  }

  private AdCampaign campaign(
      Long companyId, Long accountId, Long campaignId, String externalId, String objective) {
    AdCampaign campaign = new AdCampaign();
    campaign.setId(campaignId);
    campaign.setCompanyId(companyId);
    campaign.setAdAccountId(accountId);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setExternalCampaignId(externalId);
    campaign.setObjective(objective);
    campaign.setAccessible(true);
    return campaign;
  }
}
