package com.messaging.ads.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdCredentialType;
import com.messaging.ads.meta.MetaAdsApiClient.TokenResponse;
import com.messaging.ads.service.AdProviderCredentialService;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsTokenService {

  private final AdProviderCredentialService credentialService;
  private final MetaAdsProperties properties;
  private final ObjectMapper objectMapper;

  public void store(
      AdProviderConnection connection,
      TokenResponse token,
      String externalUserId) {
    Instant expiresAt =
        token.expiresIn() == null || token.expiresIn() <= 0
            ? null
            : Instant.now().plusSeconds(token.expiresIn());

    MetaAdsStoredToken storedToken =
        new MetaAdsStoredToken(
            token.accessToken(),
            token.tokenType(),
            properties.getGraphApiVersion(),
            externalUserId);

    credentialService.upsert(
        connection,
        AdCredentialType.OAUTH_TOKEN_SET,
        write(storedToken),
        expiresAt);
  }

  public TokenValue read(Long connectionId) {
    AdProviderCredentialService.CredentialValue value =
        credentialService.read(connectionId, AdCredentialType.OAUTH_TOKEN_SET);
    if (value.expiresAt() != null && !value.expiresAt().isAfter(Instant.now())) {
      throw new BadRequestException("Meta Ads authorization expired. Reconnect Meta Ads");
    }
    try {
      return new TokenValue(
          objectMapper.readValue(value.plaintextPayload(), MetaAdsStoredToken.class),
          value.expiresAt());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored Meta Ads credential is invalid", exception);
    }
  }

  private String write(MetaAdsStoredToken token) {
    try {
      return objectMapper.writeValueAsString(token);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Meta Ads credential", exception);
    }
  }

  public record TokenValue(MetaAdsStoredToken token, Instant expiresAt) {}
}
