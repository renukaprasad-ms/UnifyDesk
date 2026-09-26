package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleAdGroupRequest;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.ads.service.AdActivityLogService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsAdGroupMutationServiceTests {

  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdSetRepository adSetRepository;
  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private GoogleAdsAdGroupSyncService syncService;
  @Mock private GoogleAdsAdGroupPersistenceService persistenceService;
  @Mock private AdActivityLogService activityLogService;

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void createsDemandGenAdGroupAudienceReadyWithAllChannels() throws Exception {
    GoogleAdsAdGroupMutationService service =
        new GoogleAdsAdGroupMutationService(
            campaignRepository,
            adSetRepository,
            contextService,
            apiClient,
            syncService,
            persistenceService,
            activityLogService);

    AdCampaign campaign = new AdCampaign();
    campaign.setId(30L);
    campaign.setCompanyId(10L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setObjective("DEMAND_GEN");
    campaign.setExternalCampaignId("777");
    campaign.setAccessible(true);
    when(campaignRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(Optional.of(campaign));

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("1234567890");
    account.setManaged(true);
    account.setAccessible(true);
    var context =
        new GoogleAdsAccountContextService.Context(
            account, new AdProviderConnection(), "access-token", null, false, false);
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerResourceName("1234567890", "campaigns", "777"))
        .thenReturn("customers/1234567890/campaigns/777");

    var response =
        new ObjectMapper()
            .readTree(
                "{"results":[{"resourceName":"customers/1234567890/adGroups/888"}]}");
    when(apiClient.mutate(
            eq("access-token"), eq("1234567890"), eq(null), eq("adGroups"), any()))
        .thenReturn(response);
    when(apiClient.firstMutatedResourceName(response))
        .thenReturn("customers/1234567890/adGroups/888");

    var row =
        new ObjectMapper()
            .readTree(
                "{"adGroup":{"id":"888","name":"Demand Gen group","status":"PAUSED"}}");
    when(syncService.adGroup(context, "777", "888")).thenReturn(row);

    AdSet persisted = new AdSet();
    persisted.setId(40L);
    persisted.setCompanyId(10L);
    persisted.setAdAccountId(20L);
    persisted.setCampaignId(30L);
    persisted.setProvider(AdProvider.GOOGLE_ADS);
    persisted.setExternalAdSetId("888");
    persisted.setName("Demand Gen group");
    persisted.setAccessible(true);
    when(persistenceService.persistOne(10L, 30L, row)).thenReturn(persisted);

    service.create(
        10L, 30L, new CreateGoogleAdGroupRequest("Demand Gen group", null), 99L);

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("adGroups"),
            operations.capture());

    Map<String, Object> create =
        (Map<String, Object>) ((Map<String, Object>) operations.getValue().getFirst()).get("create");
    assertThat(create.get("audienceSetting"))
        .isEqualTo(Map.of("useAudienceGrouped", true));
    assertThat(create.get("demandGenAdGroupSettings"))
        .isEqualTo(
            Map.of(
                "channelControls",
                Map.of("channelStrategy", "ALL_CHANNELS")));
  }
}
