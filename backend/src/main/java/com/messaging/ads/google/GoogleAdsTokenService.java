package com.messaging.ads.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdCredentialType;
import com.messaging.ads.google.GoogleAdsApiClient.TokenResponse;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.ads.service.AdProviderCredentialService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleAdsTokenService {

  private final AdProviderCredentialService credentialService;
  private final AdProviderConnectionRepository connectionRepository;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsProperties properties;
  private final ObjectMapper objectMapper;

  @Transactional
  public void store(AdProviderConnection connection, TokenResponse token) {
    String refreshToken = token.refreshToken();
    if (refreshToken == null || refreshToken.isBlank()) {
      refreshToken = existingRefreshToken(connection.getId());
    }

    if (refreshToken == null || refreshToken.isBlank()) {
      throw new BadRequestException(
          "Google did not return a refresh token. Revoke the existing consent and connect again");
    }

    Instant expiresAt = expiry(token.expiresIn());
    GoogleAdsStoredToken stored =
        new GoogleAdsStoredToken(
            token.accessToken(),
            refreshToken,
            token.tokenType(),
            token.scope(),
            properties.getApiVersion());

    credentialService.upsert(
        connection,
        AdCredentialType.OAUTH_TOKEN_SET,
        write(stored),
        expiresAt);
  }

  @Transactional
  public String accessToken(Long connectionId) {
    AdProviderCredentialService.CredentialValue credential =
        credentialService.read(connectionId, AdCredentialType.OAUTH_TOKEN_SET);
    GoogleAdsStoredToken stored = read(credential.plaintextPayload());

    Instant refreshAt =
        credential.expiresAt() == null
            ? Instant.EPOCH
            : credential.expiresAt().minus(properties.getTokenRefreshSkew());

    if (stored.accessToken() != null
        && !stored.accessToken().isBlank()
        && refreshAt.isAfter(Instant.now())) {
      return stored.accessToken();
    }

    if (stored.refreshToken() == null || stored.refreshToken().isBlank()) {
      throw new BadRequestException("Google Ads authorization expired. Reconnect Google Ads");
    }

    TokenResponse refreshed = apiClient.refreshAccessToken(stored.refreshToken());
    if (refreshed.accessToken() == null || refreshed.accessToken().isBlank()) {
      throw new BadRequestException("Google did not return a refreshed access token");
    }

    AdProviderConnection connection =
        connectionRepository
            .findById(connectionId)
            .orElseThrow(() -> new NotFoundException("Google Ads connection not found"));

    GoogleAdsStoredToken updated =
        new GoogleAdsStoredToken(
            refreshed.accessToken(),
            stored.refreshToken(),
            first(refreshed.tokenType(), stored.tokenType()),
            first(refreshed.scope(), stored.scope()),
            properties.getApiVersion());

    credentialService.upsert(
        connection,
        AdCredentialType.OAUTH_TOKEN_SET,
        write(updated),
        expiry(refreshed.expiresIn()));

    return updated.accessToken();
  }

  private String existingRefreshToken(Long connectionId) {
    if (connectionId == null) return null;
    try {
      var value = credentialService.read(connectionId, AdCredentialType.OAUTH_TOKEN_SET);
      return read(value.plaintextPayload()).refreshToken();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private Instant expiry(Long expiresIn) {
    if (expiresIn == null || expiresIn <= 0) return null;
    return Instant.now().plusSeconds(expiresIn);
  }

  private String first(String preferred, String fallback) {
    return preferred == null || preferred.isBlank() ? fallback : preferred;
  }

  private GoogleAdsStoredToken read(String payload) {
    try {
      return objectMapper.readValue(payload, GoogleAdsStoredToken.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored Google Ads credential is invalid", exception);
    }
  }

  private String write(GoogleAdsStoredToken token) {
    try {
      return objectMapper.writeValueAsString(token);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Google Ads credential", exception);
    }
  }

  public record GoogleAdsStoredToken(
      String accessToken,
      String refreshToken,
      String tokenType,
      String scope,
      String apiVersion) {}
}
