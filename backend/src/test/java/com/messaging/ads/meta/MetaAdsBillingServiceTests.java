package com.messaging.ads.meta;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.messaging.ads.dto.AdAccountBillingResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.AdAccountBillingSummary;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaAdsBillingServiceTests {

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private MetaAdsTokenService tokenService;
  @Mock private MetaAdsApiClient apiClient;
  @Mock private MetaAdsBillingPersistenceService persistenceService;

  private MetaAdsBillingService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsBillingService(
            accountRepository,
            connectionRepository,
            tokenService,
            apiClient,
            persistenceService);
  }

  @Test
  void updatesSpendCapThenRefetchesAndPersistsAcceptedMetaState() {
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

    AdAccountBillingSummary accepted =
        new AdAccountBillingSummary(
            "act_123456",
            "123456",
            "Acme Ads",
            "INR",
            1,
            "5000",
            "12500",
            "25000",
            null,
            null,
            null,
            null,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            "IN");

    when(apiClient.accountBilling("token", "123456")).thenReturn(accepted);
    AdAccountBillingResponse persisted = AdAccountBillingResponse.empty(account);
    when(persistenceService.persist(10L, 20L, accepted)).thenReturn(persisted);

    service.updateSpendCap(10L, 20L, 25000L);

    InOrder order = inOrder(apiClient, persistenceService);
    order.verify(apiClient).updateAccountSpendCap("token", "123456", 25000L);
    order.verify(apiClient).accountBilling("token", "123456");
    order.verify(persistenceService).persist(10L, 20L, accepted);
  }

  private AdAccount account() {
    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setConnectionId(30L);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId("123456");
    account.setName("Acme Ads");
    account.setCurrency("INR");
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
