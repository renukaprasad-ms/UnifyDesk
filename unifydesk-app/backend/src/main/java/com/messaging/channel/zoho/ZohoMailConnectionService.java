package com.messaging.channel.zoho;

import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.service.ChannelConnectionLifecycleService;
import com.messaging.channel.service.ChannelEntitlementService;
import com.messaging.channel.zoho.ZohoMailApiClient.TokenResponse;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoAccount;
import com.messaging.channel.zoho.ZohoMailDomains.Region;
import com.messaging.channel.zoho.ZohoMailOAuthStateService.OAuthState;
import com.messaging.channel.zoho.dto.ZohoMailAuthorizationResponse;
import com.messaging.channel.zoho.dto.ZohoMailConnectionResponse;
import com.messaging.common.exception.BadRequestException;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ZohoMailConnectionService {

  private final ZohoMailOAuthProperties properties;
  private final ZohoMailOAuthStateService stateService;
  private final ZohoMailApiClient apiClient;
  private final ZohoMailPersistenceService persistenceService;
  private final ChannelConnectionRepository connectionRepository;
  private final ChannelEntitlementService entitlementService;
  private final CompanyAuthorizationService authorizationService;
  private final ChannelConnectionLifecycleService lifecycleService;

  public ZohoMailAuthorizationResponse authorization(
      Long companyId, Long userId, String returnTo) {
    requireConfigured();
    entitlementService.assertCanCreateConnection(companyId, ChannelType.EMAIL);
    String state = stateService.create(companyId, userId, returnTo);
    return new ZohoMailAuthorizationResponse(apiClient.authorizationUrl(state));
  }

  public ZohoCompletion completeWithReturn(
      String state,
      String code,
      String location,
      String accountsServer) {
    requireConfigured();
    if (code == null || code.isBlank()) {
      throw new BadRequestException("Zoho authorization code is missing");
    }

    OAuthState oauthState = stateService.consume(state);
    authorizationService.requirePermission(
        oauthState.userId(), oauthState.companyId(), CompanyPermissionKeys.EMAIL_CONNECT);

    Region region = ZohoMailDomains.resolve(location, accountsServer);
    TokenResponse token = apiClient.exchangeCode(region.accountsServer(), code);

    if (token.accessToken() == null || token.accessToken().isBlank()) {
      throw new BadRequestException("Zoho did not return an access token");
    }
    if (token.refreshToken() == null || token.refreshToken().isBlank()) {
      throw new BadRequestException(
          "Zoho did not return a refresh token. Reconnect and grant offline access");
    }

    ZohoAccount account =
        apiClient.accounts(region.mailApiBaseUrl(), token.accessToken()).stream()
            .filter(item -> item != null && item.email() != null && !item.email().isBlank())
            .filter(item -> item.type() == null || "ZOHO_ACCOUNT".equalsIgnoreCase(item.type()))
            .filter(item -> !Boolean.FALSE.equals(item.enabled()))
            .min(Comparator.comparing(item -> item.email().toLowerCase(java.util.Locale.ROOT)))
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "Zoho did not return an enabled Zoho Mail account"));

    String email = account.email().trim().toLowerCase(java.util.Locale.ROOT);
    boolean existing =
        connectionRepository
            .findByCompanyIdAndProviderTypeAndExternalAccountId(
                oauthState.companyId(), ProviderType.ZOHO_MAIL, email)
            .isPresent();
    if (!existing) {
      entitlementService.assertCanCreateConnection(oauthState.companyId(), ChannelType.EMAIL);
    }

    ZohoMailConnectionResponse response =
        persistenceService.persist(
            oauthState.companyId(),
            oauthState.userId(),
            account,
            token,
            location.toLowerCase(java.util.Locale.ROOT),
            region.accountsServer(),
            region.mailApiBaseUrl());

    lifecycleService.cancelPendingPurge(
        oauthState.companyId(), Long.valueOf(response.connectionId()));
    return new ZohoCompletion(response, oauthState.returnTo());
  }

  public String consumeCancelledStateWithReturn(String state) {
    return stateService.consume(state).returnTo();
  }

  public boolean configured() {
    return properties.configured();
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Zoho Mail is not configured. Configure the Zoho OAuth environment variables first");
    }
  }

  public record ZohoCompletion(ZohoMailConnectionResponse connection, String returnTo) {}
}
