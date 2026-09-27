package com.messaging.ads.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

  public AdAccountHealthSummary accountHealth(
      String accessToken, String accountId) {
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/{version}/{account}")
                            .queryParam("fields", accountHealthFields())
                            .queryParam("appsecret_proof", appSecretProof(accessToken))
                            .build(
                                properties.getGraphApiVersion(),
                                accountPath(accountId)))
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(AdAccountHealthSummary.class));
  }

  private String accountHealthFields() {
    return "id,account_id,name,account_status,disable_reason,opportunity_score,"
        + "opportunity_score_weight,capabilities,all_capabilities,user_tasks,"
        + "failed_delivery_checks,has_page_authorized_adaccount,"
        + "offsite_pixels_tos_accepted,is_direct_deals_enabled,"
        + "is_notifications_enabled,user_access_expire_time,"
        + "offsite_clo_signal_status";
  }

  public AdAccountBillingSummary accountBilling(
      String accessToken, String accountId) {
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/{version}/{account}")
                            .queryParam("fields", accountBillingFields())
                            .queryParam("appsecret_proof", appSecretProof(accessToken))
                            .build(
                                properties.getGraphApiVersion(),
                                accountPath(accountId)))
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(AdAccountBillingSummary.class));
  }

  public void updateAccountSpendCap(
      String accessToken, String accountId, long spendCapMinor) {
    SuccessResponse response =
        execute(
            () ->
                graphClient
                    .post()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/{version}/{account}")
                                .queryParam("appsecret_proof", appSecretProof(accessToken))
                                .build(
                                    properties.getGraphApiVersion(),
                                    accountPath(accountId)))
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("spend_cap", spendCapMinor))
                    .retrieve()
                    .body(SuccessResponse.class));
    if (!response.success()) {
      throw new BadRequestException("Meta did not accept the account spend cap update");
    }
  }

  private String accountBillingFields() {
    return "id,account_id,name,currency,account_status,amount_spent,balance,spend_cap,"
        + "funding_source,funding_source_details,expired_funding_source_details,"
        + "extended_credit_invoice_group,is_prepay_account,is_tax_id_required,"
        + "min_campaign_group_spend_cap,min_daily_budget,tax_id_status,tax_id_type,"
        + "disable_reason,business_country_code";
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

  public String createPixel(String accessToken, String accountId, String name) {
    IdResponse response =
        execute(
            () ->
                graphClient
                    .post()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/{version}/{account}/adspixels")
                                .queryParam("appsecret_proof", appSecretProof(accessToken))
                                .build(properties.getGraphApiVersion(), accountPath(accountId)))
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("name", name))
                    .retrieve()
                    .body(IdResponse.class));
    if (response.id() == null || response.id().isBlank()) {
      throw new BadRequestException("Meta created the pixel without returning an ID");
    }
    return response.id();
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


  public List<CustomAudienceSummary> customAudiences(String accessToken, String accountId) {
    List<CustomAudienceSummary> result = new ArrayList<>();
    String after = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      CustomAudiencePage response = customAudiencePage(accessToken, accountId, after);
      if (response.data() != null) result.addAll(response.data());
      after = nextCursor(response.paging());
      if (after == null) break;
    }
    return List.copyOf(result);
  }

  public CustomAudienceSummary customAudience(String accessToken, String audienceId) {
    return execute(
        () -> graphClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/{version}/{audienceId}")
                .queryParam("fields", customAudienceFields())
                .queryParam("appsecret_proof", appSecretProof(accessToken))
                .build(properties.getGraphApiVersion(), audienceId))
            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
            .retrieve().body(CustomAudienceSummary.class));
  }

  public String createCustomAudience(
      String accessToken, String accountId, Map<String, Object> payload) {
    IdResponse response = execute(
        () -> graphClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/{version}/{account}/customaudiences")
                .queryParam("appsecret_proof", appSecretProof(accessToken))
                .build(properties.getGraphApiVersion(), accountPath(accountId)))
            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve().body(IdResponse.class));
    if (response.id() == null || response.id().isBlank()) {
      throw new BadRequestException("Meta created the audience without returning an ID");
    }
    return response.id();
  }

  public JsonNode customAudienceUsers(
      String accessToken,
      String audienceId,
      boolean remove,
      Map<String, Object> payload) {
    return execute(
        () ->
            graphClient
                .method(remove ? HttpMethod.DELETE : HttpMethod.POST)
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/{version}/{audienceId}/users")
                            .queryParam("appsecret_proof", appSecretProof(accessToken))
                            .build(properties.getGraphApiVersion(), audienceId))
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class));
  }

  public List<CustomConversionSummary> customConversions(
      String accessToken, String accountId) {
    List<CustomConversionSummary> result = new ArrayList<>();
    String after = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      CustomConversionPage response = customConversionPage(accessToken, accountId, after);
      if (response.data() != null) result.addAll(response.data());
      after = nextCursor(response.paging());
      if (after == null) break;
    }
    return List.copyOf(result);
  }

  public CustomConversionSummary customConversion(
      String accessToken, String conversionId) {
    return execute(
        () -> graphClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/{version}/{conversionId}")
                .queryParam("fields", customConversionFields())
                .queryParam("appsecret_proof", appSecretProof(accessToken))
                .build(properties.getGraphApiVersion(), conversionId))
            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
            .retrieve().body(CustomConversionSummary.class));
  }

  public String createCustomConversion(
      String accessToken, String accountId, Map<String, Object> payload) {
    IdResponse response = execute(
        () -> graphClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/{version}/{account}/customconversions")
                .queryParam("appsecret_proof", appSecretProof(accessToken))
                .build(properties.getGraphApiVersion(), accountPath(accountId)))
            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve().body(IdResponse.class));
    if (response.id() == null || response.id().isBlank()) {
      throw new BadRequestException("Meta created the custom conversion without returning an ID");
    }
    return response.id();
  }

  private CustomAudiencePage customAudiencePage(
      String accessToken, String accountId, String after) {
    return execute(
        () -> graphClient.get()
            .uri(uriBuilder -> {
              var builder = uriBuilder.path("/{version}/{account}/customaudiences")
                  .queryParam("fields", customAudienceFields())
                  .queryParam("limit", PAGE_LIMIT)
                  .queryParam("appsecret_proof", appSecretProof(accessToken));
              if (after != null && !after.isBlank()) builder.queryParam("after", after);
              return builder.build(properties.getGraphApiVersion(), accountPath(accountId));
            })
            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
            .retrieve().body(CustomAudiencePage.class));
  }

  private CustomConversionPage customConversionPage(
      String accessToken, String accountId, String after) {
    return execute(
        () -> graphClient.get()
            .uri(uriBuilder -> {
              var builder = uriBuilder.path("/{version}/{account}/customconversions")
                  .queryParam("fields", customConversionFields())
                  .queryParam("limit", PAGE_LIMIT)
                  .queryParam("appsecret_proof", appSecretProof(accessToken));
              if (after != null && !after.isBlank()) builder.queryParam("after", after);
              return builder.build(properties.getGraphApiVersion(), accountPath(accountId));
            })
            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
            .retrieve().body(CustomConversionPage.class));
  }

  private String customAudienceFields() {
    return "id,name,account_id,subtype,description,retention_days,"
        + "approximate_count_lower_bound,approximate_count_upper_bound,delivery_status,"
        + "operation_status,pixel_id,rule,lookalike_spec,customer_file_source,"
        + "time_created,time_updated";
  }

  private String customConversionFields() {
    return "id,account_id,name,description,custom_event_type,event_source_type,pixel,rule,"
        + "data_sources,default_conversion_value,is_archived,is_unavailable,"
        + "first_fired_time,last_fired_time,creation_time";
  }
  public List<InsightSummary> insightBreakdown(
      String accessToken,
      String accountId,
      LocalDate from,
      LocalDate to,
      String breakdown,
      String actionBreakdown) {
    List<InsightSummary> result = new ArrayList<>();
    String after = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      InsightPage response =
          insightBreakdownPage(
              accessToken,
              accountId,
              from,
              to,
              breakdown,
              actionBreakdown,
              after);
      if (response.data() != null) result.addAll(response.data());
      after = nextCursor(response.paging());
      if (after == null) break;
    }
    return List.copyOf(result);
  }

  private InsightPage insightBreakdownPage(
      String accessToken,
      String accountId,
      LocalDate from,
      LocalDate to,
      String breakdown,
      String actionBreakdown,
      String after) {
    String timeRange =
        "{\"since\":\"" + from + "\",\"until\":\"" + to + "\"}";
    String fields =
        actionBreakdown != null && !actionBreakdown.isBlank()
            ? actionBreakdownFields()
            : insightFields();
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/{version}/{account}/insights")
                              .queryParam("fields", fields)
                              .queryParam("level", "account")
                              .queryParam("time_range", timeRange)
                              .queryParam("time_increment", "all_days")
                              .queryParam("limit", PAGE_LIMIT)
                              .queryParam("appsecret_proof", appSecretProof(accessToken));
                      if (breakdown != null && !breakdown.isBlank()) {
                        builder.queryParam("breakdowns", breakdown);
                      }
                      if (actionBreakdown != null && !actionBreakdown.isBlank()) {
                        builder.queryParam("action_breakdowns", actionBreakdown);
                      }
                      if (after != null && !after.isBlank()) {
                        builder.queryParam("after", after);
                      }
                      return builder.build(
                          properties.getGraphApiVersion(), accountPath(accountId));
                    })
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(InsightPage.class));
  }

  public List<InsightSummary> insights(
      String accessToken,
      String accountId,
      String level,
      LocalDate from,
      LocalDate to,
      String timeIncrement) {
    List<InsightSummary> result = new ArrayList<>();
    String after = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      InsightPage response =
          insightPage(accessToken, accountId, level, from, to, timeIncrement, after);
      if (response.data() != null) result.addAll(response.data());
      after = nextCursor(response.paging());
      if (after == null) break;
    }
    return List.copyOf(result);
  }

  private InsightPage insightPage(
      String accessToken,
      String accountId,
      String level,
      LocalDate from,
      LocalDate to,
      String timeIncrement,
      String after) {
    String timeRange =
        "{\"since\":\"" + from + "\",\"until\":\"" + to + "\"}";
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/{version}/{account}/insights")
                              .queryParam("fields", insightFields())
                              .queryParam("level", level)
                              .queryParam("time_range", timeRange)
                              .queryParam("time_increment", timeIncrement)
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
                .body(InsightPage.class));
  }

  private String actionBreakdownFields() {
    return "account_id,account_name,account_currency,date_start,date_stop,actions,action_values";
  }

  private String insightFields() {
    return "account_id,account_name,account_currency,campaign_id,campaign_name,"
        + "adset_id,adset_name,ad_id,ad_name,date_start,date_stop,spend,impressions,"
        + "reach,clicks,unique_clicks,inline_link_clicks,ctr,cpc,cpm,frequency,"
        + "actions,action_values,conversions,conversion_values,purchase_roas,"
        + "website_purchase_roas";
  }

  public JsonNode searchAdGeoLocations(
      String accessToken,
      String query,
      List<String> locationTypes,
      String countryCode,
      int limit) {
    String typesJson =
        locationTypes == null || locationTypes.isEmpty()
            ? null
            : locationTypes.stream()
                .map(value -> "\"" + value.replace("\"", "") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    int safeLimit = Math.max(1, Math.min(limit, 50));
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/{version}/search")
                              .queryParam("type", "adgeolocation")
                              .queryParam("limit", safeLimit)
                              .queryParam("appsecret_proof", appSecretProof(accessToken));
                      if (query != null && !query.isBlank()) {
                        builder.queryParam("q", query.trim());
                      }
                      if (typesJson != null) {
                        builder.queryParam("location_types", typesJson);
                      }
                      if (countryCode != null && !countryCode.isBlank()) {
                        builder.queryParam("country_code", countryCode.trim().toUpperCase());
                      }
                      return builder.build(properties.getGraphApiVersion());
                    })
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(JsonNode.class));
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
  public String uploadVideoFromUrl(String accessToken, String accountId, String fileUrl) {
    IdResponse response =
        execute(
            () ->
                graphClient
                    .post()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/{version}/{account}/advideos")
                                .queryParam("appsecret_proof", appSecretProof(accessToken))
                                .build(properties.getGraphApiVersion(), accountPath(accountId)))
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("file_url", fileUrl))
                    .retrieve()
                    .body(IdResponse.class));
    if (response.id() == null || response.id().isBlank()) {
      throw new BadRequestException("Meta uploaded the video without returning an ID");
    }
    return response.id();
  }
  public List<CreativeSummary> creatives(String accessToken, String accountId) {
    List<CreativeSummary> result = new ArrayList<>();
    String after = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      CreativePage response = creativePage(accessToken, accountId, after);
      if (response.data() != null) result.addAll(response.data());
      after = nextCursor(response.paging());
      if (after == null) break;
    }
    return List.copyOf(result);
  }

  public CreativeSummary creative(String accessToken, String creativeId) {
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/{version}/{creativeId}")
                            .queryParam("fields", creativeFields())
                            .queryParam("appsecret_proof", appSecretProof(accessToken))
                            .build(properties.getGraphApiVersion(), creativeId))
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(CreativeSummary.class));
  }

  public String createCreative(
      String accessToken, String accountId, Map<String, Object> payload) {
    IdResponse response =
        execute(
            () ->
                graphClient
                    .post()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/{version}/{account}/adcreatives")
                                .queryParam("appsecret_proof", appSecretProof(accessToken))
                                .build(properties.getGraphApiVersion(), accountPath(accountId)))
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(IdResponse.class));
    if (response.id() == null || response.id().isBlank()) {
      throw new BadRequestException("Meta created the creative without returning an ID");
    }
    return response.id();
  }

  public List<AdSummary> ads(String accessToken, String accountId, String adSetId) {
    List<AdSummary> result = new ArrayList<>();
    String after = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      AdPage response = adPage(accessToken, accountId, after);
      if (response.data() != null) {
        response.data().stream()
            .filter(item -> adSetId.equals(item.adSetId()))
            .forEach(result::add);
      }
      after = nextCursor(response.paging());
      if (after == null) break;
    }
    return List.copyOf(result);
  }

  public AdSummary ad(String accessToken, String adId) {
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/{version}/{adId}")
                            .queryParam("fields", adFields())
                            .queryParam("appsecret_proof", appSecretProof(accessToken))
                            .build(properties.getGraphApiVersion(), adId))
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(AdSummary.class));
  }

  public String createAd(
      String accessToken, String accountId, Map<String, Object> payload) {
    IdResponse response =
        execute(
            () ->
                graphClient
                    .post()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/{version}/{account}/ads")
                                .queryParam("appsecret_proof", appSecretProof(accessToken))
                                .build(properties.getGraphApiVersion(), accountPath(accountId)))
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(IdResponse.class));
    if (response.id() == null || response.id().isBlank()) {
      throw new BadRequestException("Meta created the ad without returning an ID");
    }
    return response.id();
  }

  public void updateAd(
      String accessToken, String adId, Map<String, Object> updates) {
    if (updates.isEmpty()) return;
    SuccessResponse response =
        execute(
            () ->
                graphClient
                    .post()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/{version}/{adId}")
                                .queryParam("appsecret_proof", appSecretProof(accessToken))
                                .build(properties.getGraphApiVersion(), adId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(updates)
                    .retrieve()
                    .body(SuccessResponse.class));
    if (!response.success()) {
      throw new BadRequestException("Meta did not accept the ad update");
    }
  }

  private CreativePage creativePage(String accessToken, String accountId, String after) {
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/{version}/{account}/adcreatives")
                              .queryParam("fields", creativeFields())
                              .queryParam("limit", PAGE_LIMIT)
                              .queryParam("appsecret_proof", appSecretProof(accessToken));
                      if (after != null && !after.isBlank()) builder.queryParam("after", after);
                      return builder.build(
                          properties.getGraphApiVersion(), accountPath(accountId));
                    })
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(CreativePage.class));
  }

  private AdPage adPage(String accessToken, String accountId, String after) {
    return execute(
        () ->
            graphClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/{version}/{account}/ads")
                              .queryParam("fields", adFields())
                              .queryParam("limit", PAGE_LIMIT)
                              .queryParam("appsecret_proof", appSecretProof(accessToken));
                      if (after != null && !after.isBlank()) builder.queryParam("after", after);
                      return builder.build(
                          properties.getGraphApiVersion(), accountPath(accountId));
                    })
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(AdPage.class));
  }

  private String creativeFields() {
    return "id,name,account_id,object_story_spec,thumbnail_url,body,title,call_to_action_type,"
        + "url_tags,image_hash,image_url,instagram_actor_id,status,degrees_of_freedom_spec";
  }

  private String adFields() {
    return "id,name,account_id,adset_id,campaign_id,creative{id,name},status,effective_status,tracking_specs,conversion_specs,created_time,updated_time";
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

  public record AdAccountHealthSummary(
      String id,
      @JsonProperty("account_id") String accountId,
      String name,
      @JsonProperty("account_status") Integer accountStatus,
      @JsonProperty("disable_reason") Integer disableReason,
      @JsonProperty("opportunity_score") java.math.BigDecimal opportunityScore,
      @JsonProperty("opportunity_score_weight") Integer opportunityScoreWeight,
      List<String> capabilities,
      @JsonProperty("all_capabilities") List<String> allCapabilities,
      @JsonProperty("user_tasks") List<String> userTasks,
      @JsonProperty("failed_delivery_checks") JsonNode failedDeliveryChecks,
      @JsonProperty("has_page_authorized_adaccount") Boolean pageAuthorized,
      @JsonProperty("offsite_pixels_tos_accepted") Boolean offsitePixelsTosAccepted,
      @JsonProperty("is_direct_deals_enabled") Boolean directDealsEnabled,
      @JsonProperty("is_notifications_enabled") Boolean notificationsEnabled,
      @JsonProperty("user_access_expire_time") String userAccessExpireTime,
      @JsonProperty("offsite_clo_signal_status") Integer offsiteCloSignalStatus) {}

  public record FundingSourceDetails(
      String id,
      Integer type,
      @JsonProperty("display_string") String displayString) {}

  public record AdAccountBillingSummary(
      String id,
      @JsonProperty("account_id") String accountId,
      String name,
      String currency,
      @JsonProperty("account_status") Integer accountStatus,
      @JsonProperty("amount_spent") String amountSpent,
      String balance,
      @JsonProperty("spend_cap") String spendCap,
      @JsonProperty("funding_source") String fundingSource,
      @JsonProperty("funding_source_details") FundingSourceDetails fundingSourceDetails,
      @JsonProperty("expired_funding_source_details") JsonNode expiredFundingSourceDetails,
      @JsonProperty("extended_credit_invoice_group") JsonNode extendedCreditInvoiceGroup,
      @JsonProperty("is_prepay_account") Boolean prepayAccount,
      @JsonProperty("is_tax_id_required") Boolean taxIdRequired,
      @JsonProperty("min_campaign_group_spend_cap") String minCampaignGroupSpendCap,
      @JsonProperty("min_daily_budget") Long minDailyBudget,
      @JsonProperty("tax_id_status") Integer taxIdStatus,
      @JsonProperty("tax_id_type") String taxIdType,
      @JsonProperty("disable_reason") Integer disableReason,
      @JsonProperty("business_country_code") String businessCountryCode) {}

  public record AdAccountSummary(
      String id,
      @JsonProperty("account_id") String accountId,
      String name,
      String currency,
      @JsonProperty("timezone_name") String timezoneName,
      @JsonProperty("account_status") Integer accountStatus,
      BusinessRef business) {}



  public record CreativeRef(String id, String name) {}

  public record CreativeSummary(
      String id,
      String name,
      @JsonProperty("account_id") String accountId,
      @JsonProperty("object_story_spec") JsonNode objectStorySpec,
      @JsonProperty("thumbnail_url") String thumbnailUrl,
      String body,
      String title,
      @JsonProperty("call_to_action_type") String callToActionType,
      @JsonProperty("url_tags") String urlTags,
      @JsonProperty("image_hash") String imageHash,
      @JsonProperty("image_url") String imageUrl,
      @JsonProperty("instagram_actor_id") String instagramActorId,
      String status,
      @JsonProperty("degrees_of_freedom_spec") JsonNode degreesOfFreedomSpec) {

    public CreativeSummary(
        String id,
        String name,
        String accountId,
        JsonNode objectStorySpec,
        String thumbnailUrl,
        String body,
        String title,
        String callToActionType,
        String urlTags,
        String imageHash,
        String imageUrl,
        String instagramActorId,
        String status) {
      this(
          id,
          name,
          accountId,
          objectStorySpec,
          thumbnailUrl,
          body,
          title,
          callToActionType,
          urlTags,
          imageHash,
          imageUrl,
          instagramActorId,
          status,
          null);
    }
  }

  public record CreativePage(List<CreativeSummary> data, Paging paging) {}

  public record AdSummary(
      String id,
      String name,
      @JsonProperty("account_id") String accountId,
      @JsonProperty("adset_id") String adSetId,
      @JsonProperty("campaign_id") String campaignId,
      CreativeRef creative,
      String status,
      @JsonProperty("effective_status") String effectiveStatus,
      @JsonProperty("tracking_specs") JsonNode trackingSpecs,
      @JsonProperty("conversion_specs") JsonNode conversionSpecs,
      @JsonProperty("created_time") String createdTime,
      @JsonProperty("updated_time") String updatedTime) {}

  public record AdPage(List<AdSummary> data, Paging paging) {}
  public record InsightSummary(
      @JsonProperty("account_id") String accountId,
      @JsonProperty("account_name") String accountName,
      @JsonProperty("account_currency") String accountCurrency,
      @JsonProperty("campaign_id") String campaignId,
      @JsonProperty("campaign_name") String campaignName,
      @JsonProperty("adset_id") String adSetId,
      @JsonProperty("adset_name") String adSetName,
      @JsonProperty("ad_id") String adId,
      @JsonProperty("ad_name") String adName,
      @JsonProperty("date_start") String dateStart,
      @JsonProperty("date_stop") String dateStop,
      String spend,
      String impressions,
      String reach,
      String clicks,
      @JsonProperty("unique_clicks") String uniqueClicks,
      @JsonProperty("inline_link_clicks") String inlineLinkClicks,
      String ctr,
      String cpc,
      String cpm,
      String frequency,
      JsonNode actions,
      @JsonProperty("action_values") JsonNode actionValues,
      JsonNode conversions,
      @JsonProperty("conversion_values") JsonNode conversionValues,
      @JsonProperty("purchase_roas") JsonNode purchaseRoas,
      @JsonProperty("website_purchase_roas") JsonNode websitePurchaseRoas,
      String age,
      String gender,
      String country,
      String region,
      @JsonProperty("publisher_platform") String publisherPlatform,
      @JsonProperty("platform_position") String platformPosition,
      @JsonProperty("impression_device") String impressionDevice,
      @JsonProperty("device_platform") String devicePlatform,
      @JsonProperty("conversion_destination") String conversionDestination) {}

  public record InsightPage(List<InsightSummary> data, Paging paging) {}

  public record CustomAudienceSummary(
      String id,
      String name,
      @JsonProperty("account_id") String accountId,
      String subtype,
      String description,
      @JsonProperty("retention_days") Integer retentionDays,
      @JsonProperty("approximate_count_lower_bound") Long approximateCountLowerBound,
      @JsonProperty("approximate_count_upper_bound") Long approximateCountUpperBound,
      @JsonProperty("delivery_status") JsonNode deliveryStatus,
      @JsonProperty("operation_status") JsonNode operationStatus,
      @JsonProperty("pixel_id") String pixelId,
      JsonNode rule,
      @JsonProperty("lookalike_spec") JsonNode lookalikeSpec,
      @JsonProperty("customer_file_source") String customerFileSource,
      @JsonProperty("time_created") String timeCreated,
      @JsonProperty("time_updated") String timeUpdated) {}

  public record CustomAudiencePage(List<CustomAudienceSummary> data, Paging paging) {}

  public record PixelRef(String id, String name) {}

  public record CustomConversionSummary(
      String id,
      @JsonProperty("account_id") String accountId,
      String name,
      String description,
      @JsonProperty("custom_event_type") String customEventType,
      @JsonProperty("event_source_type") String eventSourceType,
      PixelRef pixel,
      JsonNode rule,
      @JsonProperty("data_sources") JsonNode dataSources,
      @JsonProperty("default_conversion_value") java.math.BigDecimal defaultConversionValue,
      @JsonProperty("is_archived") Boolean archived,
      @JsonProperty("is_unavailable") Boolean unavailable,
      @JsonProperty("first_fired_time") String firstFiredTime,
      @JsonProperty("last_fired_time") String lastFiredTime,
      @JsonProperty("creation_time") String creationTime) {}

  public record CustomConversionPage(List<CustomConversionSummary> data, Paging paging) {}
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
