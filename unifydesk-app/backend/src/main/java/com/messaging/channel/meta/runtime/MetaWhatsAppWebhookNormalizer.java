package com.messaging.channel.meta.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MetaWhatsAppWebhookNormalizer {
  private final ObjectMapper objectMapper;
  private final MetaWhatsAppRuntimeProperties properties;

  public MetaWhatsAppWebhookNormalizer(
      ObjectMapper objectMapper, MetaWhatsAppRuntimeProperties properties) {
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  public List<NormalizedEvent> normalize(byte[] body) {
    final JsonNode root;
    try {
      root = objectMapper.readTree(body);
    } catch (Exception exception) {
      throw new InvalidPayloadException("Invalid WhatsApp webhook JSON", exception);
    }
    if (root == null || !root.isObject()) {
      throw new InvalidPayloadException("Invalid WhatsApp webhook JSON", null);
    }

    List<NormalizedEvent> events = new ArrayList<>();
    JsonNode entries = root.path("entry");
    if (!entries.isArray()) return events;

    for (JsonNode entry : entries) {
      String entryId = text(entry, "id");
      JsonNode changes = entry.path("changes");
      if (!changes.isArray()) continue;
      for (JsonNode change : changes) {
        String field = text(change, "field");
        JsonNode value = change.path("value");
        String phoneNumberId = text(value.path("metadata"), "phone_number_id");
        JsonNode contacts = value.path("contacts");

        JsonNode messages = value.path("messages");
        if (messages.isArray()) {
          for (JsonNode message : messages) {
            String providerMessageId = text(message, "id");
            add(
                events,
                event(
                    "MESSAGE",
                    entryId,
                    field,
                    phoneNumberId,
                    providerMessageId,
                    providerInstant(message.path("timestamp")),
                    effect(entryId, field, phoneNumberId, contacts, "message", message)));
          }
        }

        JsonNode statuses = value.path("statuses");
        if (statuses.isArray()) {
          for (JsonNode status : statuses) {
            String providerMessageId = text(status, "id");
            add(
                events,
                event(
                    "STATUS",
                    entryId,
                    field,
                    phoneNumberId,
                    providerMessageId,
                    providerInstant(status.path("timestamp")),
                    effect(entryId, field, phoneNumberId, contacts, "status", status)));
          }
        }

        if ((!messages.isArray() || messages.isEmpty())
            && (!statuses.isArray() || statuses.isEmpty())) {
          String kind =
              field != null && field.toLowerCase(java.util.Locale.ROOT).contains("template")
                  ? "TEMPLATE_STATUS"
                  : "ACCOUNT_UPDATE";
          add(
              events,
              event(
                  kind,
                  entryId,
                  field,
                  phoneNumberId,
                  null,
                  Instant.now(),
                  effect(entryId, field, phoneNumberId, contacts, "value", value)));
        }
      }
    }
    return List.copyOf(events);
  }

  private void add(List<NormalizedEvent> events, NormalizedEvent event) {
    if (events.size() >= properties.getWebhookMaxEffects()) {
      throw new InvalidPayloadException("WhatsApp webhook contains too many effects", null);
    }
    events.add(event);
  }

  private NormalizedEvent event(
      String kind,
      String entryId,
      String field,
      String phoneNumberId,
      String providerMessageId,
      Instant providerEventAt,
      JsonNode payload) {
    String material =
        kind
            + "|"
            + value(phoneNumberId)
            + "|"
            + value(providerMessageId)
            + "|"
            + value(field)
            + "|"
            + providerEventAt
            + "|"
            + payload;
    return new NormalizedEvent(
        "sha256:" + sha256(material),
        entryId,
        phoneNumberId,
        providerMessageId,
        kind,
        providerEventAt,
        payload.toString());
  }

  private ObjectNode effect(
      String entryId,
      String field,
      String phoneNumberId,
      JsonNode contacts,
      String effectName,
      JsonNode effect) {
    ObjectNode result = objectMapper.createObjectNode();
    put(result, "entryId", entryId);
    put(result, "field", field);
    put(result, "phoneNumberId", phoneNumberId);
    if (contacts != null && !contacts.isMissingNode()) result.set("contacts", contacts.deepCopy());
    result.set(effectName, effect.deepCopy());
    return result;
  }

  private void put(ObjectNode node, String field, String value) {
    if (value != null) node.put(field, value);
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isMissingNode()) return null;
    JsonNode value = node.get(field);
    return value != null && value.isValueNode() && !value.asText().isBlank()
        ? value.asText().trim()
        : null;
  }

  private Instant providerInstant(JsonNode value) {
    if (value != null && !value.isMissingNode()) {
      try {
        return Instant.ofEpochSecond(Long.parseLong(value.asText()));
      } catch (RuntimeException ignored) {
        // Provider timestamp is optional; receipt time remains an ordering fallback.
      }
    }
    return Instant.now();
  }

  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private String value(String value) {
    return value == null ? "" : value;
  }

  public record NormalizedEvent(
      String eventKey,
      String providerObjectId,
      String phoneNumberId,
      String providerMessageId,
      String eventKind,
      Instant providerEventAt,
      String payloadJson) {}

  public static final class InvalidPayloadException extends RuntimeException {
    public InvalidPayloadException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
