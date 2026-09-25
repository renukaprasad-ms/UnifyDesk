package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class GoogleAdsAiMaxControlServiceTests {

  @Mock private AdCampaignRepository campaignRepository;
  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private AdActivityLogService activityLogService;

  private GoogleAdsAiMaxControlService service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsAiMaxControlService(
            campaignRepository, contextService, apiClient, activityLogService);
  }

  @Test
  void validatesInclusionAndExclusionUrlShapes() {
    assertThat(service.validateUrlInclusions(List.of("https://example.com/product")).requestedCount())
        .isEqualTo(1);
    assertThat(service.validateUrlExclusions(List.of("/privacy", "https://example.com/old")).requestedCount())
        .isEqualTo(2);

    assertThatThrownBy(() -> service.validateUrlInclusions(List.of("/product")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("absolute HTTP(S) URLs");
  }

  @Test
  void createsNamedNegativeWebpageCriterionForUrlExclusion() {
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

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setExternalAccountId("1234567890");
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setManaged(true);
    account.setAccessible(true);
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "token", null, false, false));
    when(apiClient.searchRows(eq("token"), eq("1234567890"), eq(null), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(List.of());

    service.applyUrlExclusions(
        1L, 10L, List.of("https://example.com/old"), 99L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, Object>>> operations =
        ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("campaignCriteria"),
            operations.capture());

    @SuppressWarnings("unchecked")
    Map<String, Object> create =
        (Map<String, Object>) operations.getValue().getFirst().get("create");
    assertThat(create.get("negative")).isEqualTo(true);
    @SuppressWarnings("unchecked")
    Map<String, Object> webpage = (Map<String, Object>) create.get("webpage");
    assertThat(webpage.get("criterionName").toString())
        .startsWith("UnifyDesk AI Max exclusion ");
  }
}
