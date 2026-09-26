package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleAppCampaignRequest;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsAppCampaignServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private GoogleAdsCampaignSyncService campaignSyncService;
  @Mock private GoogleAdsCampaignPersistenceService campaignPersistenceService;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsAppCampaignService service;
  private GoogleAdsAccountContextService.Context context;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsAppCampaignService(
            contextService,
            apiClient,
            campaignSyncService,
            campaignPersistenceService,
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
            account, new AdProviderConnection(), "access-token", null, false, false);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void createsPausedInstallCampaignWithTargetCpa() throws Exception {
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerResourceName("1234567890", "campaignBudgets", "-1"))
        .thenReturn("customers/1234567890/campaignBudgets/-1");
    when(apiClient.customerResourceName("1234567890", "campaigns", "-2"))
        .thenReturn("customers/1234567890/campaigns/-2");
    when(apiClient.mutateAll(eq("access-token"), eq("1234567890"), eq(null), any()))
        .thenReturn(objectMapper.readTree("{\"mutateOperationResponses\":[]}"));
    when(apiClient.firstBulkResourceName(any(), eq("campaignResult")))
        .thenReturn("customers/1234567890/campaigns/777");

    var row =
        objectMapper.readTree(
            """
            {
              "campaign": {
                "id":"777",
                "name":"App installs",
                "status":"PAUSED",
                "advertisingChannelType":"MULTI_CHANNEL",
                "advertisingChannelSubType":"APP_CAMPAIGN"
              }
            }
            """);
    when(campaignSyncService.campaign(context, "777")).thenReturn(row);

    AdCampaign persisted = new AdCampaign();
    persisted.setId(700L);
    persisted.setCompanyId(10L);
    persisted.setAdAccountId(20L);
    persisted.setProvider(AdProvider.GOOGLE_ADS);
    persisted.setExternalCampaignId("777");
    persisted.setName("App installs");
    persisted.setObjective("APP");
    persisted.setBuyingType("APP_CAMPAIGN");
    persisted.setStatus(AdEntityStatus.PAUSED);
    persisted.setAccessible(true);
    when(campaignPersistenceService.persistOne(10L, 20L, row)).thenReturn(persisted);

    service.create(
        10L,
        20L,
        new CreateGoogleAppCampaignRequest(
            "App installs",
            "APP_CAMPAIGN",
            "com.example.app",
            "GOOGLE_APP_STORE",
            "OPTIMIZE_INSTALLS_TARGET_INSTALL_COST",
            50_000L,
            10_000L,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            false,
            null,
            null),
        99L);

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutateAll(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            operations.capture());

    Map<String, Object> campaignOperation =
        (Map<String, Object>) operations.getValue().get(1).get("campaignOperation");
    Map<String, Object> campaign =
        (Map<String, Object>) campaignOperation.get("create");

    assertThat(campaign.get("advertisingChannelType")).isEqualTo("MULTI_CHANNEL");
    assertThat(campaign.get("advertisingChannelSubType")).isEqualTo("APP_CAMPAIGN");
    assertThat(campaign.get("status")).isEqualTo("PAUSED");
    assertThat(campaign).containsKey("targetCpa");
    assertThat(campaign.get("appCampaignSetting"))
        .isEqualTo(
            Map.of(
                "appId", "com.example.app",
                "appStore", "GOOGLE_APP_STORE",
                "biddingStrategyGoalType", "OPTIMIZE_INSTALLS_TARGET_INSTALL_COST"));
  }

  @Test
  void rejectsApplePreRegistrationBeforeProviderMutation() {
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);

    assertThatThrownBy(
            () ->
                service.create(
                    10L,
                    20L,
                    new CreateGoogleAppCampaignRequest(
                        "Pre-reg",
                        "APP_CAMPAIGN_FOR_PRE_REGISTRATION",
                        "123456789",
                        "APPLE_APP_STORE",
                        "OPTIMIZE_PRE_REGISTRATION_CONVERSION_VOLUME",
                        50_000L,
                        10_000L,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        null,
                        null),
                    99L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Google Play");

    verify(apiClient, never()).mutateAll(any(), any(), any(), any());
  }

  @Test
  void rejectsNonFirebaseConversionForValueOptimization() throws Exception {
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerResourceName("1234567890", "campaignBudgets", "-1"))
        .thenReturn("customers/1234567890/campaignBudgets/-1");
    when(apiClient.customerResourceName("1234567890", "campaigns", "-2"))
        .thenReturn("customers/1234567890/campaigns/-2");

    var conversion =
        objectMapper.readTree(
            """
            {
              "conversionAction": {
                "id":"55",
                "status":"ENABLED",
                "origin":"APP",
                "type":"THIRD_PARTY_APP_ANALYTICS"
              }
            }
            """);
    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), any()))
        .thenReturn(List.of(conversion));

    assertThatThrownBy(
            () ->
                service.create(
                    10L,
                    20L,
                    new CreateGoogleAppCampaignRequest(
                        "Value",
                        "APP_CAMPAIGN",
                        "com.example.app",
                        "GOOGLE_APP_STORE",
                        "OPTIMIZE_RETURN_ON_ADVERTISING_SPEND",
                        50_000L,
                        null,
                        new BigDecimal("3.5"),
                        List.of("55"),
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        null,
                        null),
                    99L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Firebase SDK");

    verify(apiClient, never()).mutateAll(any(), any(), any(), any());
  }
}
