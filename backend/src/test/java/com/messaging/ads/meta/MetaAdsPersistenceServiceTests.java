package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.AdAccountSummary;
import com.messaging.ads.meta.MetaAdsApiClient.BusinessRef;
import com.messaging.ads.meta.MetaAdsApiClient.BusinessSummary;
import com.messaging.ads.meta.MetaAdsApiClient.TokenResponse;
import com.messaging.ads.meta.MetaAdsApiClient.UserProfile;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.ads.service.AdProviderCredentialService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaAdsPersistenceServiceTests {

  private static final Long COMPANY_ID = 101L;
  private static final Long CONNECTION_ID = 500L;

  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private AdAccountRepository accountRepository;
  @Mock private AdProviderCredentialService credentialService;

  private MetaAdsPersistenceService service;

  @BeforeEach
  void setUp() {
    MetaAdsProperties properties = new MetaAdsProperties();
    properties.setGraphApiVersion("v26.0");
    service =
        new MetaAdsPersistenceService(
            connectionRepository,
            accountRepository,
            credentialService,
            properties,
            new ObjectMapper());
  }

  @Test
  void reconnectPreservesReturnedAccountAndDisablesAccountNoLongerAccessible() {
    AdProviderConnection connection = new AdProviderConnection();
    connection.setId(CONNECTION_ID);
    connection.setCompanyId(COMPANY_ID);
    connection.setProvider(AdProvider.META_ADS);
    connection.setStatus(AdConnectionStatus.ACTIVE);

    AdAccount retained = account(1L, "111", true, true, true);
    AdAccount removed = account(2L, "222", true, true, false);

    when(connectionRepository.findByCompanyIdAndProviderAndExternalUserId(
            COMPANY_ID, AdProvider.META_ADS, "meta-user"))
        .thenReturn(Optional.of(connection));
    when(connectionRepository.saveAndFlush(connection)).thenReturn(connection);
    when(accountRepository.findByCompanyIdAndConnectionId(COMPANY_ID, CONNECTION_ID))
        .thenReturn(List.of(retained, removed));
    when(accountRepository.findByCompanyIdAndProviderAndExternalAccountId(
            COMPANY_ID, AdProvider.META_ADS, "111"))
        .thenReturn(Optional.of(retained));
    when(accountRepository.save(any(AdAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.persist(
        COMPANY_ID,
        202L,
        new UserProfile("meta-user", "Acme Admin"),
        List.of(new BusinessSummary("business-1", "Acme")),
        List.of(
            new AdAccountSummary(
                "act_111",
                "111",
                "Acme Ads",
                "INR",
                "Asia/Kolkata",
                1,
                new BusinessRef("business-1", "Acme"))),
        new TokenResponse("long-token", "bearer", 3600L));

    assertThat(retained.isAccessible()).isTrue();
    assertThat(retained.isManaged()).isTrue();
    assertThat(retained.isPrimaryAccount()).isTrue();
    assertThat(retained.getName()).isEqualTo("Acme Ads");

    assertThat(removed.isAccessible()).isFalse();
    assertThat(removed.isManaged()).isFalse();
    assertThat(removed.isPrimaryAccount()).isFalse();
  }

  private AdAccount account(
      Long id,
      String externalAccountId,
      boolean accessible,
      boolean managed,
      boolean primaryAccount) {
    AdAccount account = new AdAccount();
    account.setId(id);
    account.setCompanyId(COMPANY_ID);
    account.setConnectionId(CONNECTION_ID);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId(externalAccountId);
    account.setName("Account " + externalAccountId);
    account.setStatus(AdAccountStatus.ACTIVE);
    account.setAccessible(accessible);
    account.setManaged(managed);
    account.setPrimaryAccount(primaryAccount);
    return account;
  }
}
