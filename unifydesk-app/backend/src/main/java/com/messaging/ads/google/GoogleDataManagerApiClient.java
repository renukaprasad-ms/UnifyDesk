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
public class GoogleDataManagerApiClient {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private final RestClient client = RestClient.create("https://datamanager.googleapis.com");

  public JsonNode ingestAudienceMembers(String accessToken, Map<String, Object> body) {
    return execute(
        () ->
            parseJson(
                client
                    .post()
                    .uri("/v1/audienceMembers:ingest")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class)),
        "Google Data Manager audience upload failed");
  }

  public JsonNode ingestEvents(String accessToken, Map<String, Object> body) {
    return execute(
        () ->
            parseJson(
                client
                    .post()
                    .uri("/v1/events:ingest")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class)),
        "Google Data Manager conversion upload failed");
  }

  private JsonNode parseJson(String body) {
    if (body == null || body.isBlank()) return null;
    try {
      return JSON_MAPPER.readTree(body);
    } catch (JsonProcessingException exception) {
      throw new BadRequestException("Unable to parse Google Data Manager JSON response");
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
          "google_data_manager_provider_error operation={} status={} response={}",
          fallbackMessage,
          exception.getStatusCode().value(),
          truncateForLog(providerMessage));
      throw new BadRequestException(GoogleApiErrorMapper.dataManager(exception, fallbackMessage));
    } catch (RuntimeException exception) {
      throw new BadRequestException(fallbackMessage + ": " + exception.getMessage());
    }
  }
}
