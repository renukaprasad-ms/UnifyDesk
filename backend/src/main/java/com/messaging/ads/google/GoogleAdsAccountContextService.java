package com.messaging.ads.google;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsApiClient.GoogleAdsAccountSummary;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsAccountContextService {

  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final GoogleAdsTokenService tokenService;
  private final ObjectMapper objectMapper;

  public Context requireManaged(Long companyId, Long accountId) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));

    if (account.getProvider() != AdProvider.GOOGLE_ADS) {
      throw new BadRequestException("Ad account is not a Google Ads account");
    }
    if (!account.isManaged() || !account.isAccessible()) {
      throw new BadRequestException("Google Ads account is not enabled for management");
    }

    AdProviderConnection connection =
        connectionRepository
            .findByIdAndCompanyId(account.getConnectionId(), companyId)
            .orElseThrow(() -> new NotFoundException("Google Ads connection not found"));
    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Google Ads connection is not active");
    }

    GoogleAdsAccountSummary metadata = metadata(account);
    String accessToken = tokenService.accessToken(connection.getId());
    return new Context(
        account,
        connection,
        accessToken,
        metadata == null ? null : blank(metadata.loginCustomerId()),
        metadata != null && metadata.manager(),
        metadata != null && metadata.testAccount());
  }

  private GoogleAdsAccountSummary metadata(AdAccount account) {
    if (account.getProviderPayloadJson() == null
        || account.getProviderPayloadJson().isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(
          account.getProviderPayloadJson(), GoogleAdsAccountSummary.class);
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Stored Google Ads account metadata is invalid", exception);
    }
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record Context(
      AdAccount account,
      AdProviderConnection connection,
      String accessToken,
      String loginCustomerId,
      boolean managerAccount,
      boolean testAccount) {}
}
