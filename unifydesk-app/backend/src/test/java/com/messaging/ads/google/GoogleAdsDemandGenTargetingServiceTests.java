package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleDemandGenSelectedChannels;
import com.messaging.ads.google.dto.GoogleDemandGenTargetingRequest;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
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
class GoogleAdsDemandGenTargetingServiceTests {

  @Mock private AdSetRepository adSetRepository;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsDemandGenTargetingService service;
  private GoogleAdsAccountContextService.Context googleContext;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsDemandGenTargetingService(
            adSetRepository,
            campaignRepository,
            contextService,
            apiClient,
            activityLogService);

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setExternalAccountId("1234567890");
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setManaged(true);
    account.setAccessible(true);

    googleContext =
        new GoogleAdsAccountContextService.Context(
            account, new AdProviderConnection(), "access-token", "9999999999", false, false);

    AdCampaign campaign = new AdCampaign();
    campaign.setId(30L);
    campaign.setCompanyId(10L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setObjective("DEMAND_GEN");
    campaign.setExternalCampaignId("777");
    campaign.setAccessible(true);

    AdSet adGroup = new AdSet();
    adGroup.setId(40L);
    adGroup.setCompanyId(10L);
    adGroup.setAdAccountId(20L);
    adGroup.setCampaignId(30L);
    adGroup.setProvider(AdProvider.GOOGLE_ADS);
    adGroup.setExternalAdSetId("888");
    adGroup.setAccessible(true);

    when(adSetRepository.findByIdAndCompanyId(40L, 10L)).thenReturn(Optional.of(adGroup));
    when(campaignRepository.findByIdAndCompanyId(30L, 10L)).thenReturn(Optional.of(campaign));
    when(contextService.requireManaged(10L, 20L)).thenReturn(googleContext);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void updatesIndividualChannelControlsAndPreservesCriteriaResources() throws Exception {
    var groupRow =
        objectMapper.readTree(
            """
            {
              "adGroup": {
                "id":"888",
                "audienceSetting":{"useAudienceGrouped":true},
                "demandGenAdGroupSettings":{
                  "channelControls":{"channelConfig":"CHANNEL_STRATEGY","channelStrategy":"ALL_CHANNELS"}
                }
              }
            }
            """);
    var afterCriteria =
        objectMapper.readTree(
            """
            {
              "adGroupCriterion":{
                "type":"LOCATION",
                "negative":false,
                "location":{"geoTargetConstant":"geoTargetConstants/2356"}
              }
            }
            """);
    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq("9999999999"), anyString()))
        .thenAnswer(
            invocation -> {
              String query = invocation.getArgument(3);
              if (query.contains("FROM ad_group WHERE")) return List.of(groupRow);
              if (query.contains("FROM ad_group_criterion")) {
                return query.contains("resource_name") ? List.of() : List.of(afterCriteria);
              }
              return List.of();
            });
    when(apiClient.customerResourceName("1234567890", "adGroups", "888"))
        .thenReturn("customers/1234567890/adGroups/888");

    service.update(
        10L,
        40L,
        new GoogleDemandGenTargetingRequest(
            null,
            new GoogleDemandGenSelectedChannels(false, false, true, false, false, false, false),
            List.of(),
            List.of("geoTargetConstants/2356"),
            List.of(),
            List.of(),
            List.of(),
            List.of("languageConstants/1000"),
            List.of()),
        99L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, Object>>> operations =
        ArgumentCaptor.forClass(List.class);

    verify(apiClient, atLeastOnce())
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq("9999999999"),
            eq("adGroups"),
            operations.capture());

    Map<String, Object> adGroupUpdate = operations.getAllValues().getFirst().getFirst();
    assertThat(adGroupUpdate.get("updateMask"))
        .isEqualTo("demand_gen_ad_group_settings.channel_controls");

    verify(apiClient)
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq("9999999999"),
            eq("adGroupCriteria"),
            any());
  }

  @Test
  void rejectsIndividualChannelSelectionWhenAllChannelsAreDisabled() throws Exception {
    var groupRow =
        objectMapper.readTree(
            """
            {
              "adGroup": {
                "id":"888",
                "audienceSetting":{"useAudienceGrouped":true},
                "demandGenAdGroupSettings":{
                  "channelControls":{"channelConfig":"CHANNEL_STRATEGY","channelStrategy":"ALL_CHANNELS"}
                }
              }
            }
            """);
    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq("9999999999"), anyString()))
        .thenAnswer(
            invocation ->
                invocation.getArgument(3, String.class).contains("FROM ad_group WHERE")
                    ? List.of(groupRow)
                    : List.of());

    assertThatThrownBy(
            () ->
                service.update(
                    10L,
                    40L,
                    new GoogleDemandGenTargetingRequest(
                        null,
                        new GoogleDemandGenSelectedChannels(
                            false, false, false, false, false, false, false),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                    99L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("at least one enabled channel");
  }

  @Test
  void rejectsAudienceLinkingForLegacyUngroupedAdGroup() throws Exception {
    var groupRow =
        objectMapper.readTree(
            """
            {
              "adGroup": {
                "id":"888",
                "audienceSetting":{"useAudienceGrouped":false},
                "demandGenAdGroupSettings":{
                  "channelControls":{"channelConfig":"CHANNEL_STRATEGY","channelStrategy":"ALL_CHANNELS"}
                }
              }
            }
            """);
    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq("9999999999"), anyString()))
        .thenAnswer(
            invocation ->
                invocation.getArgument(3, String.class).contains("FROM ad_group WHERE")
                    ? List.of(groupRow)
                    : List.of());

    assertThatThrownBy(
            () ->
                service.update(
                    10L,
                    40L,
                    new GoogleDemandGenTargetingRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of("customers/1234567890/audiences/55")),
                    99L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("immutable");

    verify(apiClient, never())
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq("9999999999"),
            eq("adGroupCriteria"),
            any());
  }
}
