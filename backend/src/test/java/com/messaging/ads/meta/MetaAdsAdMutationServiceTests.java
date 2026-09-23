package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.messaging.ads.entity.Ad;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCreative;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.AdSummary;
import com.messaging.ads.meta.MetaAdsApiClient.CreativeRef;
import com.messaging.ads.meta.dto.CreateMetaAdRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCreativeRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.ads.repository.AdRepository;
import com.messaging.ads.repository.AdSetRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaAdsAdMutationServiceTests {
  private static final Long COMPANY_ID = 10L;
  private static final Long ACCOUNT_ID = 20L;
  private static final Long AD_SET_ID = 30L;
  private static final Long CONNECTION_ID = 40L;
  private static final Long CREATIVE_ID = 50L;

  @Mock private AdSetRepository adSetRepository;
  @Mock private AdRepository adRepository;
  @Mock private AdCreativeRepository creativeRepository;
  @Mock private AdAccountRepository accountRepository;
  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private MetaAdsTokenService tokenService;
  @Mock private MetaAdsApiClient apiClient;
  @Mock private MetaAdsAdPersistenceService persistenceService;

  private MetaAdsAdMutationService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsAdMutationService(
            adSetRepository,
            adRepository,
            creativeRepository,
            accountRepository,
            connectionRepository,
            tokenService,
            apiClient,
            persistenceService);
  }

  @Test
  void createAlwaysSendsPausedStatus() {
    when(adSetRepository.findByIdAndCompanyId(AD_SET_ID, COMPANY_ID))
        .thenReturn(Optional.of(adSet()));
    when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(account()));
    when(creativeRepository.findByIdAndCompanyId(CREATIVE_ID, COMPANY_ID))
        .thenReturn(Optional.of(creative()));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection()));
    when(tokenService.read(CONNECTION_ID))
        .thenReturn(
            new MetaAdsTokenService.TokenValue(
                new MetaAdsStoredToken("token", "bearer", "v26.0", "meta-user"), null));

    ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
    when(apiClient.createAd(eq("token"), eq("123456"), payload.capture()))
        .thenReturn("ad-external");

    AdSummary snapshot =
        new AdSummary(
            "ad-external",
            "Launch ad",
            "123456",
            "adset-external",
            "campaign-external",
            new CreativeRef("creative-external", "Creative"),
            "PAUSED",
            "PAUSED",
            null,
            null,
            null,
            null);
    when(apiClient.ad("token", "ad-external")).thenReturn(snapshot);

    Ad stored = new Ad();
    stored.setId(77L);
    stored.setCompanyId(COMPANY_ID);
    stored.setAdAccountId(ACCOUNT_ID);
    stored.setCampaignId(99L);
    stored.setAdSetId(AD_SET_ID);
    stored.setCreativeId(CREATIVE_ID);
    stored.setProvider(AdProvider.META_ADS);
    stored.setExternalAdId("ad-external");
    stored.setName("Launch ad");
    stored.setStatus(AdEntityStatus.PAUSED);
    when(persistenceService.persistOne(COMPANY_ID, AD_SET_ID, snapshot)).thenReturn(stored);

    var response =
        service.create(
            COMPANY_ID,
            AD_SET_ID,
            new CreateMetaAdRequest("Launch ad", CREATIVE_ID.toString()));

    assertThat(response.status()).isEqualTo(AdEntityStatus.PAUSED);
    assertThat(payload.getValue().get("status")).isEqualTo("PAUSED");
    assertThat(payload.getValue().get("adset_id")).isEqualTo("adset-external");
    assertThat(payload.getValue().get("creative"))
        .isEqualTo(Map.of("creative_id", "creative-external"));
  }

  private AdSet adSet() {
    AdSet adSet = new AdSet();
    adSet.setId(AD_SET_ID);
    adSet.setCompanyId(COMPANY_ID);
    adSet.setAdAccountId(ACCOUNT_ID);
    adSet.setCampaignId(99L);
    adSet.setProvider(AdProvider.META_ADS);
    adSet.setExternalAdSetId("adset-external");
    adSet.setAccessible(true);
    return adSet;
  }

  private AdCreative creative() {
    AdCreative creative = new AdCreative();
    creative.setId(CREATIVE_ID);
    creative.setCompanyId(COMPANY_ID);
    creative.setAdAccountId(ACCOUNT_ID);
    creative.setProvider(AdProvider.META_ADS);
    creative.setExternalCreativeId("creative-external");
    creative.setAccessible(true);
    return creative;
  }

  private AdAccount account() {
    AdAccount account = new AdAccount();
    account.setId(ACCOUNT_ID);
    account.setCompanyId(COMPANY_ID);
    account.setConnectionId(CONNECTION_ID);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId("123456");
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
