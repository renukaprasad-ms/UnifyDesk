package com.messaging.ads.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsApiClient.GoogleAdsAccountSummary;
import com.messaging.ads.google.GoogleAdsApiClient.TokenResponse;
import com.messaging.ads.google.GoogleAdsApiClient.UserProfile;
import com.messaging.ads.google.dto.GoogleAdsConnectionResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.ads.service.AdAccountResourceLifecycleService;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleAdsPersistenceService {

  private final AdProviderConnectionRepository connectionRepository;
  private final AdAccountRepository accountRepository;
  private final GoogleAdsTokenService tokenService;
  private final AdAccountResourceLifecycleService resourceLifecycleService;
  private final ObjectMapper objectMapper;

  @Transactional
  public GoogleAdsConnectionResponse persist(
      Long companyId,
      Long userId,
      UserProfile profile,
      List<GoogleAdsAccountSummary> accounts,
      TokenResponse token) {
    String externalUserId = externalUserId(profile);

    AdProviderConnection connection =
        connectionRepository
            .findByCompanyIdAndProviderAndExternalUserId(
                companyId, AdProvider.GOOGLE_ADS, externalUserId)
            .orElseGet(AdProviderConnection::new);

    if (connection.getId() == null) {
      connection.setCompanyId(companyId);
      connection.setProvider(AdProvider.GOOGLE_ADS);
      connection.setCreatedByUserId(userId);
    }

    connection.setExternalUserId(externalUserId);
    connection.setExternalBusinessId(null);
    connection.setDisplayName(displayName(profile));
    connection.setStatus(AdConnectionStatus.ACTIVE);
    connection.setLastError(null);
    connection.setLastSyncAt(Instant.now());
    connection.setProviderPayloadJson(
        json(new StoredGoogleConnectionMetadata(profile.email(), profile.emailVerified())));
    connection = connectionRepository.saveAndFlush(connection);

    tokenService.store(connection, token);

    List<AdAccount> previouslyLinked =
        accountRepository.findByCompanyIdAndConnectionId(companyId, connection.getId());
    Set<String> discoveredIds = new HashSet<>();

    for (GoogleAdsAccountSummary summary : accounts) {
      String customerId = normalizeCustomerId(summary.customerId());
      discoveredIds.add(customerId);

      AdAccount account =
          accountRepository
              .findByCompanyIdAndProviderAndExternalAccountId(
                  companyId, AdProvider.GOOGLE_ADS, customerId)
              .orElseGet(AdAccount::new);

      if (account.getId() == null) {
        account.setCompanyId(companyId);
        account.setProvider(AdProvider.GOOGLE_ADS);
        account.setManaged(false);
        account.setPrimaryAccount(false);
      }

      account.setConnectionId(connection.getId());
      account.setExternalAccountId(customerId);
      account.setExternalBusinessId(null);
      account.setName(accountName(summary, customerId));
      account.setCurrency(summary.currency());
      account.setTimezone(summary.timezone());
      account.setStatus(mapStatus(summary.status()));
      account.setAccessible(true);
      account.setProviderPayloadJson(json(summary));
      account.setLastSyncAt(Instant.now());
      accountRepository.save(account);
    }

    previouslyLinked.stream()
        .filter(account -> !discoveredIds.contains(account.getExternalAccountId()))
        .forEach(
            account -> {
              account.setAccessible(false);
              account.setManaged(false);
              account.setPrimaryAccount(false);
              resourceLifecycleService.markAllInaccessible(companyId, account.getId());
            });
    accountRepository.saveAll(previouslyLinked);

    int managerAccounts = (int) accounts.stream().filter(GoogleAdsAccountSummary::manager).count();
    int testAccounts = (int) accounts.stream().filter(GoogleAdsAccountSummary::testAccount).count();

    return new GoogleAdsConnectionResponse(
        connection.getId().toString(),
        connection.getDisplayName(),
        accounts.size(),
        managerAccounts,
        testAccounts);
  }

  private String externalUserId(UserProfile profile) {
    if (profile.sub() != null && !profile.sub().isBlank()) return profile.sub().trim();
    if (profile.email() != null && !profile.email().isBlank()) return profile.email().trim();
    throw new IllegalStateException("Google did not return an account identifier");
  }

  private String displayName(UserProfile profile) {
    if (profile.name() != null && !profile.name().isBlank()) return profile.name().trim();
    if (profile.email() != null && !profile.email().isBlank()) return profile.email().trim();
    return "Google Ads";
  }

  private String accountName(GoogleAdsAccountSummary summary, String customerId) {
    if (summary.name() != null && !summary.name().isBlank()) return summary.name().trim();
    return (summary.manager() ? "Google Ads Manager " : "Google Ads Account ") + customerId;
  }

  private AdAccountStatus mapStatus(String status) {
    if (status == null || status.isBlank()) return AdAccountStatus.UNKNOWN;
    return switch (status) {
      case "ENABLED" -> AdAccountStatus.ACTIVE;
      case "CANCELED", "CLOSED" -> AdAccountStatus.CLOSED;
      case "SUSPENDED" -> AdAccountStatus.DISABLED;
      default -> AdAccountStatus.UNKNOWN;
    };
  }

  private String normalizeCustomerId(String value) {
    if (value == null) throw new IllegalStateException("Google returned an account without an ID");
    return value.trim().replace("-", "");
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Google Ads provider data", exception);
    }
  }

  private record StoredGoogleConnectionMetadata(String email, Boolean emailVerified) {}
}
