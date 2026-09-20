package com.messaging.channel.microsoft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.credential.ProviderCredentialService;
import com.messaging.channel.credential.ProviderCredentialService.CredentialValue;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.CredentialType;
import com.messaging.channel.microsoft.MicrosoftApiClient.TokenResponse;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MicrosoftTokenService {

  private final ProviderCredentialService credentialService;
  private final MicrosoftApiClient apiClient;
  private final MicrosoftOAuthProperties properties;
  private final ObjectMapper objectMapper;

  public String accessToken(ChannelConnection connection) {
    CredentialValue credential =
        credentialService.readWithMetadata(connection.getId(), CredentialType.OAUTH_TOKEN_SET);
    MicrosoftStoredToken stored = read(credential.plaintextPayload());

    Instant expiresAt = credential.expiresAt();
    if (hasText(stored.accessToken())
        && expiresAt != null
        && expiresAt.isAfter(Instant.now().plus(properties.getTokenRefreshSkew()))) {
      return stored.accessToken();
    }

    if (!hasText(stored.refreshToken())) {
      throw new BadRequestException(
          "Microsoft refresh token is missing. Disconnect and reconnect the account");
    }

    TokenResponse refreshed = apiClient.refreshAccessToken(stored.refreshToken());
    if (!hasText(refreshed.accessToken())) {
      throw new BadRequestException("Microsoft did not return an access token");
    }

    String refreshToken =
        hasText(refreshed.refreshToken()) ? refreshed.refreshToken() : stored.refreshToken();
    MicrosoftStoredToken updated =
        new MicrosoftStoredToken(
            refreshed.accessToken(),
            refreshToken,
            hasText(refreshed.tokenType()) ? refreshed.tokenType() : stored.tokenType(),
            hasText(refreshed.scope()) ? refreshed.scope() : stored.scope(),
            stored.userId(),
            stored.emailAddress());

    Instant newExpiresAt =
        refreshed.expiresIn() == null || refreshed.expiresIn() <= 0
            ? null
            : Instant.now().plusSeconds(refreshed.expiresIn());

    credentialService.upsert(
        connection, CredentialType.OAUTH_TOKEN_SET, write(updated), newExpiresAt);
    return updated.accessToken();
  }

  private MicrosoftStoredToken read(String json) {
    try {
      return objectMapper.readValue(json, MicrosoftStoredToken.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to read Microsoft credential", exception);
    }
  }

  private String write(MicrosoftStoredToken token) {
    try {
      return objectMapper.writeValueAsString(token);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Microsoft credential", exception);
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
