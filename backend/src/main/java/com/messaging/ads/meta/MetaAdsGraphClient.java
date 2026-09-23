package com.messaging.ads.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MetaAdsGraphClient {

  private final RestClient restClient = RestClient.create("https://graph.facebook.com");
  private final MetaAdsProperties properties;

  public MetaAdsGraphClient(MetaAdsProperties properties) {
    this.properties = properties;
  }

  public TokenResponse exchangeCode(String code) {
    requireConfigured();
    return execute(() -> restClient.get().uri(uriBuilder -> uriBuilder
        .path("/{version}/oauth/access_token")
        .queryParam("client_id", properties.getAppId())
        .queryParam("client_secret", properties.getAppSecret())
        .queryParam("redirect_uri", properties.getRedirectUri())
        .queryParam("code", code)
        .build(properties.getGraphApiVersion()))
        .retrieve().body(TokenResponse.class));
  }

  public TokenResponse exchangeLongLivedToken(String shortLivedToken) {
    requireConfigured();
    return execute(() -> restClient.get().uri(uriBuilder -> uriBuilder
        .path("/{version}/oauth/access_token")
        .queryParam("grant_type", "fb_exchange_token")
        .queryParam("client_id", properties.getAppId())
        .queryParam("client_secret", properties.getAppSecret())
        .queryParam("fb_exchange_token", shortLivedToken)
        .build(properties.getGraphApiVersion()))
        .retrieve().body(TokenResponse.class));
  }

  public UserProfile me(String token) {
    return execute(() -> restClient.get()
        .uri("/{version}/me?fields=id,name", properties.getGraphApiVersion())
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .retrieve().body(UserProfile.class));
  }

  public List<Business> businesses(String token) {
    BusinessList response = execute(() -> restClient.get()
        .uri("/{version}/me/businesses?fields=id,name", properties.getGraphApiVersion())
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .retrieve().body(BusinessList.class));
    return response == null || response.data() == null ? List.of() : response.data();
  }

  public List<AdAccount> ownedAdAccounts(String businessId, String token) {
    return accounts("/{version}/{businessId}/owned_ad_accounts?fields=id,account_id,name,currency,timezone_name,account_status", businessId, token);
  }

  public List<AdAccount> clientAdAccounts(String businessId, String token) {
    return accounts("/{version}/{businessId}/client_ad_accounts?fields=id,account_id,name,currency,timezone_name,account_status", businessId, token);
  }

  public List<AdAccount> personalAdAccounts(String token) {
    AccountList response = execute(() -> restClient.get()
        .uri("/{version}/me/adaccounts?fields=id,account_id,name,currency,timezone_name,account_status", properties.getGraphApiVersion())
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .retrieve().body(AccountList.class));
    return response == null || response.data() == null ? List.of() : response.data();
  }

  private List<AdAccount> accounts(String path, String businessId, String token) {
    AccountList response = execute(() -> restClient.get()
        .uri(path, properties.getGraphApiVersion(), businessId)
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .retrieve().body(AccountList.class));
    return response == null || response.data() == null ? List.of() : response.data();
  }

  private String bearer(String token) { return "Bearer " + token; }

  private void requireConfigured() {
    if (!properties.configured()) throw new BadRequestException("Meta Ads is not configured");
  }

  private <T> T execute(GraphCall<T> call) {
    try {
      T result = call.execute();
      if (result == null) throw new BadRequestException("Meta returned an empty response");
      return result;
    } catch (RestClientResponseException exception) {
      throw new BadRequestException("Meta Graph API request failed with HTTP " + exception.getStatusCode().value());
    }
  }

  @FunctionalInterface
  private interface GraphCall<T> { T execute(); }

  public record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("token_type") String tokenType, @JsonProperty("expires_in") Long expiresIn) {}
  public record UserProfile(String id, String name) {}
  public record BusinessList(List<Business> data) {}
  public record Business(String id, String name) {}
  public record AccountList(List<AdAccount> data) {}
  public record AdAccount(String id, @JsonProperty("account_id") String accountId, String name, String currency, @JsonProperty("timezone_name") String timezoneName, @JsonProperty("account_status") Integer accountStatus) {}
}
