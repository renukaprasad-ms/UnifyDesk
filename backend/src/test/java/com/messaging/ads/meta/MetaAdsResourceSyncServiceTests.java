package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAccountResource;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.PageSummary;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAccountResourceRepository;
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
  @Mock private AdAccountResourceRepository resourceRepository;
  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private MetaAdsTokenService tokenService;
  @Mock private MetaAdsApiClient apiClient;

  private MetaAdsResourceSyncService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsResourceSyncService(
            accountRepository,
            resourceRepository,
            connectionRepository,
            tokenService,
            apiClient,
            new ObjectMapper());
  }

  @Test
  void syncMarksMissingResourcesInaccessibleWithoutDeletingHistory() {
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

    AdAccountResource page = resource(1L, AdAccountResourceType.FACEBOOK_PAGE, "page-1");
    AdAccountResource removedPixel =
        resource(2L, AdAccountResourceType.META_PIXEL, "pixel-old");

    when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(account));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection));
    when(tokenService.read(CONNECTION_ID))
        .thenReturn(
            new MetaAdsTokenService.TokenValue(
                new MetaAdsStoredToken("access-token", "bearer", "v26.0", "meta-user"), null));
    when(apiClient.promotePages("access-token", "123"))
        .thenReturn(List.of(new PageSummary("page-1", "Acme")));
    when(apiClient.instagramAccounts("access-token", "123")).thenReturn(List.of());
    when(apiClient.pixels("access-token", "123")).thenReturn(List.of());
    when(resourceRepository
            .findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
                COMPANY_ID, ACCOUNT_ID, AdAccountResourceType.FACEBOOK_PAGE, "page-1"))
        .thenReturn(Optional.of(page));
    when(resourceRepository.findByCompanyIdAndAdAccountId(COMPANY_ID, ACCOUNT_ID))
        .thenReturn(List.of(page, removedPixel));
    when(resourceRepository.findByCompanyIdAndAdAccountIdOrderByResourceTypeAscNameAsc(
            COMPANY_ID, ACCOUNT_ID))
        .thenReturn(List.of(page, removedPixel));

    var response = service.sync(COMPANY_ID, ACCOUNT_ID);

    assertThat(response.pages()).isEqualTo(1);
    assertThat(response.instagramAccounts()).isZero();
    assertThat(response.pixels()).isZero();
    assertThat(page.isAccessible()).isTrue();
    assertThat(page.getName()).isEqualTo("Acme");
    assertThat(removedPixel.isAccessible()).isFalse();
  }

  private AdAccountResource resource(
      Long id, AdAccountResourceType type, String externalResourceId) {
    AdAccountResource resource = new AdAccountResource();
    resource.setId(id);
    resource.setCompanyId(COMPANY_ID);
    resource.setAdAccountId(ACCOUNT_ID);
    resource.setProvider(AdProvider.META_ADS);
    resource.setResourceType(type);
    resource.setExternalResourceId(externalResourceId);
    resource.setName(externalResourceId);
    resource.setAccessible(true);
    return resource;
  }
}
