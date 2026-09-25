package com.messaging.channel.microsoft;

import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.microsoft.MicrosoftApiClient.MicrosoftProfile;
import com.messaging.channel.microsoft.MicrosoftApiClient.TokenResponse;
import com.messaging.channel.microsoft.MicrosoftOAuthStateService.OAuthState;
import com.messaging.channel.microsoft.dto.MicrosoftAuthorizationResponse;
import com.messaging.channel.microsoft.dto.MicrosoftConnectionResponse;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.service.ChannelConnectionLifecycleService;
import com.messaging.channel.service.ChannelEntitlementService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MicrosoftConnectionService {

  private final MicrosoftOAuthProperties properties;
  private final MicrosoftOAuthStateService stateService;
  private final MicrosoftApiClient apiClient;
  private final MicrosoftPersistenceService persistenceService;
  private final MicrosoftSubscriptionService subscriptionService;
  private final ChannelConnectionRepository connectionRepository;
  private final ChannelEntitlementService entitlementService;
  private final CompanyAuthorizationService authorizationService;
  private final ChannelConnectionLifecycleService lifecycleService;

  public MicrosoftAuthorizationResponse authorization(
      Long companyId, Long userId, String returnTo) {
    requireConfigured();
    entitlementService.assertCanCreateConnection(companyId, ChannelType.EMAIL);
    String state = stateService.create(companyId, userId, returnTo);
    return new MicrosoftAuthorizationResponse(apiClient.authorizationUrl(state));
  }

  public MicrosoftCompletion completeWithReturn(String state, String code) {
    requireConfigured();
    if (code == null || code.isBlank()) {
      throw new BadRequestException("Microsoft authorization code is missing");
    }

    OAuthState oauthState = stateService.consume(state);
    authorizationService.requirePermission(
        oauthState.userId(), oauthState.companyId(), CompanyPermissionKeys.EMAIL_CONNECT);

    TokenResponse token = apiClient.exchangeCode(code);
    if (token.accessToken() == null || token.accessToken().isBlank()) {
      throw new BadRequestException("Microsoft did not return an access token");
    }
    if (token.refreshToken() == null || token.refreshToken().isBlank()) {
      throw new BadRequestException(
          "Microsoft did not return a refresh token. Ensure offline_access is consented");
    }

    MicrosoftProfile profile = apiClient.profile(token.accessToken());
    String email = profile.emailAddress();
    if (email == null || email.isBlank()) {
      throw new BadRequestException("Microsoft account did not return an email address");
    }

    boolean existing =
        connectionRepository
            .findByCompanyIdAndProviderTypeAndExternalAccountId(
                oauthState.companyId(), ProviderType.MICROSOFT, email)
            .isPresent();
    if (!existing) {
      entitlementService.assertCanCreateConnection(oauthState.companyId(), ChannelType.EMAIL);
    }

    MicrosoftConnectionResponse response =
        persistenceService.persist(oauthState.companyId(), oauthState.userId(), profile, token);

    Long connectionId = Long.valueOf(response.connectionId());
    lifecycleService.cancelPendingPurge(oauthState.companyId(), connectionId);
    try {
      subscriptionService.ensure(connectionId);
    } catch (RuntimeException ignored) {
      // A mailbox remains usable through periodic/manual delta sync even if webhook setup fails.
    }

    return new MicrosoftCompletion(response, oauthState.returnTo());
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
          "Microsoft email is not configured. Configure Microsoft OAuth first");
    }
  }

  public record MicrosoftCompletion(MicrosoftConnectionResponse connection, String returnTo) {}
}
