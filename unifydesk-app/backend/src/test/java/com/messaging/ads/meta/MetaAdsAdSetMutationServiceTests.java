package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.AdSetSummary;
import com.messaging.ads.meta.dto.CreateMetaAdSetRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdConversionRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.ads.repository.AdSetRepository;
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
class MetaAdsAdSetMutationServiceTests {

  private static final Long COMPANY_ID = 10L;
  private static final Long CAMPAIGN_ID = 20L;
  private static final Long ACCOUNT_ID = 30L;
  private static final Long CONNECTION_ID = 40L;

  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdSetRepository adSetRepository;
  @Mock private AdAudienceRepository audienceRepository;
  @Mock private AdConversionRepository conversionRepository;
  @Mock private AdAccountRepository accountRepository;
  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private MetaAdsTokenService tokenService;
  @Mock private MetaAdsApiClient apiClient;
  @Mock private MetaAdsAdSetPersistenceService persistenceService;

  private MetaAdsAdSetMutationService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsAdSetMutationService(
            campaignRepository,
            adSetRepository,
            audienceRepository,
            conversionRepository,
            accountRepository,
            connectionRepository,
            tokenService,
            apiClient,
            persistenceService);
  }

  @Test
  void createBuildsPausedTargetingPayload() {
    AdCampaign campaign = campaign(false);
    AdAccount account = account();
    AdProviderConnection connection = connection();

    when(campaignRepository.findByIdAndCompanyId(CAMPAIGN_ID, COMPANY_ID))
        .thenReturn(Optional.of(campaign));
    when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(account));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection));
    when(tokenService.read(CONNECTION_ID))
        .thenReturn(
            new MetaAdsTokenService.TokenValue(
                new MetaAdsStoredToken("access-token", "bearer", "v26.0", "meta-user"), null));

    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    when(apiClient.createAdSet(
            eq("access-token"), eq("123456"), payloadCaptor.capture()))
        .thenReturn("meta-ad-set");

    AdSetSummary snapshot =
        new AdSetSummary(
            "meta-ad-set",
            "India traffic",
            "123456",
            "meta-campaign",
            "LINK_CLICKS",
            "IMPRESSIONS",
            "LOWEST_COST_WITHOUT_CAP",
            "WEBSITE",
            null,
            "2100",
            null,
            null,
            null,
            null,
            null,
            null,
            "PAUSED",
            "PAUSED",
            null,
            null);
    when(apiClient.adSet("access-token", "meta-ad-set")).thenReturn(snapshot);

    AdSet stored = new AdSet();
    stored.setId(99L);
    stored.setCompanyId(COMPANY_ID);
    stored.setAdAccountId(ACCOUNT_ID);
    stored.setCampaignId(CAMPAIGN_ID);
    stored.setProvider(AdProvider.META_ADS);
    stored.setExternalAdSetId("meta-ad-set");
    stored.setName("India traffic");
    stored.setStatus(AdEntityStatus.PAUSED);
    when(persistenceService.persistOne(COMPANY_ID, CAMPAIGN_ID, snapshot))
        .thenReturn(stored);

    var response =
        service.create(
            COMPANY_ID,
            CAMPAIGN_ID,
            new CreateMetaAdSetRequest(
                "India traffic",
                "LINK_CLICKS",
                "IMPRESSIONS",
                "LOWEST_COST_WITHOUT_CAP",
                null,
                2100L,
                null,
                null,
                null,
                "WEBSITE",
                List.of(new CreateMetaAdSetRequest.TargetingLocation("IN", List.of("home"))),
                21,
                65,
                List.of(1),
                List.of("instagram"),
                List.of(),
                List.of("stream"),
                List.of(),
                List.of(),
                null,
                null,
                null));

    assertThat(response.status()).isEqualTo(AdEntityStatus.PAUSED);

    Map<String, Object> payload = payloadCaptor.getValue();
    assertThat(payload.get("status")).isEqualTo("PAUSED");
    assertThat(payload.get("daily_budget")).isEqualTo(2100L);
    assertThat(payload.get("campaign_id")).isEqualTo("meta-campaign");

    @SuppressWarnings("unchecked")
    Map<String, Object> targeting = (Map<String, Object>) payload.get("targeting");
    assertThat(targeting.get("age_min")).isEqualTo(21);
    assertThat(targeting.get("age_max")).isEqualTo(65);
    assertThat(targeting.get("publisher_platforms")).isEqualTo(List.of("instagram"));
  }

  @Test
  void createBuildsDetailedGeoAndRadiusPayload() {
    AdCampaign campaign = campaign(false);
    AdAccount account = account();
    AdProviderConnection connection = connection();

    when(campaignRepository.findByIdAndCompanyId(CAMPAIGN_ID, COMPANY_ID))
        .thenReturn(Optional.of(campaign));
    when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(account));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection));
    when(tokenService.read(CONNECTION_ID))
        .thenReturn(
            new MetaAdsTokenService.TokenValue(
                new MetaAdsStoredToken("access-token", "bearer", "v26.0", "meta-user"), null));

    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    when(apiClient.createAdSet(
            eq("access-token"), eq("123456"), payloadCaptor.capture()))
        .thenReturn("meta-ad-set");

    AdSetSummary snapshot =
        new AdSetSummary(
            "meta-ad-set",
            "Bengaluru traffic",
            "123456",
            "meta-campaign",
            "LINK_CLICKS",
            "IMPRESSIONS",
            "LOWEST_COST_WITHOUT_CAP",
            "WEBSITE",
            null,
            "2100",
            null,
            null,
            null,
            null,
            null,
            null,
            "PAUSED",
            "PAUSED",
            null,
            null);
    when(apiClient.adSet("access-token", "meta-ad-set")).thenReturn(snapshot);

    AdSet stored = new AdSet();
    stored.setId(100L);
    stored.setCompanyId(COMPANY_ID);
    stored.setAdAccountId(ACCOUNT_ID);
    stored.setCampaignId(CAMPAIGN_ID);
    stored.setProvider(AdProvider.META_ADS);
    stored.setExternalAdSetId("meta-ad-set");
    stored.setName("Bengaluru traffic");
    stored.setStatus(AdEntityStatus.PAUSED);
    when(persistenceService.persistOne(COMPANY_ID, CAMPAIGN_ID, snapshot))
        .thenReturn(stored);

    service.create(
        COMPANY_ID,
        CAMPAIGN_ID,
        new CreateMetaAdSetRequest(
            "Bengaluru traffic",
            "LINK_CLICKS",
            "IMPRESSIONS",
            "LOWEST_COST_WITHOUT_CAP",
            null,
            2100L,
            null,
            null,
            null,
            "WEBSITE",
            List.of(
                new CreateMetaAdSetRequest.TargetingLocation(
                    "IN",
                    "CITY",
                    "1028297",
                    "Bengaluru",
                    null,
                    null,
                    null,
                    List.of("home", "recent")),
                new CreateMetaAdSetRequest.TargetingLocation(
                    "IN",
                    "CUSTOM",
                    null,
                    "Office radius",
                    12.9716,
                    77.5946,
                    10.0,
                    List.of("home", "recent"))),
            21,
            65,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null));

    @SuppressWarnings("unchecked")
    Map<String, Object> targeting =
        (Map<String, Object>) payloadCaptor.getValue().get("targeting");
    @SuppressWarnings("unchecked")
    Map<String, Object> geoLocations =
        (Map<String, Object>) targeting.get("geo_locations");

    assertThat(geoLocations.get("cities"))
        .isEqualTo(List.of(Map.of("key", "1028297")));
    assertThat(geoLocations.get("location_types"))
        .isEqualTo(List.of("home", "recent"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> customLocations =
        (List<Map<String, Object>>) geoLocations.get("custom_locations");
    assertThat(customLocations).hasSize(1);
    assertThat(customLocations.getFirst())
        .containsEntry("latitude", 12.9716)
        .containsEntry("longitude", 77.5946)
        .containsEntry("radius", 10.0)
        .containsEntry("distance_unit", "kilometer");
  }

  @Test
  void createRejectsAdSetBudgetWhenCampaignOwnsBudget() {
    AdCampaign campaign = campaign(true);
    when(campaignRepository.findByIdAndCompanyId(CAMPAIGN_ID, COMPANY_ID))
        .thenReturn(Optional.of(campaign));
    when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(account()));

    assertThatThrownBy(
            () ->
                service.create(
                    COMPANY_ID,
                    CAMPAIGN_ID,
                    new CreateMetaAdSetRequest(
                        "Invalid budget",
                        "LINK_CLICKS",
                        "IMPRESSIONS",
                        null,
                        null,
                        2100L,
                        null,
                        null,
                        null,
                        null,
                        List.of(
                            new CreateMetaAdSetRequest.TargetingLocation(
                                "IN", List.of("home"))),
                        18,
                        65,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("campaign-level budget");
  }

  private AdCampaign campaign(boolean withBudget) {
    AdCampaign campaign = new AdCampaign();
    campaign.setId(CAMPAIGN_ID);
    campaign.setCompanyId(COMPANY_ID);
    campaign.setAdAccountId(ACCOUNT_ID);
    campaign.setProvider(AdProvider.META_ADS);
    campaign.setExternalCampaignId("meta-campaign");
    campaign.setName("Campaign");
    campaign.setAccessible(true);
    if (withBudget) campaign.setDailyBudgetMinor(5000L);
    return campaign;
  }

  private AdAccount account() {
    AdAccount account = new AdAccount();
    account.setId(ACCOUNT_ID);
    account.setCompanyId(COMPANY_ID);
    account.setConnectionId(CONNECTION_ID);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId("123456");
    account.setName("Acme Ads");
    account.setManaged(true);
    account.setAccessible(true);
    return account;
  }

  private AdProviderConnection connection() {
    AdProviderConnection connection = new AdProviderConnection();
    connection.setId(CONNECTION_ID);
    connection.setCompanyId(COMPANY_ID);
    connection.setProvider(AdProvider.META_ADS);
    connection.setStatus(AdConnectionStatus.ACTIVE);
    return connection;
  }
}
