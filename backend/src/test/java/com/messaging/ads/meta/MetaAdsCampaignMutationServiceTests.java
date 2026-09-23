package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.CampaignSummary;
import com.messaging.ads.meta.MetaAdsStoredToken;
import com.messaging.ads.meta.dto.CreateMetaCampaignRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaAdsCampaignMutationServiceTests {

  private static final Long COMPANY_ID = 10L;
  private static final Long ACCOUNT_ID = 20L;
  private static final Long CONNECTION_ID = 30L;

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private MetaAdsTokenService tokenService;
  @Mock private MetaAdsApiClient apiClient;
  @Mock private MetaAdsCampaignPersistenceService persistenceService;

  private MetaAdsCampaignMutationService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsCampaignMutationService(
            accountRepository,
            campaignRepository,
            connectionRepository,
            tokenService,
            apiClient,
            persistenceService);
  }

  @Test
  void createNormalizesNoneSpecialCategoryAndDefaultsBuyingType() {
    AdAccount account = managedAccount();
    AdProviderConnection connection = activeConnection();
    when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(account));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection));
    when(tokenService.read(CONNECTION_ID))
        .thenReturn(
            new MetaAdsTokenService.TokenValue(
                new MetaAdsStoredToken("access-token", "bearer", "v26.0", "meta-user"), null));
    when(apiClient.createCampaign(
            eq("access-token"),
            eq("123456"),
            eq("Launch"),
            eq("OUTCOME_TRAFFIC"),
            eq("AUCTION"),
            eq(null),
            eq(5000L),
            eq(null),
            eq(List.of())))
        .thenReturn("meta-campaign");
    CampaignSummary snapshot =
        new CampaignSummary(
            "meta-campaign",
            "Launch",
            "OUTCOME_TRAFFIC",
            "123456",
            "AUCTION",
            "5000",
            null,
            null,
            null,
            "PAUSED",
            "PAUSED",
            null,
            null,
            null,
            null,
            List.of(),
            List.of());
    when(apiClient.campaign("access-token", "meta-campaign")).thenReturn(snapshot);

    AdCampaign stored = new AdCampaign();
    stored.setId(50L);
    stored.setCompanyId(COMPANY_ID);
    stored.setAdAccountId(ACCOUNT_ID);
    stored.setProvider(AdProvider.META_ADS);
    stored.setExternalCampaignId("meta-campaign");
    stored.setName("Launch");
    stored.setStatus(AdEntityStatus.PAUSED);
    when(persistenceService.persistOne(COMPANY_ID, ACCOUNT_ID, snapshot)).thenReturn(stored);

    var response =
        service.create(
            COMPANY_ID,
            ACCOUNT_ID,
            new CreateMetaCampaignRequest(
                "Launch",
                "OUTCOME_TRAFFIC",
                null,
                null,
                5000L,
                null,
                List.of("NONE")));

    assertThat(response.externalCampaignId()).isEqualTo("meta-campaign");
    assertThat(response.status()).isEqualTo(AdEntityStatus.PAUSED);
    verify(apiClient)
        .createCampaign(
            "access-token",
            "123456",
            "Launch",
            "OUTCOME_TRAFFIC",
            "AUCTION",
            null,
            5000L,
            null,
            List.of());
  }

  @Test
  void rejectsUnsupportedCampaignStatusBeforeCallingMeta() {
    AdCampaign campaign = new AdCampaign();
    campaign.setId(99L);
    campaign.setCompanyId(COMPANY_ID);
    campaign.setAdAccountId(ACCOUNT_ID);
    campaign.setProvider(AdProvider.META_ADS);
    campaign.setExternalCampaignId("external-99");
    campaign.setAccessible(true);
    when(campaignRepository.findByIdAndCompanyId(99L, COMPANY_ID))
        .thenReturn(Optional.of(campaign));
    when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(managedAccount()));

    assertThatThrownBy(() -> service.setStatus(COMPANY_ID, 99L, "DELETED"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("ACTIVE or PAUSED");
  }

  private AdAccount managedAccount() {
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

  private AdProviderConnection activeConnection() {
    AdProviderConnection connection = new AdProviderConnection();
    connection.setId(CONNECTION_ID);
    connection.setCompanyId(COMPANY_ID);
    connection.setProvider(AdProvider.META_ADS);
    connection.setStatus(AdConnectionStatus.ACTIVE);
    return connection;
  }
}
