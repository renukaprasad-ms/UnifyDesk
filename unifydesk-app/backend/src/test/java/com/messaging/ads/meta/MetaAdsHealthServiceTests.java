package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.messaging.ads.dto.AdAccountHealthResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.AdAccountHealthSummary;
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
class MetaAdsHealthServiceTests {

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private MetaAdsTokenService tokenService;
  @Mock private MetaAdsApiClient apiClient;
  @Mock private MetaAdsHealthPersistenceService persistenceService;

  private MetaAdsHealthService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsHealthService(
            accountRepository,
            connectionRepository,
            tokenService,
            apiClient,
            persistenceService);
  }

  @Test
  void syncsHealthForManagedActiveMetaAccount() {
    AdAccount account = account();
    AdProviderConnection connection = connection();

    when(accountRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(account));
    when(connectionRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(Optional.of(connection));
    when(tokenService.read(30L))
        .thenReturn(
            new MetaAdsTokenService.TokenValue(
                new MetaAdsStoredToken(
                    "token", "bearer", "v26.0", "meta-user"),
                null));

    AdAccountHealthSummary summary =
        new AdAccountHealthSummary(
            "act_123456",
            "123456",
            "Acme Ads",
            1,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of("ADVERTISE"),
            null,
            true,
            true,
            false,
            true,
            null,
            null);

    when(apiClient.accountHealth("token", "123456")).thenReturn(summary);
    AdAccountHealthResponse response = AdAccountHealthResponse.empty(account);
    when(persistenceService.persist(10L, 20L, summary)).thenReturn(response);

    var result = service.sync(10L, 20L);

    assertThat(result.adAccountId()).isEqualTo("20");
    org.mockito.Mockito.verify(apiClient).accountHealth("token", "123456");
    org.mockito.Mockito.verify(persistenceService).persist(10L, 20L, summary);
  }

  private AdAccount account() {
    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setConnectionId(30L);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId("123456");
    account.setName("Acme Ads");
    account.setStatus(AdAccountStatus.ACTIVE);
    account.setManaged(true);
    account.setAccessible(true);
    return account;
  }

  private AdProviderConnection connection() {
    AdProviderConnection connection = new AdProviderConnection();
    connection.setId(30L);
    connection.setCompanyId(10L);
    connection.setProvider(AdProvider.META_ADS);
    connection.setStatus(AdConnectionStatus.ACTIVE);
    return connection;
  }
}
