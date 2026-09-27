package com.messaging.ads.google;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class GoogleAdsApiClient {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private final RestClient oauthClient = RestClient.create("https://oauth2.googleapis.com");
  private final RestClient identityClient =
      RestClient.create("https://openidconnect.googleapis.com");
  private final RestClient adsClient = RestClient.create("https://googleads.googleapis.com");
  private final GoogleAdsProperties properties;

  public GoogleAdsApiClient(GoogleAdsProperties properties) {
    this.properties = properties;
  }

  public String authorizationUrl(String state) {
    requireConfigured();
    return UriComponentsBuilder
        .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
        .queryParam("client_id", properties.getClientId().trim())
        .queryParam("redirect_uri", properties.getRedirectUri().trim())
        .queryParam("response_type", "code")
        .queryParam("scope", properties.getScope().trim())
        .queryParam("access_type", "offline")
        .queryParam("include_granted_scopes", "true")
        .queryParam("prompt", "consent")
        .queryParam("state", state)
        .build()
        .encode()
        .toUriString();
  }

  public TokenResponse exchangeCode(String code) {
    requireConfigured();
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", properties.getClientId().trim());
    form.add("client_secret", properties.getClientSecret().trim());
    form.add("redirect_uri", properties.getRedirectUri().trim());
    form.add("grant_type", "authorization_code");
    form.add("code", code);

    return execute(
        () ->
            oauthClient
                .post()
                .uri("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class),
        "Google OAuth token exchange failed");
  }

  public TokenResponse refreshAccessToken(String refreshToken) {
    requireConfigured();
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", properties.getClientId().trim());
    form.add("client_secret", properties.getClientSecret().trim());
    form.add("grant_type", "refresh_token");
    form.add("refresh_token", refreshToken);

    return execute(
        () ->
            oauthClient
                .post()
                .uri("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class),
        "Google OAuth token refresh failed");
  }

  public UserProfile profile(String accessToken) {
    return execute(
        () ->
            identityClient
                .get()
                .uri("/v1/userinfo")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(UserProfile.class),
        "Unable to read the Google account profile");
  }

  public List<GoogleAdsAccountSummary> discoverAccounts(String accessToken) {
    List<String> directlyAccessible = listAccessibleCustomerIds(accessToken);
    Map<String, GoogleAdsAccountSummary> discovered = new LinkedHashMap<>();

    for (String customerId : directlyAccessible) {
      GoogleAdsAccountSummary account = customer(accessToken, customerId, null);
      if (account == null) continue;

      // Direct access is preferred over a manager path for the same customer.
      discovered.put(customerId, account);

      if (account.manager()) {
        for (GoogleAdsAccountSummary child :
            customerClients(accessToken, customerId, customerId)) {
          discovered.putIfAbsent(child.customerId(), child);
        }
      }
    }

    return List.copyOf(discovered.values());
  }

  public List<String> listAccessibleCustomerIds(String accessToken) {
    JsonNode response =
        execute(
            () -> {
              RestClient.RequestHeadersSpec<?> request =
                  adsClient
                      .get()
                      .uri(
                          "/{version}/customers:listAccessibleCustomers",
                          properties.getApiVersion().trim());
              addAdsHeaders(request, accessToken, null);
              return parseJson(request.retrieve().body(String.class));
            },
            "Unable to list accessible Google Ads customers");

    if (response == null || !response.has("resourceNames")) return List.of();

    List<String> result = new ArrayList<>();
    for (JsonNode resourceName : response.path("resourceNames")) {
      String customerId = normalizeCustomerId(resourceName.asText());
      if (!customerId.isBlank()) result.add(customerId);
    }
    return List.copyOf(result);
  }

  private GoogleAdsAccountSummary customer(
      String accessToken, String customerId, String loginCustomerId) {
    String query =
        "SELECT customer.id, customer.descriptive_name, customer.currency_code, "
            + "customer.time_zone, customer.manager, customer.status, customer.test_account "
            + "FROM customer LIMIT 1";

    JsonNode response = searchStream(accessToken, customerId, loginCustomerId, query);
    for (JsonNode batch : iterable(response)) {
      for (JsonNode result : iterable(batch.path("results"))) {
        JsonNode customer = result.path("customer");
        if (customer.isMissingNode() || customer.isNull()) continue;
        return new GoogleAdsAccountSummary(
            customer.path("id").asText(customerId),
            nullableText(customer, "descriptiveName"),
            nullableText(customer, "currencyCode"),
            nullableText(customer, "timeZone"),
            customer.path("manager").asBoolean(false),
            customer.path("testAccount").asBoolean(false),
            nullableText(customer, "status"),
            loginCustomerId,
            0);
      }
    }
    return null;
  }

  private List<GoogleAdsAccountSummary> customerClients(
      String accessToken, String managerCustomerId, String loginCustomerId) {
    String query =
        "SELECT customer_client.client_customer, customer_client.descriptive_name, "
            + "customer_client.currency_code, customer_client.time_zone, "
            + "customer_client.manager, customer_client.level, customer_client.status, "
            + "customer_client.test_account, customer_client.hidden "
            + "FROM customer_client WHERE customer_client.level > 0";

    JsonNode response =
        searchStream(accessToken, managerCustomerId, loginCustomerId, query);
    List<GoogleAdsAccountSummary> result = new ArrayList<>();

    for (JsonNode batch : iterable(response)) {
      for (JsonNode row : iterable(batch.path("results"))) {
        JsonNode client = row.path("customerClient");
        if (client.isMissingNode() || client.isNull() || client.path("hidden").asBoolean(false)) {
          continue;
        }

        String customerId = normalizeCustomerId(client.path("clientCustomer").asText());
        if (customerId.isBlank()) continue;

        result.add(
            new GoogleAdsAccountSummary(
                customerId,
                nullableText(client, "descriptiveName"),
                nullableText(client, "currencyCode"),
                nullableText(client, "timeZone"),
                client.path("manager").asBoolean(false),
                client.path("testAccount").asBoolean(false),
                nullableText(client, "status"),
                loginCustomerId,
                client.path("level").asInt(1)));
      }
    }

    return List.copyOf(result);
  }

  public List<JsonNode> searchRows(
      String accessToken,
      String customerId,
      String loginCustomerId,
      String query) {
    return searchRows(accessToken, customerId, loginCustomerId, query, false);
  }

  public List<JsonNode> searchRows(
      String accessToken,
      String customerId,
      String loginCustomerId,
      String query,
      boolean includeDrafts) {
    JsonNode response =
        searchStream(accessToken, customerId, loginCustomerId, query, includeDrafts);
    List<JsonNode> rows = new ArrayList<>();
    for (JsonNode batch : iterable(response)) {
      for (JsonNode row : iterable(batch.path("results"))) {
        rows.add(row);
      }
    }
    return List.copyOf(rows);
  }

  public JsonNode mutate(
      String accessToken,
      String customerId,
      String loginCustomerId,
      String resourceCollection,
      List<Map<String, Object>> operations) {
    if (operations == null || operations.isEmpty()) {
      throw new BadRequestException("Google Ads mutation requires at least one operation");
    }
    return execute(
        () -> {
          RestClient.RequestBodySpec request =
              adsClient
                  .post()
                  .uri(
                      "/{version}/customers/{customerId}/{resourceCollection}:mutate",
                      properties.getApiVersion().trim(),
                      normalizeCustomerId(customerId),
                      resourceCollection)
                  .contentType(MediaType.APPLICATION_JSON);
          addAdsHeaders(request, accessToken, loginCustomerId);
          return parseJson(
              request
                  .body(Map.of("operations", operations, "partialFailure", false))
                  .retrieve()
                  .body(String.class));
        },
        "Google Ads mutation failed for " + resourceCollection);
  }

  public JsonNode mutateReturningMutableResource(
      String accessToken,
      String customerId,
      String loginCustomerId,
      String resourceCollection,
      List<Map<String, Object>> operations) {
    if (operations == null || operations.isEmpty()) {
      throw new BadRequestException("Google Ads mutation requires at least one operation");
    }
    return execute(
        () -> {
          RestClient.RequestBodySpec request =
              adsClient
                  .post()
                  .uri(
                      "/{version}/customers/{customerId}/{resourceCollection}:mutate",
                      properties.getApiVersion().trim(),
                      normalizeCustomerId(customerId),
                      resourceCollection)
                  .contentType(MediaType.APPLICATION_JSON);
          addAdsHeaders(request, accessToken, loginCustomerId);
          Map<String, Object> body = new LinkedHashMap<>();
          body.put("operations", operations);
          body.put("responseContentType", "MUTABLE_RESOURCE");
          return parseJson(request.body(body).retrieve().body(String.class));
        },
        "Google Ads mutation failed for " + resourceCollection);
  }

  public JsonNode mutateCustomer(
      String accessToken,
      String customerId,
      String loginCustomerId,
      Map<String, Object> operation) {
    if (operation == null || operation.isEmpty()) {
      throw new BadRequestException("Google Ads customer mutation requires an operation");
    }
    return execute(
        () -> {
          RestClient.RequestBodySpec request =
              adsClient
                  .post()
                  .uri(
                      "/{version}/customers/{customerId}:mutate",
                      properties.getApiVersion().trim(),
                      normalizeCustomerId(customerId))
                  .contentType(MediaType.APPLICATION_JSON);
          addAdsHeaders(request, accessToken, loginCustomerId);
          return parseJson(
              request
                  .body(Map.of("operation", operation))
                  .retrieve()
                  .body(String.class));
        },
        "Google Ads customer mutation failed");
  }

  public JsonNode mutateAll(
      String accessToken,
      String customerId,
      String loginCustomerId,
      List<Map<String, Object>> mutateOperations) {
    if (mutateOperations == null || mutateOperations.isEmpty()) {
      throw new BadRequestException("Google Ads bulk mutation requires at least one operation");
    }

    return execute(
        () -> {
          RestClient.RequestBodySpec request =
              adsClient
                  .post()
                  .uri(
                      "/{version}/customers/{customerId}/googleAds:mutate",
                      properties.getApiVersion().trim(),
                      normalizeCustomerId(customerId))
                  .contentType(MediaType.APPLICATION_JSON);
          addAdsHeaders(request, accessToken, loginCustomerId);
          return parseJson(
              request
                  .body(Map.of("mutateOperations", mutateOperations, "partialFailure", false))
                  .retrieve()
                  .body(String.class));
        },
        "Google Ads bulk mutation failed");
  }

  public String firstBulkResourceName(JsonNode response, String resultField) {
    if (response == null || resultField == null || resultField.isBlank()) return null;
    JsonNode operations = response.path("mutateOperationResponses");
    if (!operations.isArray()) return null;
    for (JsonNode operation : operations) {
      String resourceName = operation.path(resultField).path("resourceName").asText(null);
      if (resourceName != null && !resourceName.isBlank()) return resourceName;
    }
    return null;
  }

  public JsonNode suggestGeoTargets(
      String accessToken,
      String loginCustomerId,
      String countryCode,
      List<String> locationNames) {
    if (countryCode == null || countryCode.isBlank()) {
      throw new BadRequestException("Google geo target country code is required");
    }
    if (locationNames == null || locationNames.isEmpty()) {
      throw new BadRequestException("Google geo target location names are required");
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("locale", "en");
    body.put("countryCode", countryCode.trim().toUpperCase());
    body.put("locationNames", Map.of("names", locationNames));

    return execute(
        () -> {
          RestClient.RequestBodySpec request =
              adsClient
                  .post()
                  .uri(
                      "/{version}/geoTargetConstants:suggest",
                      properties.getApiVersion().trim())
                  .contentType(MediaType.APPLICATION_JSON);
          addAdsHeaders(request, accessToken, loginCustomerId);
          return parseJson(
              request.body(body).retrieve().body(String.class));
        },
        "Unable to resolve Google Ads geo targets");
  }

  public JsonNode suggestBrands(
      String accessToken,
      String customerId,
      String loginCustomerId,
      String brandPrefix,
      List<String> selectedBrandIds) {
    if (brandPrefix == null || brandPrefix.isBlank()) {
      throw new BadRequestException("Google brand prefix is required");
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("brandPrefix", brandPrefix.trim());
    if (selectedBrandIds != null && !selectedBrandIds.isEmpty()) {
      body.put("selectedBrands", selectedBrandIds);
    }
    return execute(
        () -> {
          RestClient.RequestBodySpec request =
              adsClient
                  .post()
                  .uri(
                      "/{version}/customers/{customerId}:suggestBrands",
                      properties.getApiVersion().trim(),
                      normalizeCustomerId(customerId))
                  .contentType(MediaType.APPLICATION_JSON);
          addAdsHeaders(request, accessToken, loginCustomerId);
          return parseJson(request.body(body).retrieve().body(String.class));
        },
        "Unable to suggest Google Ads brands");
  }

  public JsonNode customerColonAction(
      String accessToken,
      String customerId,
      String loginCustomerId,
      String action,
      Map<String, Object> body) {
    if (action == null || action.isBlank()) {
      throw new BadRequestException("Google Ads customer action is required");
    }
    return execute(
        () -> {
          RestClient.RequestBodySpec request =
              adsClient
                  .post()
                  .uri(
                      "/{version}/customers/{customerId}:{action}",
                      properties.getApiVersion().trim(),
                      normalizeCustomerId(customerId),
                      action.trim())
                  .contentType(MediaType.APPLICATION_JSON);
          addAdsHeaders(request, accessToken, loginCustomerId);
          return parseJson(
              request
                  .body(body == null ? Map.of() : body)
                  .retrieve()
                  .body(String.class));
        },
        "Google Ads customer action failed for " + action);
  }

  public JsonNode customerAction(
      String accessToken,
      String customerId,
      String loginCustomerId,
      String actionPath,
      Map<String, Object> body) {
    if (actionPath == null || actionPath.isBlank()) {
      throw new BadRequestException("Google Ads customer action path is required");
    }

    return execute(
        () -> {
          RestClient.RequestBodySpec request =
              adsClient
                  .post()
                  .uri(
                      "/{version}/customers/{customerId}/{actionPath}",
                      properties.getApiVersion().trim(),
                      normalizeCustomerId(customerId),
                      actionPath)
                  .contentType(MediaType.APPLICATION_JSON);
          addAdsHeaders(request, accessToken, loginCustomerId);
          return parseJson(
              request
                  .body(body == null ? Map.of() : body)
                  .retrieve()
                  .body(String.class));
        },
        "Google Ads customer action failed for " + actionPath);
  }

  public String firstMutatedResourceName(JsonNode response) {
    if (response == null) return null;
    JsonNode results = response.path("results");
    if (!results.isArray() || results.isEmpty()) return null;
    String resourceName = results.get(0).path("resourceName").asText(null);
    return resourceName == null || resourceName.isBlank() ? null : resourceName;
  }

  public String customerResourceName(String customerId, String collection, String id) {
    return "customers/"
        + normalizeCustomerId(customerId)
        + "/"
        + collection
        + "/"
        + id;
  }

  private JsonNode searchStream(
      String accessToken, String customerId, String loginCustomerId, String query) {
    return searchStream(accessToken, customerId, loginCustomerId, query, false);
  }

  private JsonNode searchStream(
      String accessToken,
      String customerId,
      String loginCustomerId,
      String query,
      boolean includeDrafts) {
    return execute(
        () -> {
          RestClient.RequestBodySpec request =
              adsClient
                  .post()
                  .uri(
                      "/{version}/customers/{customerId}/googleAds:searchStream",
                      properties.getApiVersion().trim(),
                      normalizeCustomerId(customerId))
                  .contentType(MediaType.APPLICATION_JSON);
          addAdsHeaders(request, accessToken, loginCustomerId);
          Map<String, Object> body = new LinkedHashMap<>();
          body.put("query", query);
          if (includeDrafts) body.put("includeDrafts", true);
          return parseJson(request.body(body).retrieve().body(String.class));
        },
        "Unable to query Google Ads customer " + normalizeCustomerId(customerId));
  }

  private void addAdsHeaders(
      RestClient.RequestHeadersSpec<?> request,
      String accessToken,
      String loginCustomerId) {
    request.header(HttpHeaders.AUTHORIZATION, bearer(accessToken));

    // Google sunset developer tokens on 2026-09-09 and moved API access levels to
    // the Google Cloud project that owns the OAuth client. Keep this header optional
    // for backwards compatibility with projects that still supply one.
    if (properties.hasDeveloperToken()) {
      request.header("developer-token", properties.getDeveloperToken().trim());
    }
    if (loginCustomerId != null && !loginCustomerId.isBlank()) {
      request.header("login-customer-id", normalizeCustomerId(loginCustomerId));
    }
  }

  private JsonNode parseJson(String body) {
    if (body == null || body.isBlank()) return null;
    try {
      return JSON_MAPPER.readTree(body);
    } catch (JsonProcessingException exception) {
      throw new BadRequestException("Unable to parse Google API JSON response");
    }
  }

  private Iterable<JsonNode> iterable(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    return node;
  }

  private String nullableText(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || value.asText().isBlank()) return null;
    return value.asText();
  }

  private String normalizeCustomerId(String value) {
    if (value == null) return "";
    String normalized = value.trim();
    if (normalized.startsWith("customers/")) {
      normalized = normalized.substring("customers/".length());
    }
    return normalized.replace("-", "");
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Google Ads is not configured. Configure the Google OAuth client first");
    }
  }

  private String truncateForLog(String value) {
    if (value == null || value.isBlank()) return "<empty>";
    String normalized = value.replaceAll("\\s+", " ").trim();
    return normalized.length() > 2000 ? normalized.substring(0, 2000) + "..." : normalized;
  }

  private <T> T execute(Supplier<T> operation, String fallbackMessage) {
    try {
      T result = operation.get();
      if (result == null) {
        throw new BadRequestException(fallbackMessage + ": empty response");
      }
      return result;
    } catch (BadRequestException exception) {
      throw exception;
    } catch (RestClientResponseException exception) {
      String providerMessage = exception.getResponseBodyAsString();
      log.warn(
          "google_ads_provider_error operation={} status={} response={}",
          fallbackMessage,
          exception.getStatusCode().value(),
          truncateForLog(providerMessage));
      throw new BadRequestException(GoogleApiErrorMapper.googleAds(exception, fallbackMessage));
    } catch (RuntimeException exception) {
      throw new BadRequestException(fallbackMessage + ": " + exception.getMessage());
    }
  }

  public record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") Long expiresIn,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("scope") String scope,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("id_token") String idToken) {}

  public record UserProfile(
      String sub,
      String name,
      String email,
      @JsonProperty("email_verified") Boolean emailVerified) {}

  public record GoogleAdsAccountSummary(
      String customerId,
      String name,
      String currency,
      String timezone,
      boolean manager,
      boolean testAccount,
      String status,
      String loginCustomerId,
      int hierarchyLevel) {}
}
