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
public class CloudflareManagedDnsProvider implements ManagedDnsProvider {

  private final ManagedEmailProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public CloudflareManagedDnsProvider(
      ManagedEmailProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restClient =
        RestClient.builder()
            .baseUrl("https://api.cloudflare.com/client/v4")
            .defaultHeader(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + (properties.getCloudflareApiToken() == null
                    ? ""
                    : properties.getCloudflareApiToken()))
            .build();
  }

  @Override
  public boolean available() {
    return properties.hasCloudflareConfiguration();
  }

  @Override
  public void ensure(DnsRecord record) {
    requireAvailable();
    try {
      List<CloudflareRecord> existing = find(record.type(), record.name());
      CloudflareRecord exact =
          existing.stream()
              .filter(
                  item ->
                      item.content().equals(record.value())
                          && (!isCname(record.type()) || Boolean.FALSE.equals(item.proxied())))
              .findFirst()
              .orElse(null);
      if (exact != null) return;

      Map<String, Object> payload = payload(record);
      if (existing.isEmpty()) {
        assertSuccess(
            restClient
                .post()
                .uri("/zones/{zone}/dns_records", properties.getCloudflareZoneId())
                .body(payload)
                .retrieve()
                .body(String.class));
      } else {
        assertSuccess(
            restClient
                .put()
                .uri(
                    "/zones/{zone}/dns_records/{id}",
                    properties.getCloudflareZoneId(),
                    existing.getFirst().id())
                .body(payload)
                .retrieve()
                .body(String.class));
      }
    } catch (RestClientResponseException exception) {
      throw unavailable("Hosted DNS update failed", exception);
    }
  }

  @Override
  public void deleteExact(List<DnsRecord> records) {
    requireAvailable();
    try {
      for (DnsRecord desired : records) {
        for (CloudflareRecord existing : find(desired.type(), desired.name())) {
          if (!existing.content().equals(desired.value())) continue;
          assertSuccess(
              restClient
                  .delete()
                  .uri(
                      "/zones/{zone}/dns_records/{id}",
                      properties.getCloudflareZoneId(),
                      existing.id())
                  .retrieve()
                  .body(String.class));
        }
      }
    } catch (RestClientResponseException exception) {
      throw unavailable("Hosted DNS cleanup failed", exception);
    }
  }

  private List<CloudflareRecord> find(DnsRecordType type, String name) {
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
    List<CloudflareRecord> records = new ArrayList<>();
    for (JsonNode item : root.path("result")) {
      records.add(
          new CloudflareRecord(
              item.path("id").asText(),
              item.path("content").asText(),
              item.has("proxied") ? item.path("proxied").asBoolean() : null));
    }
    return records;
  }

  private Map<String, Object> payload(DnsRecord record) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", record.type().name());
    payload.put("name", record.name());
    payload.put("content", record.value());
    payload.put("ttl", 1);
    if (record.priority() != null) payload.put("priority", record.priority());
    if (isCname(record.type())) payload.put("proxied", false);
    return payload;
  }

  private boolean isCname(DnsRecordType type) {
    return type == DnsRecordType.CNAME;
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
    if (!available()) {
      throw new ServiceUnavailableException("Managed Email hosted DNS is not configured");
    }
  }

  private ServiceUnavailableException unavailable(String message, RuntimeException cause) {
    return new ServiceUnavailableException(message, cause);
  }

  private record CloudflareRecord(String id, String content, Boolean proxied) {}
}
