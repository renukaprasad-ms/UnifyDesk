package com.messaging.channel.gmail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.credential.ProviderCredentialService;
import com.messaging.channel.dto.ChannelConnectionResponse;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.CredentialType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.gmail.GmailApiClient.GmailProfile;
import com.messaging.channel.gmail.GmailApiClient.TokenResponse;
import com.messaging.channel.gmail.GmailOAuthStateService.OAuthState;
import com.messaging.channel.gmail.GmailPersistenceService.StoredGmailToken;
import com.messaging.channel.gmail.dto.GmailAuthorizationResponse;
import com.messaging.channel.gmail.dto.GmailConnectionResponse;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.service.ChannelConnectionService;
import com.messaging.channel.service.ChannelEntitlementService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmailConnectionService {

  private final GmailOAuthProperties properties;
  private final GmailOAuthStateService stateService;
  private final GmailApiClient gmailApiClient;
  private final GmailPersistenceService persistenceService;
  private final ChannelConnectionRepository connectionRepository;
  private final ChannelEntitlementService entitlementService;
  private final CompanyAuthorizationService authorizationService;
  private final ProviderCredentialService credentialService;
  private final ChannelConnectionService channelConnectionService;
  private final ObjectMapper objectMapper;

  public GmailAuthorizationResponse authorization(Long companyId, Long userId) {
    requireConfigured();
    entitlementService.assertCanCreateConnection(companyId, ChannelType.EMAIL);
    String state = stateService.create(companyId, userId);
    return new GmailAuthorizationResponse(gmailApiClient.authorizationUrl(state));
  }

  public GmailConnectionResponse complete(String state, String code) {
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

    return persistenceService.persist(oauthState.companyId(), oauthState.userId(), profile, token);
  }

  public void consumeCancelledState(String state) {
    stateService.consume(state);
  }

  public ChannelConnectionResponse disconnect(Long companyId, Long connectionId) {
    ChannelConnectionResponse connection = channelConnectionService.get(companyId, connectionId);
    if (connection.providerType() != ProviderType.GMAIL) {
      throw new BadRequestException("Channel connection is not a Gmail account");
    }

    try {
      String raw = credentialService.read(connectionId, CredentialType.OAUTH_TOKEN_SET);
      StoredGmailToken stored = objectMapper.readValue(raw, StoredGmailToken.class);
      String token =
          stored.refreshToken() == null || stored.refreshToken().isBlank()
              ? stored.accessToken()
              : stored.refreshToken();
      gmailApiClient.revokeBestEffort(token);
    } catch (Exception ignored) {
      // Local disconnect must still succeed if the Google grant is already missing or invalid.
    }

    return channelConnectionService.disconnect(companyId, connectionId);
  }

  public boolean configured() {
    return properties.configured();
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Gmail is not configured. Configure Google OAuth before connecting Gmail");
    }
  }
}
