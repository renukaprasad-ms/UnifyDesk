package com.messaging.ads.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class GoogleMerchantApiClient {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private final RestClient client = RestClient.create("https://merchantapi.googleapis.com");

  public JsonNode listAccounts(String accessToken, String pageToken) {
    return execute(
        () ->
            parseJson(
                client
                    .get()
                    .uri(
                        builder -> {
                          builder.path("/accounts/v1/accounts").queryParam("pageSize", 500);
                          if (pageToken != null && !pageToken.isBlank()) {
                            builder.queryParam("pageToken", pageToken);
                          }
                          return builder.build();
                        })
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .retrieve()
                    .body(String.class)),
        "Unable to list Google Merchant Center accounts");
  }

  public JsonNode listDataSources(
      String accessToken, String merchantId, String pageToken) {
    return execute(
        () ->
            parseJson(
                client
                    .get()
                    .uri(
                        builder -> {
                          builder
                              .path("/datasources/v1/accounts/{merchantId}/dataSources")
                              .queryParam("pageSize", 1000);
                          if (pageToken != null && !pageToken.isBlank()) {
                            builder.queryParam("pageToken", pageToken);
                          }
                          return builder.build(merchantId);
                        })
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .retrieve()
                    .body(String.class)),
        "Unable to list Merchant Center data sources");
  }

  public JsonNode createDataSource(
      String accessToken, String merchantId, Map<String, Object> body) {
    return execute(
        () ->
            parseJson(
                client
                    .post()
                    .uri("/datasources/v1/accounts/{merchantId}/dataSources", merchantId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class)),
        "Unable to create Merchant Center data source");
  }

  public void deleteDataSource(
      String accessToken, String merchantId, String dataSourceId) {
    executeVoid(
        () ->
            client
                .delete()
                .uri(
                    "/datasources/v1/accounts/{merchantId}/dataSources/{dataSourceId}",
                    merchantId,
                    dataSourceId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .toBodilessEntity(),
        "Unable to delete Merchant Center data source");
  }

  public JsonNode listProducts(
      String accessToken, String merchantId, String pageToken, int pageSize) {
    return execute(
        () ->
            parseJson(
                client
                    .get()
                    .uri(
                        builder -> {
                          builder
                              .path("/products/v1/accounts/{merchantId}/products")
                              .queryParam("pageSize", Math.max(1, Math.min(pageSize, 250)));
                          if (pageToken != null && !pageToken.isBlank()) {
                            builder.queryParam("pageToken", pageToken);
                          }
                          return builder.build(merchantId);
                        })
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .retrieve()
                    .body(String.class)),
        "Unable to list Merchant Center products");
  }

  public JsonNode insertProductInput(
      String accessToken,
      String merchantId,
      String dataSource,
      Map<String, Object> body) {
    return execute(
        () ->
            parseJson(
                client
                    .post()
                    .uri(
                        builder ->
                            builder
                                .path(
                                    "/products/v1/accounts/{merchantId}/productInputs:insert")
                                .queryParam("dataSource", dataSource)
                                .build(merchantId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class)),
        "Unable to upload Merchant Center product");
  }

  public void deleteProductInput(
      String accessToken,
      String merchantId,
      String encodedProductInputId,
      String dataSource) {
    executeVoid(
        () ->
            client
                .delete()
                .uri(
                    builder ->
                        builder
                            .path(
                                "/products/v1/accounts/{merchantId}/productInputs/{productInputId}")
                            .queryParam("dataSource", dataSource)
                            .build(merchantId, encodedProductInputId))
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .toBodilessEntity(),
        "Unable to delete Merchant Center product input");
  }

  private JsonNode parseJson(String body) {
    if (body == null || body.isBlank()) return null;
    try {
      return JSON_MAPPER.readTree(body);
    } catch (JsonProcessingException exception) {
      throw new BadRequestException("Unable to parse Google Merchant API JSON response");
    }
  }

  private String bearer(String accessToken) {
    return "Bearer " + accessToken;
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
      throw providerError(exception, fallbackMessage);
    } catch (RuntimeException exception) {
      throw new BadRequestException(fallbackMessage + ": " + exception.getMessage());
    }
  }

  private void executeVoid(Runnable operation, String fallbackMessage) {
    try {
      operation.run();
    } catch (RestClientResponseException exception) {
      throw providerError(exception, fallbackMessage);
    } catch (RuntimeException exception) {
      throw new BadRequestException(fallbackMessage + ": " + exception.getMessage());
    }
  }

  private BadRequestException providerError(
      RestClientResponseException exception, String fallbackMessage) {
    String providerMessage = exception.getResponseBodyAsString();
    log.warn(
        "google_merchant_provider_error operation={} status={} response={}",
        fallbackMessage,
        exception.getStatusCode().value(),
        truncateForLog(providerMessage));
    return new BadRequestException(GoogleApiErrorMapper.merchant(exception, fallbackMessage));
  }

  private String truncateForLog(String value) {
    if (value == null || value.isBlank()) return "<empty>";
    String normalized = value.replaceAll("\\s+", " ").trim();
    return normalized.length() > 2000 ? normalized.substring(0, 2000) + "..." : normalized;
  }
}
