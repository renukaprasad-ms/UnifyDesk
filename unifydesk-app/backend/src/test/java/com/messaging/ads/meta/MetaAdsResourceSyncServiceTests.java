package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.PageSummary;
import com.messaging.ads.meta.MetaAdsApiClient.ProductCatalogSummary;
import com.messaging.ads.meta.MetaAdsApiClient.ProductSetSummary;
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
class MetaAdsResourceSyncServiceTests {

  private static final Long COMPANY_ID = 101L;
  private static final Long ACCOUNT_ID = 202L;
  private static final Long CONNECTION_ID = 303L;

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private MetaAdsTokenService tokenService;
  @Mock private MetaAdsApiClient apiClient;
  @Mock private MetaAdsResourcePersistenceService persistenceService;

  private MetaAdsResourceSyncService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsResourceSyncService(
            accountRepository,
            connectionRepository,
            tokenService,
            apiClient,
            persistenceService);
  }

  @Test
  void discoversResourcesWithStoredGrantThenPersistsSnapshot() {
    AdAccount account = new AdAccount();
    account.setId(ACCOUNT_ID);
    account.setCompanyId(COMPANY_ID);
    account.setConnectionId(CONNECTION_ID);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId("123");
    account.setName("Acme Ads");
    account.setStatus(AdAccountStatus.ACTIVE);
    account.setManaged(true);
    account.setAccessible(true);

    AdProviderConnection connection = new AdProviderConnection();
    connection.setId(CONNECTION_ID);
    connection.setCompanyId(COMPANY_ID);
    connection.setProvider(AdProvider.META_ADS);
    connection.setStatus(AdConnectionStatus.ACTIVE);

    List<PageSummary> pages = List.of(new PageSummary("page-1", "Acme"));
    MetaAdsResourceSyncResponse expected =
        new MetaAdsResourceSyncResponse(1, 0, 0, 1, 1, List.of());

    when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(account));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection));
    when(tokenService.read(CONNECTION_ID))
        .thenReturn(
            new MetaAdsTokenService.TokenValue(
                new MetaAdsStoredToken("access-token", "bearer", "v26.0", "meta-user"), null));
    when(apiClient.promotePages("access-token", "123")).thenReturn(pages);
    when(apiClient.instagramAccounts("access-token", "123")).thenReturn(List.of());
    when(apiClient.pixels("access-token", "123")).thenReturn(List.of());
    account.setExternalBusinessId("business-1");
    List<ProductCatalogSummary> catalogs =
        List.of(new ProductCatalogSummary("catalog-1", "Acme Catalog", "commerce"));
    List<ProductSetSummary> productSets =
        List.of(
            new ProductSetSummary(
                "set-1",
                "Featured",
                new MetaAdsApiClient.ProductCatalogRef("catalog-1", "Acme Catalog")));
    when(apiClient.productCatalogs("access-token", "business-1")).thenReturn(catalogs);
    when(apiClient.productSets("access-token", "catalog-1")).thenReturn(productSets);
    when(persistenceService.persist(
            COMPANY_ID,
            ACCOUNT_ID,
            pages,
            List.of(),
            List.of(),
            catalogs,
            productSets))
        .thenReturn(expected);

    MetaAdsResourceSyncResponse actual = service.sync(COMPANY_ID, ACCOUNT_ID);

    assertThat(actual).isSameAs(expected);
    verify(persistenceService)
        .persist(
            COMPANY_ID,
            ACCOUNT_ID,
            pages,
            List.of(),
            List.of(),
            catalogs,
            productSets);
  }
}
