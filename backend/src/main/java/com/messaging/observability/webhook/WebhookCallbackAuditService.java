package com.messaging.observability.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.messaging.channel.enums.ProviderType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WebhookCallbackAuditService {

  private static final int MAX_TEXT_LENGTH = 512_000;
  private static final Set<String> SENSITIVE_HEADERS =
      Set.of(
          "authorization",
          "proxy-authorization",
          "cookie",
          "set-cookie",
          "x-xsrf-token",
          "x-csrf-token",
          "x-hub-signature-256");
  private static final Set<String> SENSITIVE_JSON_KEYS =
      Set.of(
          "authorization",
          "token",
          "accesstoken",
          "refreshtoken",
          "clientstate",
          "secret",
          "appsecret",
          "clientsecret",
          "signature",
          "password",
          "credential",
          "verificationtoken",
          "verifytoken");

  private final WebhookCallbackRepository repository;
  private final ObjectMapper objectMapper;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long recordReceived(
      ProviderType providerType,
      String callbackType,
      String endpoint,
      String method,
      String queryString,
      String remoteAddress,
      String contentType,
      HttpHeaders headers,
      String payload) {
    WebhookCallback callback = new WebhookCallback();
    callback.setProviderType(providerType);
    callback.setCallbackType(callbackType);
    callback.setEndpoint(endpoint);
    callback.setHttpMethod(method);
    callback.setQueryString(redactQuery(queryString));
    callback.setRemoteAddress(remoteAddress);
    callback.setContentType(contentType);
    callback.setRequestHeaders(serializeHeaders(headers));
    callback.setPayload(sanitizePayload(payload));
    callback.setStatus(WebhookCallbackStatus.RECEIVED);
    return repository.save(callback).getId();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markProcessed(
      Long callbackId,
      String eventId,
      Object decodedPayload,
      Object processingResult,
      int httpStatus) {
    finish(
        callbackId,
        WebhookCallbackStatus.PROCESSED,
        eventId,
        decodedPayload,
        processingResult,
        httpStatus,
        null);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markIgnored(
      Long callbackId,
      String eventId,
      Object decodedPayload,
      String reason,
      int httpStatus) {
    finish(
        callbackId,
        WebhookCallbackStatus.IGNORED,
        eventId,
        decodedPayload,
        Map.of("reason", reason),
        httpStatus,
        null);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markRejected(Long callbackId, String eventId, String reason, int httpStatus) {
    finish(
        callbackId,
        WebhookCallbackStatus.REJECTED,
        eventId,
        null,
        null,
        httpStatus,
        reason);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(
      Long callbackId,
      String eventId,
      Object decodedPayload,
      Object processingResult,
      String errorMessage,
      int httpStatus) {
    finish(
        callbackId,
        WebhookCallbackStatus.FAILED,
        eventId,
        decodedPayload,
        processingResult,
        httpStatus,
        errorMessage);
  }

  private void finish(
      Long callbackId,
      WebhookCallbackStatus status,
      String eventId,
      Object decodedPayload,
      Object processingResult,
      int httpStatus,
      String errorMessage) {
    if (callbackId == null) return;

    WebhookCallback callback = repository.findById(callbackId).orElse(null);
    if (callback == null) return;

    Instant completedAt = Instant.now();
    callback.setEventId(limit(eventId));
    callback.setDecodedPayload(serialize(decodedPayload));
    callback.setProcessingResult(serialize(processingResult));
    callback.setStatus(status);
    callback.setHttpStatus(httpStatus);
    callback.setErrorMessage(limit(errorMessage));
    callback.setCompletedAt(completedAt);
    callback.setDurationMs(
        Math.max(0, Duration.between(callback.getReceivedAt(), completedAt).toMillis()));
    repository.save(callback);
  }

  private String serializeHeaders(HttpHeaders headers) {
    if (headers == null || headers.isEmpty()) return null;

    Map<String, List<String>> sanitized = new LinkedHashMap<>();
    headers.forEach(
        (name, values) -> {
          if (SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            sanitized.put(name, List.of("[REDACTED]"));
          } else {
            sanitized.put(name, values);
          }
        });
    return serialize(sanitized);
  }

  private String sanitizePayload(String payload) {
    if (payload == null || payload.isBlank()) return payload;
    try {
      JsonNode node = objectMapper.readTree(payload);
      redactNode(node);
      return limit(objectMapper.writeValueAsString(node));
    } catch (Exception ignored) {
      return limit(redactQuery(payload));
    }
  }

  private String serialize(Object value) {
    if (value == null) return null;
    try {
      JsonNode node = objectMapper.valueToTree(value);
      redactNode(node);
      return limit(objectMapper.writeValueAsString(node));
    } catch (Exception exception) {
      return limit(String.valueOf(value));
    }
  }

  private void redactNode(JsonNode node) {
    if (node == null) return;
    if (node instanceof ObjectNode objectNode) {
      List<String> fieldNames = new ArrayList<>();
      objectNode.fieldNames().forEachRemaining(fieldNames::add);
      for (String fieldName : fieldNames) {
        JsonNode child = objectNode.get(fieldName);
        if (isSensitiveJsonKey(fieldName)) {
          objectNode.put(fieldName, "[REDACTED]");
        } else {
          redactNode(child);
        }
      }
      return;
    }
    if (node instanceof ArrayNode arrayNode) {
      arrayNode.forEach(this::redactNode);
    }
  }

  private boolean isSensitiveJsonKey(String name) {
    if (name == null) return false;
    String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    return SENSITIVE_JSON_KEYS.contains(normalized);
  }

  private String redactQuery(String queryString) {
    if (queryString == null || queryString.isBlank()) return null;
    return limit(
        queryString.replaceAll(
            "(?i)(token|access_token|refresh_token|authorization|signature|secret|clientState)=([^&]*)",
            "$1=[REDACTED]"));
  }

  private String limit(String value) {
    if (value == null) return null;
    if (value.length() <= MAX_TEXT_LENGTH) return value;
    return value.substring(0, MAX_TEXT_LENGTH) + "\n...[truncated]";
  }
}
