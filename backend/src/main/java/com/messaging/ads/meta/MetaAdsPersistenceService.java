package com.messaging.ads.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdCredentialType;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.AdAccountSummary;
import com.messaging.ads.meta.MetaAdsApiClient.BusinessSummary;
import com.messaging.ads.meta.MetaAdsApiClient.TokenResponse;
import com.messaging.ads.meta.MetaAdsApiClient.UserProfile;
import com.messaging.ads.meta.dto.MetaAdsConnectionResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.ads.service.AdProviderCredentialService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetaAdsPersistenceService {

  private final AdProviderConnectionRepository connectionRepository;
  private final AdAccountRepository accountRepository;
  private final AdProviderCredentialService credentialService;
  private final MetaAdsProperties properties;
  private final ObjectMapper objectMapper;

  @Transactional
  public MetaAdsConnectionResponse persist(
      Long companyId,
      Long userId,
      UserProfile profile,
      List<BusinessSummary> businesses,
      List<AdAccountSummary> accounts,
      TokenResponse token) {
    AdProviderConnection connection =
        connectionRepository
            .findByCompanyIdAndProviderAndExternalUserId(
                companyId, AdProvider.META_ADS, profile.id())
            .orElseGet(AdProviderConnection::new);

    if (connection.getId() == null) {
      connection.setCompanyId(companyId);
      connection.setProvider(AdProvider.META_ADS);
      connection.setCreatedByUserId(userId);
    }

    connection.setExternalUserId(profile.id());
    connection.setExternalBusinessId(null);
    connection.setDisplayName(
        profile.name() == null || profile.name().isBlank() ? "Meta Ads" : profile.name().trim());
    connection.setStatus(AdConnectionStatus.ACTIVE);
    connection.setLastError(null);
    connection.setLastSyncAt(Instant.now());
    connection.setProviderPayloadJson(json(new StoredMetaConnectionMetadata(businesses)));
    connection = connectionRepository.saveAndFlush(connection);

    Instant expiresAt =
        token.expiresIn() == null || token.expiresIn() <= 0
            ? null
            : Instant.now().plusSeconds(token.expiresIn());

    credentialService.upsert(
        connection,
        AdCredentialType.OAUTH_TOKEN_SET,
        json(
            new StoredMetaToken(
                token.accessToken(),
                token.tokenType(),
                properties.getGraphApiVersion(),
                profile.id())),
        expiresAt);

    for (AdAccountSummary summary : accounts) {
      String externalAccountId = externalAccountId(summary);
      AdAccount account =
          accountRepository
              .findByCompanyIdAndProviderAndExternalAccountId(
                  companyId, AdProvider.META_ADS, externalAccountId)
              .orElseGet(AdAccount::new);

      if (account.getId() == null) {
        account.setCompanyId(companyId);
        account.setProvider(AdProvider.META_ADS);
        account.setManaged(false);
        account.setPrimaryAccount(false);
      }

      account.setConnectionId(connection.getId());
      account.setExternalAccountId(externalAccountId);
      account.setExternalBusinessId(
          summary.business() == null ? null : summary.business().id());
      account.setName(
          summary.name() == null || summary.name().isBlank()
              ? "Meta Ad Account " + externalAccountId
              : summary.name().trim());
      account.setCurrency(summary.currency());
      account.setTimezone(summary.timezoneName());
      account.setStatus(mapStatus(summary.accountStatus()));
      account.setProviderPayloadJson(json(summary));
      account.setLastSyncAt(Instant.now());
      accountRepository.save(account);
    }

    return new MetaAdsConnectionResponse(
        connection.getId().toString(),
        connection.getDisplayName(),
        businesses.size(),
        accounts.size());
  }

  private String externalAccountId(AdAccountSummary summary) {
    if (summary.accountId() != null && !summary.accountId().isBlank()) {
      return summary.accountId().trim();
    }
    if (summary.id() == null || summary.id().isBlank()) {
      throw new IllegalStateException("Meta returned an ad account without an ID");
    }
    return summary.id().startsWith("act_") ? summary.id().substring(4) : summary.id();
  }

  private AdAccountStatus mapStatus(Integer status) {
    if (status == null) return AdAccountStatus.UNKNOWN;
    return switch (status) {
      case 1 -> AdAccountStatus.ACTIVE;
      case 2 -> AdAccountStatus.DISABLED;
      case 101 -> AdAccountStatus.CLOSED;
      default -> AdAccountStatus.UNKNOWN;
    };
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Meta Ads provider data", exception);
    }
  }

  private record StoredMetaConnectionMetadata(List<BusinessSummary> businesses) {}

  private record StoredMetaToken(
      String accessToken,
      String tokenType,
      String graphApiVersion,
      String externalUserId) {}
}
