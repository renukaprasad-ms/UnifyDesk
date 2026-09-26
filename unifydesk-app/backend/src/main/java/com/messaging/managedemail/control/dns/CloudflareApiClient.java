package com.messaging.managedemail.control.dns;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.managedemail.control.config.ManagedEmailProperties;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.DnsRecordType;
import java.util.*;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CloudflareApiClient implements CloudflareDnsClient {

  private final ManagedEmailProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public CloudflareApiClient(ManagedEmailProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restClient =
        RestClient.builder()
            .baseUrl("https://api.cloudflare.com/client/v4")
            .defaultHeader(
                HttpHeaders.AUTHORIZATION,
                "Bearer "
                    + (properties.getCloudflareApiToken() == null
                        ? ""
                        : properties.getCloudflareApiToken()))
            .build();
  }

  @Override
  public List<Record> find(DnsRecordType type, String name) {
    requireAvailable();
    try {
      String body =
          restClient
              .get()
              .uri(
                  uri ->
                      uri.path("/zones/{zone}/dns_records")
                          .queryParam("type", type.name())
                          .queryParam("name", name)
                          .build(properties.getCloudflareZoneId()))
              .retrieve()
              .body(String.class);
      JsonNode root = parse(body);
      ensureSuccess(root);
      List<Record> records = new ArrayList<>();
      for (JsonNode item : root.path("result")) {
        records.add(
            new Record(
                item.path("id").asText(),
                item.path("content").asText(),
                item.has("proxied") ? item.path("proxied").asBoolean() : null));
      }
      return records;
    } catch (RestClientResponseException exception) {
      throw unavailable("Hosted DNS lookup failed", exception);
    }
  }

  @Override
  public void create(
      DnsRecordType type, String name, String content, Integer priority, boolean proxied) {
    write(null, type, name, content, priority, proxied);
  }

  @Override
  public void update(
      String id,
      DnsRecordType type,
      String name,
      String content,
      Integer priority,
      boolean proxied) {
    write(id, type, name, content, priority, proxied);
  }

  @Override
  public void delete(String id) {
    requireAvailable();
    try {
      assertSuccess(
          restClient
              .delete()
              .uri(
                  "/zones/{zone}/dns_records/{id}",
                  properties.getCloudflareZoneId(),
                  id)
              .retrieve()
              .body(String.class));
    } catch (RestClientResponseException exception) {
      throw unavailable("Hosted DNS cleanup failed", exception);
    }
  }

  private void write(
      String id,
      DnsRecordType type,
      String name,
      String content,
      Integer priority,
      boolean proxied) {
    requireAvailable();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", type.name());
    payload.put("name", name);
    payload.put("content", content);
    payload.put("ttl", 1);
    if (priority != null) payload.put("priority", priority);
    if (type == DnsRecordType.CNAME) payload.put("proxied", proxied);

    try {
      String body =
          id == null
              ? restClient
                  .post()
                  .uri("/zones/{zone}/dns_records", properties.getCloudflareZoneId())
                  .body(payload)
                  .retrieve()
                  .body(String.class)
              : restClient
                  .put()
                  .uri(
                      "/zones/{zone}/dns_records/{id}",
                      properties.getCloudflareZoneId(),
                      id)
                  .body(payload)
                  .retrieve()
                  .body(String.class);
      assertSuccess(body);
    } catch (RestClientResponseException exception) {
      throw unavailable("Hosted DNS update failed", exception);
    }
  }

  private void assertSuccess(String body) {
    ensureSuccess(parse(body));
  }

  private JsonNode parse(String body) {
    try {
      return objectMapper.readTree(body == null ? "{}" : body);
    } catch (Exception exception) {
      throw unavailable("Hosted DNS returned an invalid response", exception);
    }
  }

  private void ensureSuccess(JsonNode root) {
    if (!root.path("success").asBoolean(false)) {
      throw new ServiceUnavailableException("Hosted DNS operation was not accepted");
    }
  }

  private void requireAvailable() {
    if (!properties.hasCloudflareConfiguration()) {
      throw new ServiceUnavailableException("Managed Email hosted DNS is not configured");
    }
  }

  private ServiceUnavailableException unavailable(String message, RuntimeException cause) {
    return new ServiceUnavailableException(message, cause);
  }
}
