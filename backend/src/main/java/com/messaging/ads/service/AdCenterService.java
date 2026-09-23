package com.messaging.ads.service;

import com.messaging.ads.dto.AdAccountResponse;
import com.messaging.ads.dto.AdProviderConnectionResponse;
import com.messaging.ads.dto.AdProviderResponse;
import com.messaging.ads.dto.UpdateAdAccountSelectionRequest;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.provider.AdsProviderRegistry;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
  private final AdAccountResourceLifecycleService resourceLifecycleService;
  private final AdActivityLogService activityLogService;

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
      Long companyId,
      Long accountId,
      UpdateAdAccountSelectionRequest request,
      Long actorUserId) {
    AdAccount accountSnapshot =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    List<AdAccount> providerAccounts =
        accountRepository.findByCompanyIdAndProviderForUpdate(
            companyId, accountSnapshot.getProvider());
    AdAccount account =
        providerAccounts.stream()
            .filter(candidate -> candidate.getId().equals(accountId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Ad account not found"));

    boolean managed = Boolean.TRUE.equals(request.managed());
    if (managed && !account.isAccessible()) {
      throw new BadRequestException(
          "Ad account is no longer accessible from the connected provider");
    }
    if (Boolean.TRUE.equals(request.primaryAccount()) && !managed) {
      throw new BadRequestException("A primary ad account must be managed");
    }

    Map<String, Object> before =
        Map.of(
            "managed", account.isManaged(),
            "primaryAccount", account.isPrimaryAccount(),
            "accessible", account.isAccessible());

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

    normalizePrimary(providerAccounts);
    accountRepository.saveAll(providerAccounts);
    AdAccount saved = accountRepository.save(account);

    activityLogService.record(
        companyId,
        saved.getId(),
        saved.getProvider(),
        actorUserId,
        "AD_ACCOUNT_SELECTION_UPDATED",
        "AD_ACCOUNT",
        saved.getExternalAccountId(),
        before,
        Map.of(
            "managed", saved.isManaged(),
            "primaryAccount", saved.isPrimaryAccount(),
            "accessible", saved.isAccessible()),
        null);

    return AdAccountResponse.from(saved);
  }

  @Transactional
  public AdProviderConnectionResponse disconnect(
      Long companyId, Long connectionId, Long actorUserId) {
    var connection =
        connectionRepository
            .findByIdAndCompanyId(connectionId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising provider connection not found"));

    credentialService.deleteAll(connection.getId());
    List<AdAccount> providerAccounts =
        accountRepository.findByCompanyIdAndProviderForUpdate(companyId, connection.getProvider());
    List<AdAccount> disconnectedAccounts =
        providerAccounts.stream()
            .filter(account -> account.getConnectionId().equals(connectionId))
            .toList();
    disconnectedAccounts.forEach(
        account -> {
          account.setAccessible(false);
          account.setManaged(false);
          account.setPrimaryAccount(false);
          resourceLifecycleService.markAllInaccessible(companyId, account.getId());
        });

    normalizePrimary(providerAccounts);
    accountRepository.saveAll(providerAccounts);

    connection.setStatus(AdConnectionStatus.DISCONNECTED);
    connection.setLastError(null);
    var saved = connectionRepository.save(connection);

    activityLogService.record(
        companyId,
        null,
        saved.getProvider(),
        actorUserId,
        "AD_PROVIDER_DISCONNECTED",
        "PROVIDER_CONNECTION",
        saved.getId().toString(),
        Map.of("status", AdConnectionStatus.ACTIVE.name()),
        Map.of("status", saved.getStatus().name()),
        Map.of("affectedAccountCount", disconnectedAccounts.size()));

    return AdProviderConnectionResponse.from(saved);
  }

  private void normalizePrimary(List<AdAccount> providerAccounts) {
    List<AdAccount> managedAccounts =
        providerAccounts.stream()
            .filter(AdAccount::isAccessible)
            .filter(AdAccount::isManaged)
            .sorted(java.util.Comparator.comparing(AdAccount::getId))
            .toList();

    if (managedAccounts.isEmpty()) {
      providerAccounts.forEach(account -> account.setPrimaryAccount(false));
      return;
    }

    AdAccount primary =
        managedAccounts.stream()
            .filter(AdAccount::isPrimaryAccount)
            .findFirst()
            .orElse(managedAccounts.getFirst());

    managedAccounts.forEach(
        candidate -> candidate.setPrimaryAccount(candidate.getId().equals(primary.getId())));
    providerAccounts.stream()
        .filter(candidate -> !managedAccounts.contains(candidate))
        .forEach(candidate -> candidate.setPrimaryAccount(false));
  }
}
