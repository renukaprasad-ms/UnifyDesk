package com.messaging.channel.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.meta.control.MetaGraphException;
import com.messaging.channel.meta.control.MetaGraphException.SafeCategory;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MetaGraphClient {

  private final RestClient restClient = RestClient.create("https://graph.facebook.com");
  private final MetaWhatsAppProperties properties;
  private final ObjectMapper objectMapper;

  public MetaGraphClient(MetaWhatsAppProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public TokenResponse exchangeCode(String code) {
    requireConfigured();
    return execute(
        "exchange_code",
        () ->
            restClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/{version}/oauth/access_token")
                            .queryParam("client_id", properties.getAppId())
                            .queryParam("client_secret", properties.getAppSecret())
                            .queryParam("code", code)
                            .queryParam("redirect_uri", properties.getRedirectUri())
                            .build(properties.getGraphApiVersion()))
                .retrieve()
                .body(TokenResponse.class));
  }

  public TokenInspection inspectToken(String accessToken) {
    DebugTokenResponse response =
        execute(
            "debug_token",
            () ->
                restClient
                    .get()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/{version}/debug_token")
                                .queryParam("input_token", accessToken)
                                .build(properties.getGraphApiVersion()))
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(properties.getAppId() + "|" + properties.getAppSecret()))
                    .retrieve()
                    .body(DebugTokenResponse.class));

    DebugTokenData data = response == null ? null : response.data();
    if (data == null) {
      return new TokenInspection(false, Set.of(), List.of(), null);
    }

    Set<String> scopes = new LinkedHashSet<>();
    if (data.scopes() != null) {
      data.scopes().stream()
          .filter(value -> value != null && !value.isBlank())
          .forEach(scopes::add);
    }
    if (data.granularScopes() != null) {
      data.granularScopes().stream()
          .filter(value -> value != null && value.scope() != null && !value.scope().isBlank())
          .map(GranularScope::scope)
          .forEach(scopes::add);
    }
    Instant expiresAt =
        data.expiresAt() == null || data.expiresAt() <= 0
            ? null
            : Instant.ofEpochSecond(data.expiresAt());
    return new TokenInspection(
        Boolean.TRUE.equals(data.valid()),
        Set.copyOf(scopes),
        data.granularScopes() == null ? List.of() : List.copyOf(data.granularScopes()),
        expiresAt);
  }

  public List<String> getGrantedWabaIds(String accessToken) {
    TokenInspection inspection = inspectToken(accessToken);
    Set<String> ids = new LinkedHashSet<>();
    inspection.granularScopes().stream()
        .filter(
            scope ->
                "whatsapp_business_management".equals(scope.scope())
                    || "whatsapp_business_messaging".equals(scope.scope()))
        .filter(scope -> scope.targetIds() != null)
        .flatMap(scope -> scope.targetIds().stream())
        .filter(id -> id != null && !id.isBlank())
        .forEach(ids::add);
    return List.copyOf(ids);
  }

  public WabaDetails getWaba(String wabaId, String accessToken) {
    return execute(
        "get_waba",
        () ->
            restClient
                .get()
                .uri(
                    "/{version}/{wabaId}?fields=id,name,currency,timezone_id",
                    properties.getGraphApiVersion(),
                    wabaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(WabaDetails.class));
  }

  public List<PhoneNumber> getPhoneNumbers(String wabaId, String accessToken) {
    PhoneNumbersResponse response =
        execute(
            "get_phone_numbers",
            () ->
                restClient
                    .get()
                    .uri(
                        "/{version}/{wabaId}/phone_numbers?fields=id,display_phone_number,verified_name,quality_rating",
                        properties.getGraphApiVersion(),
                        wabaId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .retrieve()
                    .body(PhoneNumbersResponse.class));
    return response == null || response.data() == null ? List.of() : response.data();
  }

  public void registerPhoneNumber(String phoneNumberId, String accessToken, String pin) {
    if (!properties.isRegisterPhoneNumber()) {
      return;
    }
    execute(
        "register_phone",
        () -> {
          restClient
              .post()
              .uri(
                  "/{version}/{phoneNumberId}/register",
                  properties.getGraphApiVersion(),
                  phoneNumberId)
              .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("messaging_product", "whatsapp", "pin", pin))
              .retrieve()
              .toBodilessEntity();
          return Boolean.TRUE;
        });
  }

  public void subscribeApp(String wabaId, String accessToken) {
    execute(
        "subscribe_app",
        () -> {
          restClient
              .post()
              .uri("/{version}/{wabaId}/subscribed_apps", properties.getGraphApiVersion(), wabaId)
              .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
              .retrieve()
              .toBodilessEntity();
          return Boolean.TRUE;
        });
  }

  public boolean isAppSubscribed(String wabaId, String accessToken) {
    SubscribedAppsResponse response =
        execute(
            "get_subscribed_apps",
            () ->
                restClient
                    .get()
                    .uri(
                        "/{version}/{wabaId}/subscribed_apps",
                        properties.getGraphApiVersion(),
                        wabaId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .retrieve()
                    .body(SubscribedAppsResponse.class));
    if (response == null || response.data() == null) {
      return false;
    }
    return response.data().stream()
        .filter(app -> app != null && app.id() != null)
        .anyMatch(app -> properties.getAppId().equals(app.id()));
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Meta WhatsApp is not configured. Set the Meta app environment variables first");
    }
  }

  private <T> T execute(String operation, GraphCall<T> call) {
    try {
      T result = call.execute();
      if (result == null) {
        throw new MetaGraphException(
            operation, 502, null, null, null, null, SafeCategory.PROVIDER_ERROR, true);
      }
      return result;
    } catch (MetaGraphException exception) {
      throw exception;
    } catch (RestClientResponseException exception) {
      throw safeGraphException(operation, exception);
    } catch (ResourceAccessException exception) {
      throw new MetaGraphException(
          operation, 0, null, null, null, null, SafeCategory.TRANSIENT, true);
    }
  }

  private MetaGraphException safeGraphException(
      String operation, RestClientResponseException exception) {
    return parseGraphFailure(
        operation, exception.getStatusCode().value(), exception.getResponseBodyAsByteArray());
  }

  MetaGraphException parseGraphFailure(String operation, int status, byte[] responseBody) {
    String type = null;
    Integer code = null;
    Integer subcode = null;
    String fbtraceId = null;
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode error = root == null ? null : root.path("error");
      if (error != null && !error.isMissingNode()) {
        type = text(error, "type");
        code = integer(error, "code");
        subcode = integer(error, "error_subcode");
        fbtraceId = text(error, "fbtrace_id");
      }
    } catch (Exception ignored) {
      // Raw provider payload is intentionally discarded.
    }

    SafeCategory category = category(status, code);
    boolean retryable =
        category == SafeCategory.RATE_LIMIT
            || category == SafeCategory.TRANSIENT
            || status >= 500;
    return new MetaGraphException(
        operation, status, type, code, subcode, fbtraceId, category, retryable);
  }

  private SafeCategory category(int status, Integer code) {
    if (status == 401 || Integer.valueOf(190).equals(code)) {
      return SafeCategory.AUTH;
    }
    if (status == 403
        || Integer.valueOf(10).equals(code)
        || Integer.valueOf(200).equals(code)
        || Integer.valueOf(294).equals(code)) {
      return SafeCategory.PERMISSION;
    }
    if (status == 429
        || Integer.valueOf(4).equals(code)
        || Integer.valueOf(17).equals(code)
        || Integer.valueOf(32).equals(code)
        || Integer.valueOf(613).equals(code)) {
      return SafeCategory.RATE_LIMIT;
    }
    if (status >= 500 || status == 0) {
      return SafeCategory.TRANSIENT;
    }
    if (status == 400) {
      return SafeCategory.INVALID_REQUEST;
    }
    return SafeCategory.PROVIDER_ERROR;
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isTextual() ? value.asText() : null;
  }

  private Integer integer(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.canConvertToInt() ? value.asInt() : null;
  }

  @FunctionalInterface
  private interface GraphCall<T> {
    T execute();
  }

  public record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") Long expiresIn) {}

  public record TokenInspection(
      boolean valid, Set<String> scopes, List<GranularScope> granularScopes, Instant expiresAt) {}

  public record DebugTokenResponse(DebugTokenData data) {}

  public record DebugTokenData(
      @JsonProperty("is_valid") Boolean valid,
      List<String> scopes,
      @JsonProperty("granular_scopes") List<GranularScope> granularScopes,
      @JsonProperty("expires_at") Long expiresAt) {}

  public record GranularScope(
      String scope, @JsonProperty("target_ids") List<String> targetIds) {}

  public record WabaDetails(
      String id, String name, String currency, @JsonProperty("timezone_id") String timezoneId) {}

  public record PhoneNumbersResponse(List<PhoneNumber> data) {}

  public record PhoneNumber(
      String id,
      @JsonProperty("display_phone_number") String displayPhoneNumber,
      @JsonProperty("verified_name") String verifiedName,
      @JsonProperty("quality_rating") String qualityRating) {}

  public record SubscribedAppsResponse(List<SubscribedApp> data) {}

  public record SubscribedApp(String id, String name) {}
}
