package com.messaging.channel.zoho;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.credential.ProviderCredentialService;
import com.messaging.channel.credential.ProviderCredentialService.CredentialValue;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.CredentialType;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ZohoMailTokenService {

  private final ProviderCredentialService credentialService;
  private final ZohoMailApiClient apiClient;
  private final ZohoMailOAuthProperties properties;
  private final ObjectMapper objectMapper;

  public ZohoMailStoredToken stored(ChannelConnection connection) {
    CredentialValue credential =
        credentialService.readWithMetadata(connection.getId(), CredentialType.OAUTH_TOKEN_SET);
    return read(credential.plaintextPayload());
  }

  public ZohoMailStoredToken session(ChannelConnection connection) {
    CredentialValue credential =
        credentialService.readWithMetadata(connection.getId(), CredentialType.OAUTH_TOKEN_SET);
    ZohoMailStoredToken stored = read(credential.plaintextPayload());

    Instant expiresAt = credential.expiresAt();
    if (hasText(stored.accessToken())
        && expiresAt != null
        && expiresAt.isAfter(Instant.now().plus(properties.getTokenRefreshSkew()))) {
      return stored;
    }

    if (!hasText(stored.refreshToken())) {
      throw new BadRequestException(
          "Zoho Mail refresh token is missing. Disconnect and reconnect the mailbox");
    }

    ZohoMailApiClient.TokenResponse refreshed =
        apiClient.refreshAccessToken(stored.accountsServer(), stored.refreshToken());
    if (!hasText(refreshed.accessToken())) {
      throw new BadRequestException("Zoho did not return an access token");
    }

    ZohoMailStoredToken updated =
        new ZohoMailStoredToken(
            refreshed.accessToken(),
            hasText(refreshed.refreshToken()) ? refreshed.refreshToken() : stored.refreshToken(),
            hasText(refreshed.tokenType()) ? refreshed.tokenType() : stored.tokenType(),
            hasText(refreshed.scope()) ? refreshed.scope() : stored.scope(),
            stored.accountsServer(),
            stored.mailBaseUrl(),
            stored.accountId(),
            stored.emailAddress(),
            stored.displayName(),
            stored.inboxFolderId(),
            stored.sentFolderId());

    Instant newExpiresAt =
        refreshed.expiresIn() == null || refreshed.expiresIn() <= 0
            ? null
            : Instant.now().plusSeconds(refreshed.expiresIn());

    credentialService.upsert(
        connection, CredentialType.OAUTH_TOKEN_SET, write(updated), newExpiresAt);
    return updated;
  }

  public String serialize(ZohoMailStoredToken token) {
    return write(token);
  }

  private ZohoMailStoredToken read(String json) {
    try {
      return objectMapper.readValue(json, ZohoMailStoredToken.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to read Zoho Mail credential", exception);
    }
  }

  private String write(ZohoMailStoredToken token) {
    try {
      return objectMapper.writeValueAsString(token);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Zoho Mail credential", exception);
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
