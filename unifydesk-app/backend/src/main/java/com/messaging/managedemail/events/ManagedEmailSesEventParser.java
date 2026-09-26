package com.messaging.managedemail.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ManagedEmailSesEventParser {

  private final ObjectMapper objectMapper;
  private final ManagedEmailRuntimeProperties properties;

  public ManagedEmailProviderEvent parse(SnsEnvelope envelope) {
    try {
      JsonNode root = objectMapper.readTree(envelope.message());
      validateDepth(root, 1);
      ManagedEmailProviderEventType type =
          ManagedEmailProviderEventType.fromSes(text(root, "eventType", "notificationType"));
      JsonNode mail = root.path("mail");
      String providerMessageId = required(mail, "messageId");
      Instant timestamp =
          mail.hasNonNull("timestamp") ? Instant.parse(mail.get("timestamp").asText()) : envelope.timestamp();
      JsonNode tags = mail.path("tags");

      return new ManagedEmailProviderEvent(
          envelope.messageId(),
          type,
          timestamp,
          providerMessageId,
          longTag(tags, "ud_company_id"),
          longTag(tags, "ud_run_id"),
          longTag(tags, "ud_sender_id"),
          longTag(tags, "ud_domain_id"),
          longTag(tags, "ud_recipient_id"),
          tag(tags, "ud_correlation"),
          type == ManagedEmailProviderEventType.BOUNCE
              ? root.path("bounce").path("bounceType").asText(null)
              : null,
          diagnosticCode(root, type),
          sha256(envelope.message()));
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid SES event payload", exception);
    }
  }

  private String diagnosticCode(JsonNode root, ManagedEmailProviderEventType type) {
    if (type == ManagedEmailProviderEventType.BOUNCE) {
      return root.path("bounce").path("bounceSubType").asText(null);
    }
    if (type == ManagedEmailProviderEventType.REJECT) {
      return "REJECT";
    }
    if (type == ManagedEmailProviderEventType.RENDERING_FAILURE) {
      return "RENDERING_FAILURE";
    }
    return null;
  }

  private String text(JsonNode root, String primary, String fallback) {
    String value = root.path(primary).asText(null);
    return value == null || value.isBlank() ? root.path(fallback).asText(null) : value;
  }

  private Long longTag(JsonNode tags, String name) {
    String value = tag(tags, name);
    if (value == null || value.isBlank()) return null;
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid SES correlation tag");
    }
  }

  private String tag(JsonNode tags, String name) {
    JsonNode value = tags.path(name);
    if (value.isArray() && !value.isEmpty()) return value.get(0).asText(null);
    if (value.isTextual()) return value.asText();
    return null;
  }

  private String required(JsonNode node, String field) {
    String value = node.path(field).asText(null);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing SES field " + field);
    }
    return value;
  }

  private String sha256(String value) throws Exception {
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(digest);
  }

  private void validateDepth(JsonNode node, int depth) {
    if (depth > Math.max(1, properties.getWebhookMaxJsonDepth())) {
      throw new IllegalArgumentException("SES JSON exceeds depth limit");
    }
    if (node.isContainerNode()) {
      node.elements().forEachRemaining(child -> validateDepth(child, depth + 1));
    }
  }
}
