package com.messaging.ads.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.ads.dto.UpdateAdAccountSelectionRequest;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.provider.AdsProviderRegistry;
import com.messaging.ads.repository.AdAccountRepository;
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
class AdCenterServiceTests {

  private static final Long COMPANY_ID = 101L;

  @Mock private AdsProviderRegistry providerRegistry;
  @Mock private AdAccountRepository accountRepository;
  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private AdProviderCredentialService credentialService;

  private AdCenterService service;

  @BeforeEach
  void setUp() {
    service =
        new AdCenterService(
            providerRegistry, accountRepository, connectionRepository, credentialService);
  }

  @Test
  void rejectsManagingAccountWhoseProviderAccessWasRemoved() {
    AdAccount account = account(1L, false, false, false);
    when(accountRepository.findByIdAndCompanyId(1L, COMPANY_ID))
        .thenReturn(Optional.of(account));

    assertThatThrownBy(
            () ->
                service.updateSelection(
                    COMPANY_ID, 1L, new UpdateAdAccountSelectionRequest(true, null)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("no longer accessible");
  }

  @Test
  void firstManagedAccountBecomesPrimaryWhenCallerDoesNotChoose() {
    AdAccount account = account(1L, true, false, false);
    when(accountRepository.findByIdAndCompanyId(1L, COMPANY_ID))
        .thenReturn(Optional.of(account));
    when(accountRepository.findByCompanyIdAndProvider(COMPANY_ID, AdProvider.META_ADS))
        .thenReturn(List.of(account));
    when(accountRepository.save(any(AdAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.updateSelection(
            COMPANY_ID, 1L, new UpdateAdAccountSelectionRequest(true, null));

    assertThat(response.managed()).isTrue();
    assertThat(response.primaryAccount()).isTrue();
  }

  @Test
  void selectingNewPrimaryClearsExistingPrimary() {
    AdAccount currentPrimary = account(1L, true, true, true);
    AdAccount replacement = account(2L, true, false, false);
    when(accountRepository.findByIdAndCompanyId(2L, COMPANY_ID))
        .thenReturn(Optional.of(replacement));
    when(accountRepository.findByCompanyIdAndProvider(COMPANY_ID, AdProvider.META_ADS))
        .thenReturn(List.of(currentPrimary, replacement));
    when(accountRepository.save(any(AdAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.updateSelection(
        COMPANY_ID, 2L, new UpdateAdAccountSelectionRequest(true, true));

    assertThat(currentPrimary.isPrimaryAccount()).isFalse();
    assertThat(replacement.isPrimaryAccount()).isTrue();
    assertThat(replacement.isManaged()).isTrue();
  }

  @Test
  void disconnectDeletesLocalGrantAndDisablesLinkedAccounts() {
    AdProviderConnection connection = new AdProviderConnection();
    connection.setId(99L);
    connection.setCompanyId(COMPANY_ID);
    connection.setProvider(AdProvider.META_ADS);
    connection.setStatus(AdConnectionStatus.ACTIVE);

    AdAccount account = account(1L, true, true, true);
    account.setConnectionId(99L);

    when(connectionRepository.findByIdAndCompanyId(99L, COMPANY_ID))
        .thenReturn(Optional.of(connection));
    when(accountRepository.findByCompanyIdAndConnectionId(COMPANY_ID, 99L))
        .thenReturn(List.of(account));
    when(connectionRepository.save(any(AdProviderConnection.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.disconnect(COMPANY_ID, 99L);

    verify(credentialService).deleteAll(99L);
    assertThat(account.isAccessible()).isFalse();
    assertThat(account.isManaged()).isFalse();
    assertThat(account.isPrimaryAccount()).isFalse();
    assertThat(response.status()).isEqualTo(AdConnectionStatus.DISCONNECTED);
  }

  private AdAccount account(
      Long id, boolean accessible, boolean managed, boolean primaryAccount) {
    AdAccount account = new AdAccount();
    account.setId(id);
    account.setCompanyId(COMPANY_ID);
    account.setConnectionId(99L);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId("account-" + id);
    account.setName("Account " + id);
    account.setStatus(AdAccountStatus.ACTIVE);
    account.setAccessible(accessible);
    account.setManaged(managed);
    account.setPrimaryAccount(primaryAccount);
    return account;
  }
}
