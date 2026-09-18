package com.messaging.channel.gmail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.credential.ProviderCredentialService;
import com.messaging.channel.credential.ProviderCredentialService.CredentialValue;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.CredentialType;
import com.messaging.channel.gmail.GmailApiClient.TokenResponse;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmailTokenService {

  private final ProviderCredentialService credentialService;
  private final GmailApiClient gmailApiClient;
  private final GmailOAuthProperties properties;
  private final ObjectMapper objectMapper;

  public String accessToken(ChannelConnection connection) {
    CredentialValue credential =
        credentialService.readWithMetadata(connection.getId(), CredentialType.OAUTH_TOKEN_SET);
    GmailStoredToken stored = read(credential.plaintextPayload());

    Instant expiresAt = credential.expiresAt();
    if (hasText(stored.accessToken())
        && expiresAt != null
        && expiresAt.isAfter(Instant.now().plus(properties.getTokenRefreshSkew()))) {
      return stored.accessToken();
    }

    if (!hasText(stored.refreshToken())) {
      throw new BadRequestException(
          "Gmail refresh token is missing. Disconnect and reconnect the Gmail account");
    }

    TokenResponse refreshed = gmailApiClient.refreshAccessToken(stored.refreshToken());
    if (!hasText(refreshed.accessToken())) {
      throw new BadRequestException("Google did not return an access token while refreshing Gmail");
    }

    String refreshToken =
        hasText(refreshed.refreshToken()) ? refreshed.refreshToken() : stored.refreshToken();
    String tokenType = hasText(refreshed.tokenType()) ? refreshed.tokenType() : stored.tokenType();
    String scope = hasText(refreshed.scope()) ? refreshed.scope() : stored.scope();

    Instant newExpiresAt =
        refreshed.expiresIn() == null || refreshed.expiresIn() <= 0
            ? null
            : Instant.now().plusSeconds(refreshed.expiresIn());

    GmailStoredToken updated =
        new GmailStoredToken(
            refreshed.accessToken(), refreshToken, tokenType, scope, stored.emailAddress());

    credentialService.upsert(
        connection, CredentialType.OAUTH_TOKEN_SET, write(updated), newExpiresAt);

    return updated.accessToken();
  }

  private GmailStoredToken read(String json) {
    try {
      return objectMapper.readValue(json, GmailStoredToken.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to read Gmail credential", exception);
    }
  }

  private String write(GmailStoredToken token) {
    try {
      return objectMapper.writeValueAsString(token);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Gmail credential", exception);
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
