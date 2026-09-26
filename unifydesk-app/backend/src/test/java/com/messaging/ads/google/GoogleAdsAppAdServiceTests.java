package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.Ad;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleAppAdGroupRequest;
import com.messaging.ads.google.dto.CreateGoogleAppAdRequest;
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
class GoogleAdsAppAdServiceTests {

  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdSetRepository adSetRepository;
  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private GoogleAdsAdGroupSyncService adGroupSyncService;
  @Mock private GoogleAdsAdGroupPersistenceService adGroupPersistenceService;
  @Mock private GoogleAdsAdSyncService adSyncService;
  @Mock private GoogleAdsAdPersistenceService adPersistenceService;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsAppAdService service;
  private GoogleAdsAccountContextService.Context context;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsAppAdService(
            campaignRepository,
            adSetRepository,
            contextService,
            apiClient,
            adGroupSyncService,
            adGroupPersistenceService,
            adSyncService,
            adPersistenceService,
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
  void engagementAdGroupRequiresUserList() {
    AdCampaign campaign = appCampaign("APP_CAMPAIGN_FOR_ENGAGEMENT");
    when(campaignRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(Optional.of(campaign));
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);

    assertThatThrownBy(
            () ->
                service.createAdGroup(
                    10L,
                    30L,
                    new CreateGoogleAppAdGroupRequest(
                        "Engaged users", 20_000L, null),
                    99L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("user list");

    verify(apiClient, never()).mutateAll(any(), any(), any(), any());
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void createsPreRegistrationAdUsingCorrectFormat() throws Exception {
    AdCampaign campaign = appCampaign("APP_CAMPAIGN_FOR_PRE_REGISTRATION");
    AdSet adGroup = appAdGroup();
    when(adSetRepository.findByIdAndCompanyId(40L, 10L))
        .thenReturn(Optional.of(adGroup));
    when(campaignRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(Optional.of(campaign));
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerResourceName("1234567890", "adGroups", "888"))
        .thenReturn("customers/1234567890/adGroups/888");

    var response =
        objectMapper.readTree(
            """
            {"results":[{"resourceName":"customers/1234567890/adGroupAds/888~999"}]}
            """);
    when(apiClient.mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("adGroupAds"),
            any()))
        .thenReturn(response);
    when(apiClient.firstMutatedResourceName(response))
        .thenReturn("customers/1234567890/adGroupAds/888~999");

    var row =
        objectMapper.readTree(
            """
            {
              "adGroupAd":{
                "resourceName":"customers/1234567890/adGroupAds/888~999",
                "status":"PAUSED",
                "ad":{"id":"999","name":"Pre-reg ad","type":"APP_PRE_REGISTRATION_AD"}
              }
            }
            """);
    when(adSyncService.ad(context, "888", "999")).thenReturn(row);

    Ad persisted = new Ad();
    persisted.setId(50L);
    persisted.setCompanyId(10L);
    persisted.setAdAccountId(20L);
    persisted.setCampaignId(30L);
    persisted.setAdSetId(40L);
    persisted.setProvider(AdProvider.GOOGLE_ADS);
    persisted.setExternalAdId("888~999");
    persisted.setName("Pre-reg ad");
    persisted.setStatus(AdEntityStatus.PAUSED);
    persisted.setAccessible(true);
    when(adPersistenceService.persistOne(10L, 40L, row)).thenReturn(persisted);

    service.createAd(
        10L,
        40L,
        new CreateGoogleAppAdRequest(
            "Pre-reg ad",
            List.of("Register now"),
            List.of("Be first to try the app"),
            List.of("customers/1234567890/assets/11"),
            List.of()),
        99L);

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("adGroupAds"),
            operations.capture());

    Map<String, Object> create =
        (Map<String, Object>) ((Map<String, Object>) operations.getValue().getFirst()).get("create");
    Map<String, Object> ad = (Map<String, Object>) create.get("ad");
    assertThat(ad).containsKey("appPreRegistrationAd");
    assertThat(ad).doesNotContainKeys("appAd", "appEngagementAd");
  }

  private AdCampaign appCampaign(String subtype) {
    AdCampaign campaign = new AdCampaign();
    campaign.setId(30L);
    campaign.setCompanyId(10L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setExternalCampaignId("777");
    campaign.setName("App campaign");
    campaign.setObjective("APP");
    campaign.setBuyingType(subtype);
    campaign.setStatus(AdEntityStatus.PAUSED);
    campaign.setAccessible(true);
    return campaign;
  }

  private AdSet appAdGroup() {
    AdSet adGroup = new AdSet();
    adGroup.setId(40L);
    adGroup.setCompanyId(10L);
    adGroup.setAdAccountId(20L);
    adGroup.setCampaignId(30L);
    adGroup.setProvider(AdProvider.GOOGLE_ADS);
    adGroup.setExternalAdSetId("888");
    adGroup.setName("App group");
    adGroup.setStatus(AdEntityStatus.PAUSED);
    adGroup.setAccessible(true);
    return adGroup;
  }
}
