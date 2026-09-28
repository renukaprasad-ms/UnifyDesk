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
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleDisplayCampaignRequest;
import com.messaging.ads.service.AdActivityLogService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsDisplayCampaignServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private GoogleAdsCampaignSyncService campaignSyncService;
  @Mock private GoogleAdsCampaignPersistenceService campaignPersistenceService;
  @Mock private GoogleAdsAdGroupSyncService adGroupSyncService;
  @Mock private GoogleAdsAdGroupPersistenceService adGroupPersistenceService;
  @Mock private com.messaging.ads.repository.AdCampaignRepository campaignRepository;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsDisplayCampaignService service;
  private GoogleAdsAccountContextService.Context context;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsDisplayCampaignService(
            contextService,
            apiClient,
            campaignSyncService,
            campaignPersistenceService,
            adGroupSyncService,
            adGroupPersistenceService,
            campaignRepository,
            activityLogService);

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
            account,
            new AdProviderConnection(),
            "access-token",
            null,
            false,
            false);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void createsPausedDisplayCampaignAndStandardAdGroupAtomically() throws Exception {
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerResourceName("1234567890", "campaignBudgets", "-1"))
        .thenReturn("customers/1234567890/campaignBudgets/-1");
    when(apiClient.customerResourceName("1234567890", "campaigns", "-2"))
        .thenReturn("customers/1234567890/campaigns/-2");
    when(apiClient.customerResourceName("1234567890", "adGroups", "-3"))
        .thenReturn("customers/1234567890/adGroups/-3");
    when(apiClient.mutateAll(
            eq("access-token"), eq("1234567890"), eq(null), any()))
        .thenReturn(objectMapper.readTree("{\"mutateOperationResponses\":[]}"));
    when(apiClient.firstBulkResourceName(any(), eq("campaignResult")))
        .thenReturn("customers/1234567890/campaigns/901");
    when(apiClient.firstBulkResourceName(any(), eq("adGroupResult")))
        .thenReturn("customers/1234567890/adGroups/902");

    var campaignRow =
        objectMapper.readTree(
            """
            {"campaign":{"id":"901","name":"Display awareness","status":"PAUSED","advertisingChannelType":"DISPLAY"}}
            """);
    when(campaignSyncService.campaign(context, "901")).thenReturn(campaignRow);

    AdCampaign persistedCampaign = new AdCampaign();
    persistedCampaign.setId(700L);
    persistedCampaign.setCompanyId(10L);
    persistedCampaign.setAdAccountId(20L);
    persistedCampaign.setProvider(AdProvider.GOOGLE_ADS);
    persistedCampaign.setExternalCampaignId("901");
    persistedCampaign.setName("Display awareness");
    persistedCampaign.setObjective("DISPLAY");
    persistedCampaign.setStatus(AdEntityStatus.PAUSED);
    persistedCampaign.setAccessible(true);
    when(campaignPersistenceService.persistOne(10L, 20L, campaignRow))
        .thenReturn(persistedCampaign);
    when(campaignRepository.findByIdAndCompanyId(700L, 10L))
        .thenReturn(java.util.Optional.of(persistedCampaign));

    var adGroupRow =
        objectMapper.readTree(
            """
            {"campaign":{"id":"901"},"adGroup":{"id":"902","name":"Display audience","status":"PAUSED","type":"DISPLAY_STANDARD"}}
            """);
    when(adGroupSyncService.adGroup(context, "901", "902"))
        .thenReturn(adGroupRow);

    AdSet persistedAdGroup = new AdSet();
    persistedAdGroup.setId(701L);
    persistedAdGroup.setCompanyId(10L);
    persistedAdGroup.setAdAccountId(20L);
    persistedAdGroup.setCampaignId(700L);
    persistedAdGroup.setProvider(AdProvider.GOOGLE_ADS);
    persistedAdGroup.setExternalAdSetId("902");
    persistedAdGroup.setName("Display audience");
    persistedAdGroup.setStatus(AdEntityStatus.PAUSED);
    persistedAdGroup.setAccessible(true);
    when(adGroupPersistenceService.persistOne(10L, 700L, adGroupRow))
        .thenReturn(persistedAdGroup);

    service.create(
        10L,
        20L,
        new CreateGoogleDisplayCampaignRequest(
            "Display awareness",
            10_000L,
            "Display audience",
            "MANUAL_CPC",
            null,
            500L,
            false,
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31)),
        99L);

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutateAll(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            operations.capture());

    assertThat(operations.getValue()).hasSize(3);

    Map<String, Object> campaignOperation =
        (Map<String, Object>) operations.getValue().get(1);
    Map<String, Object> campaignCreate =
        (Map<String, Object>)
            ((Map<String, Object>) campaignOperation.get("campaignOperation"))
                .get("create");
    assertThat(campaignCreate.get("status")).isEqualTo("PAUSED");
    assertThat(campaignCreate.get("advertisingChannelType"))
        .isEqualTo("DISPLAY");

    Map<String, Object> adGroupOperation =
        (Map<String, Object>) operations.getValue().get(2);
    Map<String, Object> adGroupCreate =
        (Map<String, Object>)
            ((Map<String, Object>) adGroupOperation.get("adGroupOperation"))
                .get("create");
    assertThat(adGroupCreate.get("status")).isEqualTo("PAUSED");
    assertThat(adGroupCreate).containsKey("cpcBidMicros");
  }
}
