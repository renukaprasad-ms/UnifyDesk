package com.messaging.ads.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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


  public List<PageSummary> promotePages(String accessToken, String accountId) {
    PromotePagesResponse response =
        execute(
            () ->
                graphClient
                    .get()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/{version}/{account}")
                                .queryParam("fields", "promote_pages")
                                .queryParam("limit", PAGE_LIMIT)
                                .queryParam("appsecret_proof", appSecretProof(accessToken))
                                .build(
                                    properties.getGraphApiVersion(),
                                    accountPath(accountId)))
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .retrieve()
                    .body(PromotePagesResponse.class));
    if (response.promotePages() == null || response.promotePages().data() == null) {
      return List.of();
    }
    return List.copyOf(response.promotePages().data());
  }

  public List<InstagramSummary> instagramAccounts(String accessToken, String accountId) {
    List<InstagramSummary> result = new ArrayList<>();
    String after = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      InstagramPage response = instagramPage(accessToken, accountId, after);
      if (response.data() != null) result.addAll(response.data());
      after = nextCursor(response.paging());
      if (after == null) break;
    }
    return List.copyOf(result);
  }

  public List<PixelSummary> pixels(String accessToken, String accountId) {
    List<PixelSummary> result = new ArrayList<>();
    String after = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      PixelPage response = pixelPage(accessToken, accountId, after);
      if (response.data() != null) result.addAll(response.data());
      after = nextCursor(response.paging());
      if (after == null) break;
    }
    return List.copyOf(result);
  }


  public List<CampaignSummary> campaigns(String accessToken, String accountId) {
    List<CampaignSummary> result = new ArrayList<>();
    String after = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      CampaignPage response = campaignPage(accessToken, accountId, after);
      if (response.data() != null) result.addAll(response.data());
      after = nextCursor(response.paging());
      if (after == null) break;
    }
    return List.copyOf(result);
  }

  public List<AdSetSummary> adSets(
      String accessToken, String accountId, String campaignId) {
    List<AdSetSummary> result = new ArrayList<>();
    String after = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      AdSetPage response = adSetPage(accessToken, accountId, after);
      if (response.data() != null) {
        response.data().stream()
            .filter(item -> campaignId.equals(item.campaignId()))
            .forEach(result::add);
      }
      after = nextCursor(response.paging());
      if (after == null) break;
    }
    return List.copyOf(result);
  }

  public AdSetSummary adSet(String accessToken, String adSetId) {
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/{version}/{adSetId}")
                            .queryParam("fields", adSetFields())
                            .queryParam("appsecret_proof", appSecretProof(accessToken))
                            .build(properties.getGraphApiVersion(), adSetId))
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(AdSetSummary.class));
  }

  public String createAdSet(
      String accessToken, String accountId, Map<String, Object> payload) {
    IdResponse response =
        execute(
            () ->
                graphClient
                    .post()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/{version}/{account}/adsets")
                                .queryParam("appsecret_proof", appSecretProof(accessToken))
                                .build(properties.getGraphApiVersion(), accountPath(accountId)))
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(IdResponse.class));
    if (response.id() == null || response.id().isBlank()) {
      throw new BadRequestException("Meta created the ad set without returning an ID");
    }
    return response.id();
  }

  public void updateAdSet(
      String accessToken, String adSetId, Map<String, Object> updates) {
    if (updates.isEmpty()) return;
    SuccessResponse response =
        execute(
            () ->
                graphClient
                    .post()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/{version}/{adSetId}")
                                .queryParam("appsecret_proof", appSecretProof(accessToken))
                                .build(properties.getGraphApiVersion(), adSetId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(updates)
                    .retrieve()
                    .body(SuccessResponse.class));
    if (!response.success()) {
      throw new BadRequestException("Meta did not accept the ad set update");
    }
  }

  private AdSetPage adSetPage(String accessToken, String accountId, String after) {
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/{version}/{account}/adsets")
                              .queryParam("fields", adSetFields())
                              .queryParam("limit", PAGE_LIMIT)
                              .queryParam("appsecret_proof", appSecretProof(accessToken));
                      if (after != null && !after.isBlank()) builder.queryParam("after", after);
                      return builder.build(
                          properties.getGraphApiVersion(), accountPath(accountId));
                    })
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(AdSetPage.class));
  }

  private String adSetFields() {
    return "id,name,account_id,campaign_id,optimization_goal,billing_event,bid_strategy,"
        + "destination_type,bid_amount,daily_budget,lifetime_budget,start_time,end_time,"
        + "targeting,promoted_object,attribution_spec,status,effective_status,created_time,updated_time";
  }
  public CampaignSummary campaign(String accessToken, String campaignId) {
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/{version}/{campaignId}")
                            .queryParam(
                                "fields",
                                "id,name,objective,account_id,buying_type,daily_budget,lifetime_budget,"
                                    + "spend_cap,bid_strategy,status,effective_status,start_time,stop_time,"
                                    + "created_time,updated_time,special_ad_categories,"
                                    + "special_ad_category_country")
                            .queryParam("appsecret_proof", appSecretProof(accessToken))
                            .build(properties.getGraphApiVersion(), campaignId))
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(CampaignSummary.class));
  }

  public String createCampaign(
      String accessToken,
      String accountId,
      String name,
      String objective,
      String buyingType,
      String bidStrategy,
      Long dailyBudgetMinor,
      Long lifetimeBudgetMinor,
      List<String> specialAdCategories) {
    Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("name", name);
    payload.put("objective", objective);
    payload.put("status", "PAUSED");
    payload.put("buying_type", buyingType);
    payload.put("special_ad_categories", specialAdCategories);
    if (bidStrategy != null && !bidStrategy.isBlank()) payload.put("bid_strategy", bidStrategy);
    if (dailyBudgetMinor != null) payload.put("daily_budget", dailyBudgetMinor);
    if (lifetimeBudgetMinor != null) payload.put("lifetime_budget", lifetimeBudgetMinor);

    IdResponse response =
        execute(
            () ->
                graphClient
                    .post()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/{version}/{account}/campaigns")
                                .queryParam("appsecret_proof", appSecretProof(accessToken))
                                .build(properties.getGraphApiVersion(), accountPath(accountId)))
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(IdResponse.class));
    if (response.id() == null || response.id().isBlank()) {
      throw new BadRequestException("Meta created the campaign without returning an ID");
    }
    return response.id();
  }

  public void updateCampaign(
      String accessToken, String campaignId, Map<String, Object> updates) {
    if (updates.isEmpty()) return;
    SuccessResponse response =
        execute(
            () ->
                graphClient
                    .post()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/{version}/{campaignId}")
                                .queryParam("appsecret_proof", appSecretProof(accessToken))
                                .build(properties.getGraphApiVersion(), campaignId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(updates)
                    .retrieve()
                    .body(SuccessResponse.class));
    if (!response.success()) {
      throw new BadRequestException("Meta did not accept the campaign update");
    }
  }
  private CampaignPage campaignPage(String accessToken, String accountId, String after) {
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/{version}/{account}/campaigns")
                              .queryParam(
                                  "fields",
                                  "id,name,objective,account_id,buying_type,daily_budget,lifetime_budget,"
                                      + "spend_cap,bid_strategy,status,effective_status,start_time,stop_time,"
                                      + "created_time,updated_time,special_ad_categories,"
                                      + "special_ad_category_country")
                              .queryParam("limit", PAGE_LIMIT)
                              .queryParam("appsecret_proof", appSecretProof(accessToken));
                      if (after != null && !after.isBlank()) {
                        builder.queryParam("after", after);
                      }
                      return builder.build(
                          properties.getGraphApiVersion(), accountPath(accountId));
                    })
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(CampaignPage.class));
  }

  private InstagramPage instagramPage(String accessToken, String accountId, String after) {
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/{version}/{account}/instagram_accounts")
                              .queryParam("fields", "id,username,profile_pic")
                              .queryParam("limit", PAGE_LIMIT)
                              .queryParam("appsecret_proof", appSecretProof(accessToken));
                      if (after != null && !after.isBlank()) {
                        builder.queryParam("after", after);
                      }
                      return builder.build(
                          properties.getGraphApiVersion(), accountPath(accountId));
                    })
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(InstagramPage.class));
  }

  private PixelPage pixelPage(String accessToken, String accountId, String after) {
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/{version}/{account}/adspixels")
                              .queryParam("fields", "id,name,last_fired_time")
                              .queryParam("limit", PAGE_LIMIT)
                              .queryParam("appsecret_proof", appSecretProof(accessToken));
                      if (after != null && !after.isBlank()) {
                        builder.queryParam("after", after);
                      }
                      return builder.build(
                          properties.getGraphApiVersion(), accountPath(accountId));
                    })
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(PixelPage.class));
  }

  private String accountPath(String accountId) {
    if (accountId == null || accountId.isBlank()) {
      throw new BadRequestException("Meta ad account ID is missing");
    }
    String normalized = accountId.trim();
    return normalized.startsWith("act_") ? normalized : "act_" + normalized;
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

  public record IdResponse(String id) {}

  public record SuccessResponse(boolean success) {}

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



  public record AdSetSummary(
      String id,
      String name,
      @JsonProperty("account_id") String accountId,
      @JsonProperty("campaign_id") String campaignId,
      @JsonProperty("optimization_goal") String optimizationGoal,
      @JsonProperty("billing_event") String billingEvent,
      @JsonProperty("bid_strategy") String bidStrategy,
      @JsonProperty("destination_type") String destinationType,
      @JsonProperty("bid_amount") String bidAmount,
      @JsonProperty("daily_budget") String dailyBudget,
      @JsonProperty("lifetime_budget") String lifetimeBudget,
      @JsonProperty("start_time") String startTime,
      @JsonProperty("end_time") String endTime,
      JsonNode targeting,
      @JsonProperty("promoted_object") JsonNode promotedObject,
      @JsonProperty("attribution_spec") JsonNode attributionSpec,
      String status,
      @JsonProperty("effective_status") String effectiveStatus,
      @JsonProperty("created_time") String createdTime,
      @JsonProperty("updated_time") String updatedTime) {}

  public record AdSetPage(List<AdSetSummary> data, Paging paging) {}
  public record CampaignSummary(
      String id,
      String name,
      String objective,
      @JsonProperty("account_id") String accountId,
      @JsonProperty("buying_type") String buyingType,
      @JsonProperty("daily_budget") String dailyBudget,
      @JsonProperty("lifetime_budget") String lifetimeBudget,
      @JsonProperty("spend_cap") String spendCap,
      @JsonProperty("bid_strategy") String bidStrategy,
      String status,
      @JsonProperty("effective_status") String effectiveStatus,
      @JsonProperty("start_time") String startTime,
      @JsonProperty("stop_time") String stopTime,
      @JsonProperty("created_time") String createdTime,
      @JsonProperty("updated_time") String updatedTime,
      @JsonProperty("special_ad_categories") List<String> specialAdCategories,
      @JsonProperty("special_ad_category_country") List<String> specialAdCategoryCountry) {}

  public record CampaignPage(List<CampaignSummary> data, Paging paging) {}

  public record PageSummary(String id, String name) {}

  public record InstagramSummary(
      String id,
      String username,
      @JsonProperty("profile_pic") String profilePic) {}

  public record PixelSummary(
      String id,
      String name,
      @JsonProperty("last_fired_time") String lastFiredTime) {}

  public record PageConnection(List<PageSummary> data, Paging paging) {}

  public record PromotePagesResponse(
      @JsonProperty("promote_pages") PageConnection promotePages) {}

  public record InstagramPage(List<InstagramSummary> data, Paging paging) {}

  public record PixelPage(List<PixelSummary> data, Paging paging) {}

  public record BusinessPage(List<BusinessSummary> data, Paging paging) {}

  public record AdAccountPage(List<AdAccountSummary> data, Paging paging) {}

  public record Paging(Cursors cursors, String next) {}

  public record Cursors(String before, String after) {}
}
