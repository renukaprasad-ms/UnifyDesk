package com.messaging.channel.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MetaGraphClient {

  private final RestClient restClient = RestClient.create("https://graph.facebook.com");
  private final MetaWhatsAppProperties properties;

  public MetaGraphClient(MetaWhatsAppProperties properties) {
    this.properties = properties;
  }

  public TokenResponse exchangeCode(String code) {
    requireConfigured();
    return execute(
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
                            .build(properties.getGraphApiVersion()))
                .retrieve()
                .body(TokenResponse.class));
  }

  public WabaDetails getWaba(String wabaId, String accessToken) {
    return execute(
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
    if (!properties.isRegisterPhoneNumber()) return;
    execute(
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

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Meta WhatsApp is not configured. Set the Meta app environment variables first");
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

  public record WabaDetails(
      String id, String name, String currency, @JsonProperty("timezone_id") String timezoneId) {}

  public record PhoneNumbersResponse(List<PhoneNumber> data) {}

  public record PhoneNumber(
      String id,
      @JsonProperty("display_phone_number") String displayPhoneNumber,
      @JsonProperty("verified_name") String verifiedName,
      @JsonProperty("quality_rating") String qualityRating) {}
}
