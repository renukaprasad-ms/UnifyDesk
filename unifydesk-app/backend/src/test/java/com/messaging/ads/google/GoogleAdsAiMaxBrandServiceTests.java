package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
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
class GoogleAdsAiMaxBrandServiceTests {

  @Mock private AdCampaignRepository campaignRepository;
  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsAiMaxBrandService service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsAiMaxBrandService(
            campaignRepository, contextService, apiClient, activityLogService);
  }

  @Test
  void mapsVerifiedBrandSuggestions() throws Exception {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "token", null, false, false));
    when(apiClient.suggestBrands(
            "token", "1234567890", null, "Open", List.of()))
        .thenReturn(
            objectMapper.readTree(
                """
                {
                  "brands": [
                    {
                      "id": "/m/0123",
                      "name": "OpenAI",
                      "urls": ["https://openai.com"],
                      "state": "ACTIVE"
                    }
                  ]
                }
                """));

    var result = service.suggest(1L, 20L, "Open", List.of());

    assertThat(result).singleElement().satisfies(
        brand -> {
          assertThat(brand.id()).isEqualTo("/m/0123");
          assertThat(brand.name()).isEqualTo("OpenAI");
          assertThat(brand.urls()).containsExactly("https://openai.com");
        });
  }

  @Test
  void rejectsBrandPresentInBothIncludeAndExcludeLists() {
    assertThatThrownBy(
            () -> service.validate(List.of("/m/0123"), List.of("/m/0123")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("both included and excluded");
  }

  @Test
  void createsBrandsSharedSetAndBrandCriterionWithVerifiedEntityId() throws Exception {
    AdCampaign campaign = new AdCampaign();
    campaign.setId(10L);
    campaign.setCompanyId(1L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setObjective("SEARCH");
    campaign.setExternalCampaignId("111");
    campaign.setAccessible(true);
    when(campaignRepository.findByIdAndCompanyId(10L, 1L))
        .thenReturn(Optional.of(campaign));

    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "token", null, false, false));
    when(apiClient.customerResourceName("1234567890", "campaigns", "111"))
        .thenReturn("customers/1234567890/campaigns/111");
    when(apiClient.searchRows(eq("token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(List.of());

    var mutateResponse =
        objectMapper.readTree(
            """
            {"results":[{"resourceName":"customers/1234567890/sharedSets/333"}]}
            """);
    when(apiClient.mutate(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("sharedSets"),
            org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(mutateResponse);
    when(apiClient.firstMutatedResourceName(mutateResponse))
        .thenReturn("customers/1234567890/sharedSets/333");

    service.apply(1L, 10L, List.of("/m/0123"), List.of(), 99L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, Object>>> sharedSetOps =
        ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("sharedSets"),
            sharedSetOps.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> sharedSetCreate =
        (Map<String, Object>) sharedSetOps.getValue().getFirst().get("create");
    assertThat(sharedSetCreate.get("type")).isEqualTo("BRANDS");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, Object>>> sharedCriterionOps =
        ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("sharedCriteria"),
            sharedCriterionOps.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> criterionCreate =
        (Map<String, Object>)
            sharedCriterionOps.getValue().getFirst().get("create");
    @SuppressWarnings("unchecked")
    Map<String, Object> brand =
        (Map<String, Object>) criterionCreate.get("brand");
    assertThat(brand.get("entityId")).isEqualTo("/m/0123");
  }

  private AdAccount account() {
    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setExternalAccountId("1234567890");
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setManaged(true);
    account.setAccessible(true);
    return account;
  }
}
