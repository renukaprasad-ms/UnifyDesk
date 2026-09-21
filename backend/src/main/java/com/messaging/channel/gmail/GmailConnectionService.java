package com.messaging.channel.gmail;

import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.gmail.GmailApiClient.GmailProfile;
import com.messaging.channel.gmail.GmailApiClient.TokenResponse;
import com.messaging.channel.gmail.GmailOAuthStateService.OAuthState;
import com.messaging.channel.gmail.dto.GmailAuthorizationResponse;
import com.messaging.channel.gmail.dto.GmailConnectionResponse;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.service.ChannelConnectionService;
import com.messaging.channel.service.ChannelEntitlementService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailConnectionService {

  private final GmailOAuthProperties properties;
  private final GmailOAuthStateService stateService;
  private final GmailApiClient gmailApiClient;
  private final GmailPersistenceService persistenceService;
  private final GmailWatchService watchService;
  private final ChannelConnectionRepository connectionRepository;
  private final ChannelEntitlementService entitlementService;
  private final CompanyAuthorizationService authorizationService;
  private final ChannelConnectionService channelConnectionService;

  public GmailAuthorizationResponse authorization(Long companyId, Long userId) {
    requireConfigured();
    entitlementService.assertCanCreateConnection(companyId, ChannelType.EMAIL);
    String state = stateService.create(companyId, userId);
    return new GmailAuthorizationResponse(gmailApiClient.authorizationUrl(state));
  }

  public GmailAuthorizationResponse authorization(Long companyId, Long userId, String returnTo) {
    if (returnTo == null || returnTo.isBlank()) {
      return authorization(companyId, userId);
    }
    requireConfigured();
    entitlementService.assertCanCreateConnection(companyId, ChannelType.EMAIL);
    String state = stateService.create(companyId, userId, returnTo);
    return new GmailAuthorizationResponse(gmailApiClient.authorizationUrl(state));
  }

  public GmailConnectionResponse complete(String state, String code) {
    return completeWithReturn(state, code).connection();
  }

  public GmailCompletion completeWithReturn(String state, String code) {
    requireConfigured();
    if (code == null || code.isBlank()) {
      throw new BadRequestException("Google authorization code is missing");
    }

    OAuthState oauthState = stateService.consume(state);
    authorizationService.requirePermission(
        oauthState.userId(), oauthState.companyId(), CompanyPermissionKeys.EMAIL_CONNECT);

    TokenResponse token = gmailApiClient.exchangeCode(code);
    if (token.accessToken() == null || token.accessToken().isBlank()) {
      throw new BadRequestException("Google did not return an access token");
    }
    if (token.refreshToken() == null || token.refreshToken().isBlank()) {
      throw new BadRequestException(
          "Google did not return a refresh token. Revoke the previous grant and connect Gmail again");
    }

    GmailProfile profile = gmailApiClient.profile(token.accessToken());
    if (profile.emailAddress() == null || profile.emailAddress().isBlank()) {
      throw new BadRequestException("Google did not return the Gmail account email address");
    }

    boolean existing =
        connectionRepository
            .findByCompanyIdAndProviderTypeAndExternalAccountId(
                oauthState.companyId(), ProviderType.GMAIL, profile.emailAddress())
            .isPresent();

    if (!existing) {
      entitlementService.assertCanCreateConnection(oauthState.companyId(), ChannelType.EMAIL);
    }

    GmailConnectionResponse response =
        persistenceService.persist(oauthState.companyId(), oauthState.userId(), profile, token);
    channelConnectionService.cancelPendingPurge(
        oauthState.companyId(), Long.valueOf(response.connectionId()));

    connectionRepository
        .findByIdAndCompanyId(Long.valueOf(response.connectionId()), oauthState.companyId())
        .ifPresent(
            connection -> {
              try {
                watchService.ensureWatch(connection, true);
              } catch (RuntimeException exception) {
                log.warn(
                    "Gmail connected but realtime watch setup failed connectionId={} reason={}",
                    connection.getId(),
                    exception.getClass().getSimpleName());
              }
            });

    return new GmailCompletion(response, oauthState.returnTo());
  }

  public void consumeCancelledState(String state) {
    stateService.consume(state);
  }

  public String consumeCancelledStateWithReturn(String state) {
    return stateService.consume(state).returnTo();
  }

  public boolean configured() {
    return properties.configured();
  }

  public record GmailCompletion(GmailConnectionResponse connection, String returnTo) {}

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Gmail is not configured. Configure Google OAuth before connecting Gmail");
    }
  }
}
