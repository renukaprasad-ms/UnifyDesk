package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAccountResource;
import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.PageSummary;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAccountResourceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaAdsResourcePersistenceServiceTests {

  private static final Long COMPANY_ID = 101L;
  private static final Long ACCOUNT_ID = 202L;

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdAccountResourceRepository resourceRepository;

  private MetaAdsResourcePersistenceService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsResourcePersistenceService(
            accountRepository, resourceRepository, new ObjectMapper());
  }

  @Test
  void marksMissingResourcesInaccessibleWithoutDeletingHistoricalRows() {
    AdAccount account = new AdAccount();
    account.setId(ACCOUNT_ID);
    account.setCompanyId(COMPANY_ID);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId("123");
    account.setName("Acme Ads");
    account.setStatus(AdAccountStatus.ACTIVE);
    account.setManaged(true);
    account.setAccessible(true);

    AdAccountResource page = resource(1L, AdAccountResourceType.FACEBOOK_PAGE, "page-1");
    AdAccountResource removedPixel =
        resource(2L, AdAccountResourceType.META_PIXEL, "pixel-old");

    when(accountRepository.findByIdAndCompanyIdForUpdate(ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(account));
    when(resourceRepository
            .findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
                COMPANY_ID, ACCOUNT_ID, AdAccountResourceType.FACEBOOK_PAGE, "page-1"))
        .thenReturn(Optional.of(page));
    when(resourceRepository.save(any(AdAccountResource.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(resourceRepository.findByCompanyIdAndAdAccountId(COMPANY_ID, ACCOUNT_ID))
        .thenReturn(List.of(page, removedPixel));
    when(resourceRepository.findByCompanyIdAndAdAccountIdOrderByResourceTypeAscNameAsc(
            COMPANY_ID, ACCOUNT_ID))
        .thenReturn(List.of(page, removedPixel));

    var response =
        service.persist(
            COMPANY_ID,
            ACCOUNT_ID,
            List.of(new PageSummary("page-1", "Acme")),
            List.of(),
            List.of());

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
