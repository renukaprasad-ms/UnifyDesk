package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsApiClient.GoogleAdsAccountSummary;
import com.messaging.ads.google.GoogleAdsApiClient.TokenResponse;
import com.messaging.ads.google.GoogleAdsApiClient.UserProfile;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.ads.service.AdAccountResourceLifecycleService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsPersistenceServiceTests {

  private static final Long COMPANY_ID = 101L;
  private static final Long CONNECTION_ID = 700L;

  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private AdAccountRepository accountRepository;
  @Mock private GoogleAdsTokenService tokenService;
  @Mock private AdAccountResourceLifecycleService resourceLifecycleService;

  private GoogleAdsPersistenceService service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsPersistenceService(
            connectionRepository,
            accountRepository,
            tokenService,
            resourceLifecycleService,
            new ObjectMapper());
  }

  @Test
  void reconnectKeepsManagedAccountAndDisablesCustomerNoLongerAccessible() {
    AdProviderConnection connection = new AdProviderConnection();
    connection.setId(CONNECTION_ID);
    connection.setCompanyId(COMPANY_ID);
    connection.setProvider(AdProvider.GOOGLE_ADS);
    connection.setStatus(AdConnectionStatus.ACTIVE);

    AdAccount retained = account(1L, "1111111111", true, true, true);
    AdAccount removed = account(2L, "2222222222", true, true, false);

    when(connectionRepository.findByCompanyIdAndProviderAndExternalUserId(
            COMPANY_ID, AdProvider.GOOGLE_ADS, "google-user"))
        .thenReturn(Optional.of(connection));
    when(connectionRepository.saveAndFlush(connection)).thenReturn(connection);
    when(accountRepository.findByCompanyIdAndConnectionId(COMPANY_ID, CONNECTION_ID))
        .thenReturn(List.of(retained, removed));
    when(accountRepository.findByCompanyIdAndProviderAndExternalAccountId(
            COMPANY_ID, AdProvider.GOOGLE_ADS, "1111111111"))
        .thenReturn(Optional.of(retained));
    when(accountRepository.save(any(AdAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.persist(
            COMPANY_ID,
            202L,
            new UserProfile("google-user", "Reachlyo Admin", "owner@example.com", true),
            List.of(
                new GoogleAdsAccountSummary(
                    "1111111111",
                    "Reachlyo Test Ads",
                    "INR",
                    "Asia/Kolkata",
                    false,
                    true,
                    "ENABLED",
                    "9999999999",
                    1)),
            new TokenResponse(
                "access-token",
                3600L,
                "refresh-token",
                "openid https://www.googleapis.com/auth/adwords",
                "Bearer",
                "id-token"));

    assertThat(response.accountCount()).isEqualTo(1);
    assertThat(response.testAccountCount()).isEqualTo(1);
    assertThat(response.managerAccountCount()).isZero();

    assertThat(retained.isAccessible()).isTrue();
    assertThat(retained.isManaged()).isTrue();
    assertThat(retained.isPrimaryAccount()).isTrue();
    assertThat(retained.getName()).isEqualTo("Reachlyo Test Ads");
    assertThat(retained.getCurrency()).isEqualTo("INR");
    assertThat(retained.getTimezone()).isEqualTo("Asia/Kolkata");
    assertThat(retained.getStatus()).isEqualTo(AdAccountStatus.ACTIVE);

    assertThat(removed.isAccessible()).isFalse();
    assertThat(removed.isManaged()).isFalse();
    assertThat(removed.isPrimaryAccount()).isFalse();
    verify(resourceLifecycleService).markAllInaccessible(COMPANY_ID, removed.getId());
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
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId(externalAccountId);
    account.setName("Account " + externalAccountId);
    account.setStatus(AdAccountStatus.ACTIVE);
    account.setAccessible(accessible);
    account.setManaged(managed);
    account.setPrimaryAccount(primaryAccount);
    return account;
  }
}
