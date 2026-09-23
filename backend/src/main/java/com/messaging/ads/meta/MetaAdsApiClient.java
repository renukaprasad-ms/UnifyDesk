package com.messaging.ads.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.messaging.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class MetaAdsApiClient {

  private static final int PAGE_LIMIT = 100;
  private static final int MAX_PAGES = 100;

  private final RestClient graphClient = RestClient.create("https://graph.facebook.com");
  private final MetaAdsProperties properties;

  public MetaAdsApiClient(MetaAdsProperties properties) {
    this.properties = properties;
  }

  public String authorizationUrl(String state) {
    requireConfigured();
    return UriComponentsBuilder
        .fromUriString("https://www.facebook.com")
        .pathSegment(properties.getGraphApiVersion(), "dialog", "oauth")
        .queryParam("client_id", properties.getAppId())
        .queryParam("redirect_uri", properties.getRedirectUri())
        .queryParam("state", state)
        .queryParam("scope", properties.getScope())
        .queryParam("response_type", "code")
        .build()
        .encode()
        .toUriString();
  }

  public TokenResponse exchangeCode(String code) {
    requireConfigured();
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/{version}/oauth/access_token")
                            .queryParam("client_id", properties.getAppId())
                            .queryParam("client_secret", properties.getAppSecret())
                            .queryParam("redirect_uri", properties.getRedirectUri())
                            .queryParam("code", code)
                            .build(properties.getGraphApiVersion()))
                .retrieve()
                .body(TokenResponse.class));
  }

  public TokenResponse exchangeLongLivedToken(String shortLivedToken) {
    requireConfigured();
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/{version}/oauth/access_token")
                            .queryParam("grant_type", "fb_exchange_token")
                            .queryParam("client_id", properties.getAppId())
                            .queryParam("client_secret", properties.getAppSecret())
                            .queryParam("fb_exchange_token", shortLivedToken)
                            .build(properties.getGraphApiVersion()))
                .retrieve()
                .body(TokenResponse.class));
  }

  public UserProfile profile(String accessToken) {
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/{version}/me")
                            .queryParam("fields", "id,name")
                            .queryParam("appsecret_proof", appSecretProof(accessToken))
                            .build(properties.getGraphApiVersion()))
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(UserProfile.class));
  }

  public List<BusinessSummary> businesses(String accessToken) {
    List<BusinessSummary> result = new ArrayList<>();
    String after = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      BusinessPage response = businessPage(accessToken, after);
      if (response.data() != null) result.addAll(response.data());
      after = nextCursor(response.paging());
      if (after == null) break;
    }
    return List.copyOf(result);
  }

  public List<AdAccountSummary> adAccounts(String accessToken) {
    List<AdAccountSummary> result = new ArrayList<>();
    String after = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      AdAccountPage response = adAccountPage(accessToken, after);
      if (response.data() != null) result.addAll(response.data());
      after = nextCursor(response.paging());
      if (after == null) break;
    }
    return List.copyOf(result);
  }

  private BusinessPage businessPage(String accessToken, String after) {
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/{version}/me/businesses")
                              .queryParam("fields", "id,name")
                              .queryParam("limit", PAGE_LIMIT)
                              .queryParam("appsecret_proof", appSecretProof(accessToken));
                      if (after != null && !after.isBlank()) {
                        builder.queryParam("after", after);
                      }
                      return builder.build(properties.getGraphApiVersion());
                    })
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(BusinessPage.class));
  }

  private AdAccountPage adAccountPage(String accessToken, String after) {
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/{version}/me/adaccounts")
                              .queryParam(
                                  "fields",
                                  "id,account_id,name,currency,timezone_name,account_status,business{id,name}")
                              .queryParam("limit", PAGE_LIMIT)
                              .queryParam("appsecret_proof", appSecretProof(accessToken));
                      if (after != null && !after.isBlank()) {
                        builder.queryParam("after", after);
                      }
                      return builder.build(properties.getGraphApiVersion());
                    })
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(AdAccountPage.class));
  }

  private String nextCursor(Paging paging) {
    if (paging == null
        || paging.next() == null
        || paging.next().isBlank()
        || paging.cursors() == null) {
      return null;
    }
    String after = paging.cursors().after();
    return after == null || after.isBlank() ? null : after;
  }

  private String appSecretProof(String accessToken) {
    try {
      Mac hmac = Mac.getInstance("HmacSHA256");
      hmac.init(
          new SecretKeySpec(
              properties.getAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of()
          .formatHex(hmac.doFinal(accessToken.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to create Meta appsecret_proof", exception);
    }
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Meta Ads is not configured. Configure the Meta Marketing API app first");
    }
  }

  private <T> T execute(GraphCall<T> call) {
    try {
      T result = call.execute();
      if (result == null) {
        throw new BadRequestException("Meta returned an empty response");
      }
      return result;
    } catch (RestClientResponseException exception) {
      throw new BadRequestException(
          "Meta Graph API request failed with HTTP " + exception.getStatusCode().value());
    }
  }

  @FunctionalInterface
  private interface GraphCall<T> {
    T execute();
  }

  public record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") Long expiresIn) {}

  public record UserProfile(String id, String name) {}

  public record BusinessSummary(String id, String name) {}

  public record BusinessRef(String id, String name) {}

  public record AdAccountSummary(
      String id,
      @JsonProperty("account_id") String accountId,
      String name,
      String currency,
      @JsonProperty("timezone_name") String timezoneName,
      @JsonProperty("account_status") Integer accountStatus,
      BusinessRef business) {}

  public record BusinessPage(List<BusinessSummary> data, Paging paging) {}

  public record AdAccountPage(List<AdAccountSummary> data, Paging paging) {}

  public record Paging(Cursors cursors, String next) {}

  public record Cursors(String before, String after) {}
}
