package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.messaging.ads.dto.AdAccountResourceResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.CreateMetaPixelRequest;
import com.messaging.ads.meta.dto.MetaAdsResourceSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaAdsPixelMutationServiceTests {

  private static final Long COMPANY_ID = 10L;
  private static final Long ACCOUNT_ID = 20L;
  private static final Long CONNECTION_ID = 30L;

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private MetaAdsTokenService tokenService;
  @Mock private MetaAdsApiClient apiClient;
  @Mock private MetaAdsResourceSyncService resourceSyncService;

  private MetaAdsPixelMutationService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsPixelMutationService(
            accountRepository,
            connectionRepository,
            tokenService,
            apiClient,
            resourceSyncService);
  }

  @Test
  void createsPixelAndReturnsSynchronizedResource() {
    when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(account()));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection()));
    when(tokenService.read(CONNECTION_ID))
        .thenReturn(
            new MetaAdsTokenService.TokenValue(
                new MetaAdsStoredToken("token", "bearer", "v26.0", "meta-user"), null));
    when(apiClient.createPixel("token", "123456", "Website Pixel")).thenReturn("pixel-1");

    AdAccountResourceResponse resource =
        new AdAccountResourceResponse(
            "99",
            COMPANY_ID.toString(),
            ACCOUNT_ID.toString(),
            AdProvider.META_ADS,
            AdAccountResourceType.META_PIXEL,
            "pixel-1",
            "Website Pixel",
            true,
            null);
    when(resourceSyncService.sync(COMPANY_ID, ACCOUNT_ID))
        .thenReturn(new MetaAdsResourceSyncResponse(0, 0, 1, 0, 0, List.of(resource)));

    var response =
        service.create(
            COMPANY_ID, ACCOUNT_ID, new CreateMetaPixelRequest("Website Pixel"));

    assertThat(response.externalResourceId()).isEqualTo("pixel-1");
    assertThat(response.resourceType()).isEqualTo(AdAccountResourceType.META_PIXEL);
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
