package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GooglePerformanceMaxListingGroupReplaceRequest;
import com.messaging.ads.google.dto.GooglePerformanceMaxListingGroupValueRequest;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
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
class GoogleAdsPerformanceMaxListingGroupServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsPerformanceMaxListingGroupService service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsPerformanceMaxListingGroupService(
            contextService, apiClient, campaignRepository, activityLogService);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void replacesExistingTreeChildFirstAndCreatesTemporaryBrandTree() throws Exception {
    AdCampaign campaign = campaign();
    when(campaignRepository.findByIdAndCompanyId(700L, 10L))
        .thenReturn(Optional.of(campaign));
    when(contextService.requireManaged(10L, 20L)).thenReturn(context());

    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenAnswer(
            invocation -> {
              String query = invocation.getArgument(3);
              if (query.contains("campaign.shopping_setting")) {
                return List.of(
                    objectMapper.readTree(
                        """
                        {"campaign":{"shoppingSetting":{"merchantId":"12345","feedLabel":"IN"}}}
                        """));
              }
              if (query.contains("SELECT asset_group.id")) {
                return List.of(objectMapper.readTree("{\"assetGroup\":{\"id\":\"501\"}}"));
              }
              if (query.contains("FROM asset_group_product_group_view")) {
                return List.of();
              }
              if (query.contains("FROM asset_group_listing_group_filter")) {
                return List.of(
                    objectMapper.readTree(
                        """
                        {"assetGroupListingGroupFilter":{
                          "id":"1",
                          "resourceName":"customers/1234567890/assetGroupListingGroupFilters/501~1",
                          "type":"SUBDIVISION",
                          "listingSource":"SHOPPING"
                        }}
                        """),
                    objectMapper.readTree(
                        """
                        {"assetGroupListingGroupFilter":{
                          "id":"2",
                          "resourceName":"customers/1234567890/assetGroupListingGroupFilters/501~2",
                          "parentListingGroupFilter":"customers/1234567890/assetGroupListingGroupFilters/501~1",
                          "type":"UNIT_INCLUDED",
                          "listingSource":"SHOPPING",
                          "caseValue":{"productBrand":{"value":"OldBrand"}}
                        }}
                        """));
              }
              return List.of();
            });

    service.replace(
        10L,
        700L,
        "501",
        new GooglePerformanceMaxListingGroupReplaceRequest(
            "BRAND",
            List.of(
                new GooglePerformanceMaxListingGroupValueRequest(
                    "Acme", null, null, true),
                new GooglePerformanceMaxListingGroupValueRequest(
                    "OtherBrand", null, null, false)),
            true),
        99L);

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutateAll(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            operations.capture());

    List<Map<String, Object>> values =
        (List<Map<String, Object>>) operations.getValue();

    Map<String, Object> firstRemove =
        (Map<String, Object>) values.get(0).get("assetGroupListingGroupFilterOperation");
    Map<String, Object> secondRemove =
        (Map<String, Object>) values.get(1).get("assetGroupListingGroupFilterOperation");
    assertThat(firstRemove.get("remove"))
        .isEqualTo("customers/1234567890/assetGroupListingGroupFilters/501~2");
    assertThat(secondRemove.get("remove"))
        .isEqualTo("customers/1234567890/assetGroupListingGroupFilters/501~1");

    Map<String, Object> rootOperation =
        (Map<String, Object>) values.get(2).get("assetGroupListingGroupFilterOperation");
    Map<String, Object> root = (Map<String, Object>) rootOperation.get("create");
    assertThat(root)
        .containsEntry("resourceName", "customers/1234567890/assetGroupListingGroupFilters/501~-1")
        .containsEntry("type", "SUBDIVISION")
        .containsEntry("listingSource", "SHOPPING");

    Map<String, Object> includedOperation =
        (Map<String, Object>) values.get(3).get("assetGroupListingGroupFilterOperation");
    Map<String, Object> included = (Map<String, Object>) includedOperation.get("create");
    assertThat(included.get("parentListingGroupFilter"))
        .isEqualTo("customers/1234567890/assetGroupListingGroupFilters/501~-1");
    assertThat(included.get("type")).isEqualTo("UNIT_INCLUDED");
    assertThat((Map<String, Object>) included.get("caseValue"))
        .containsKey("productBrand");

    Map<String, Object> otherOperation =
        (Map<String, Object>) values.get(5).get("assetGroupListingGroupFilterOperation");
    Map<String, Object> other = (Map<String, Object>) otherOperation.get("create");
    assertThat(other.get("type")).isEqualTo("UNIT_INCLUDED");
    assertThat((Map<String, Object>) other.get("caseValue")).isEmpty();
  }

  private AdCampaign campaign() {
    AdCampaign campaign = new AdCampaign();
    campaign.setId(700L);
    campaign.setCompanyId(10L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setObjective("PERFORMANCE_MAX");
    campaign.setExternalCampaignId("777");
    campaign.setAccessible(true);
    return campaign;
  }

  private GoogleAdsAccountContextService.Context context() {
    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("1234567890");
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
}
