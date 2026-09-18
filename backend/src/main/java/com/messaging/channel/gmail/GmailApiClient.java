package com.messaging.channel.gmail;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.messaging.common.exception.BadRequestException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GmailApiClient {

  private final RestClient googleOAuth = RestClient.create("https://oauth2.googleapis.com");
  private final RestClient gmailApi = RestClient.create("https://gmail.googleapis.com");
  private final GmailOAuthProperties properties;

  public GmailApiClient(GmailOAuthProperties properties) {
    this.properties = properties;
  }

  public String authorizationUrl(String state) {
    requireConfigured();

    Map<String, String> params = new LinkedHashMap<>();
    params.put("client_id", properties.getClientId());
    params.put("redirect_uri", properties.getRedirectUri());
    params.put("response_type", "code");
    params.put("scope", properties.getScope());
    params.put("access_type", "offline");
    params.put("include_granted_scopes", "true");
    params.put("prompt", "consent");
    params.put("state", state);

    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth");
    params.forEach(builder::queryParam);
    return builder.build().encode().toUriString();
  }

  public TokenResponse exchangeCode(String code) {
    requireConfigured();

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("code", code);
    form.add("client_id", properties.getClientId());
    form.add("client_secret", properties.getClientSecret());
    form.add("redirect_uri", properties.getRedirectUri());
    form.add("grant_type", "authorization_code");

    return execute(
        () ->
            googleOAuth
                .post()
                .uri("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class));
  }

  public GmailProfile profile(String accessToken) {
    return execute(
        () ->
            gmailApi
                .get()
                .uri("/gmail/v1/users/me/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(GmailProfile.class));
  }

  public void revokeBestEffort(String token) {
    if (token == null || token.isBlank()) return;

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("token", token);

    try {
      googleOAuth
          .post()
          .uri("/revoke")
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException ignored) {
      // Local disconnect still proceeds when Google already revoked or expired the grant.
    }
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Gmail is not configured. Set the Google OAuth environment variables first");
    }
  }

  private String bearer(String accessToken) {
    return "Bearer " + accessToken;
  }

  private <T> T execute(GoogleCall<T> call) {
    try {
      T result = call.execute();
      if (result == null) {
        throw new BadRequestException("Google returned an empty response");
      }
      return result;
    } catch (RestClientResponseException exception) {
      throw new BadRequestException(
          "Google API request failed with HTTP " + exception.getStatusCode().value());
    }
  }

  @FunctionalInterface
  private interface GoogleCall<T> {
    T execute();
  }

  public record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("expires_in") Long expiresIn,
      @JsonProperty("token_type") String tokenType,
      String scope) {}

  public record GmailProfile(
      @JsonProperty("emailAddress") String emailAddress,
      @JsonProperty("messagesTotal") Long messagesTotal,
      @JsonProperty("threadsTotal") Long threadsTotal,
      @JsonProperty("historyId") String historyId) {}
}
