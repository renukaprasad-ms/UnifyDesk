package com.messaging.ads.service;

import com.messaging.ads.dto.AdAccountResponse;
import com.messaging.ads.dto.AdProviderConnectionResponse;
import com.messaging.ads.dto.AdProviderResponse;
import com.messaging.ads.dto.UpdateAdAccountSelectionRequest;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.provider.AdsProviderRegistry;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdCenterService {

  private final AdsProviderRegistry providerRegistry;
  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final AdProviderCredentialService credentialService;

  public List<AdProviderResponse> providers() {
    return Arrays.stream(AdProvider.values())
        .map(
            provider ->
                new AdProviderResponse(
                    provider,
                    provider.displayName(),
                    providerRegistry.isConfigured(provider),
                    providerRegistry.capabilities(provider)))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<AdProviderConnectionResponse> connections(Long companyId) {
    return connectionRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
        .map(AdProviderConnectionResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<AdAccountResponse> accounts(Long companyId) {
    return accountRepository.findByCompanyIdOrderByProviderAscNameAsc(companyId).stream()
        .map(AdAccountResponse::from)
        .toList();
  }

  @Transactional
  public AdAccountResponse updateSelection(
      Long companyId, Long accountId, UpdateAdAccountSelectionRequest request) {
    var account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));

    boolean managed = Boolean.TRUE.equals(request.managed());
    if (Boolean.TRUE.equals(request.primaryAccount()) && !managed) {
      throw new BadRequestException("A primary ad account must be managed");
    }

    List<com.messaging.ads.entity.AdAccount> providerAccounts =
        accountRepository.findByCompanyIdAndProvider(companyId, account.getProvider());

    account.setManaged(managed);
    if (!managed) {
      account.setPrimaryAccount(false);
    } else if (Boolean.TRUE.equals(request.primaryAccount())) {
      providerAccounts.stream()
          .filter(candidate -> !candidate.getId().equals(account.getId()))
          .forEach(candidate -> candidate.setPrimaryAccount(false));
      account.setPrimaryAccount(true);
    } else if (Boolean.FALSE.equals(request.primaryAccount())) {
      account.setPrimaryAccount(false);
    } else if (providerAccounts.stream()
        .noneMatch(
            candidate ->
                candidate.isManaged()
                    && candidate.isPrimaryAccount()
                    && !candidate.getId().equals(account.getId()))) {
      account.setPrimaryAccount(true);
    }

    accountRepository.saveAll(providerAccounts);
    return AdAccountResponse.from(accountRepository.save(account));
  }

  @Transactional
  public AdProviderConnectionResponse disconnect(Long companyId, Long connectionId) {
    var connection =
        connectionRepository
            .findByIdAndCompanyId(connectionId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising provider connection not found"));

    credentialService.deleteAll(connection.getId());
    accountRepository.findByCompanyIdAndConnectionId(companyId, connectionId).forEach(
        account -> {
          account.setManaged(false);
          account.setPrimaryAccount(false);
        });

    connection.setStatus(AdConnectionStatus.DISCONNECTED);
    connection.setLastError(null);
    return AdProviderConnectionResponse.from(connectionRepository.save(connection));
  }
}
