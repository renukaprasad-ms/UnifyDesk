package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.common.exception.BadRequestException;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GoogleDataManagerApiClient {

  private final RestClient client = RestClient.create("https://datamanager.googleapis.com");

  public JsonNode ingestAudienceMembers(String accessToken, Map<String, Object> body) {
    return execute(
        () ->
            client
                .post()
                .uri("/v1/audienceMembers:ingest")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class),
        "Google Data Manager audience upload failed");
  }

  public JsonNode ingestEvents(String accessToken, Map<String, Object> body) {
    return execute(
        () ->
            client
                .post()
                .uri("/v1/events:ingest")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class),
        "Google Data Manager conversion upload failed");
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
      if (providerMessage != null && providerMessage.length() > 900) {
        providerMessage = providerMessage.substring(0, 900);
      }
      throw new BadRequestException(
          fallbackMessage
              + " (HTTP "
              + exception.getStatusCode().value()
              + ")"
              + (providerMessage == null || providerMessage.isBlank()
                  ? ""
                  : ": " + providerMessage));
    } catch (RuntimeException exception) {
      throw new BadRequestException(fallbackMessage + ": " + exception.getMessage());
    }
  }
}
