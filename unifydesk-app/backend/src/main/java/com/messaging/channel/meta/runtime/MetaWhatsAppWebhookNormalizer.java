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
    if (!"whatsapp_business_account".equals(text(root, "object"))) {
      throw new InvalidPayloadException("Unsupported WhatsApp webhook object", null);
    }

    List<NormalizedEvent> events = new ArrayList<>();
    JsonNode entries = root.get("entry");
    if (entries == null || !entries.isArray() || entries.isEmpty()) {
      throw new InvalidPayloadException("WhatsApp webhook entry is required", null);
    }

    for (JsonNode entry : entries) {
      if (!entry.isObject()) {
        throw new InvalidPayloadException("Invalid WhatsApp webhook entry", null);
      }
      String entryId = text(entry, "id");
      JsonNode changes = entry.get("changes");
      if (changes == null || !changes.isArray() || changes.isEmpty()) {
        throw new InvalidPayloadException("WhatsApp webhook changes are required", null);
      }
      for (JsonNode change : changes) {
        if (!change.isObject()) {
          throw new InvalidPayloadException("Invalid WhatsApp webhook change", null);
        }
        String field = text(change, "field");
        JsonNode value = change.get("value");
        if (field == null || value == null || !value.isObject()) {
          throw new InvalidPayloadException("Invalid WhatsApp webhook change envelope", null);
        }
        JsonNode messages = value.get("messages");
        JsonNode statuses = value.get("statuses");
        if ((messages != null && !messages.isArray())
            || (statuses != null && !statuses.isArray())) {
          throw new InvalidPayloadException("Invalid WhatsApp webhook effect shape", null);
        }
        boolean hasMessageEffects =
            (messages != null && !messages.isEmpty()) || (statuses != null && !statuses.isEmpty());
        if (hasMessageEffects && !"messages".equals(field)) {
          throw new InvalidPayloadException("Unsupported WhatsApp webhook effect field", null);
        }
        String phoneNumberId = text(value.path("metadata"), "phone_number_id");
        JsonNode contacts = value.path("contacts");

        messages = value.path("messages");
        if (messages.isArray()) {
          for (JsonNode message : messages) {
            String providerMessageId = text(message, "id");
            JsonNode payload =
                effect(entryId, field, phoneNumberId, contacts, "message", message);
            add(
                events,
                event(
                    canonicalMessageKey(phoneNumberId, providerMessageId, payload),
                    "MESSAGE",
                    entryId,
                    phoneNumberId,
                    providerMessageId,
                    providerInstant(message.path("timestamp")),
                    payload));
          }
        }

        statuses = value.path("statuses");
        if (statuses.isArray()) {
          for (JsonNode status : statuses) {
            String providerMessageId = text(status, "id");
            String providerStatus = text(status, "status");
            String providerTimestamp = text(status, "timestamp");
            JsonNode payload =
                effect(entryId, field, phoneNumberId, contacts, "status", status);
            add(
                events,
                event(
                    canonicalStatusKey(
                        phoneNumberId,
                        providerMessageId,
                        providerStatus,
                        providerTimestamp,
                        payload),
                    "STATUS",
                    entryId,
                    phoneNumberId,
                    providerMessageId,
                    providerInstant(status.path("timestamp")),
                    payload));
          }
        }

        if ((!messages.isArray() || messages.isEmpty())
            && (!statuses.isArray() || statuses.isEmpty())) {
          String normalizedField =
              field == null ? "" : field.toLowerCase(java.util.Locale.ROOT);
          String kind =
              normalizedField.contains("template")
                  ? "TEMPLATE_STATUS"
                  : normalizedField.contains("account")
                          || normalizedField.contains("phone_number")
                      ? "ACCOUNT_UPDATE"
                      : "UNKNOWN";
          add(
              events,
              event(
                  hashedKey(
                      kind
                          + "|"
                          + value(entryId)
                          + "|"
                          + value(field)
                          + "|"
                          + value(phoneNumberId)
                          + "|"
                          + value),
                  kind,
                  entryId,
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
      String eventKey,
      String kind,
      String entryId,
      String phoneNumberId,
      String providerMessageId,
      Instant providerEventAt,
      JsonNode payload) {
    return new NormalizedEvent(
        eventKey,
        entryId,
        phoneNumberId,
        providerMessageId,
        kind,
        providerEventAt,
        payload.toString());
  }

  private String canonicalMessageKey(
      String phoneNumberId, String providerMessageId, JsonNode payload) {
    if (phoneNumberId != null && providerMessageId != null) {
      return boundedKey("msg:" + phoneNumberId + ":" + providerMessageId);
    }
    return hashedKey("msg|" + value(phoneNumberId) + "|" + value(providerMessageId) + "|" + payload);
  }

  private String canonicalStatusKey(
      String phoneNumberId,
      String providerMessageId,
      String status,
      String providerTimestamp,
      JsonNode payload) {
    if (phoneNumberId != null
        && providerMessageId != null
        && status != null
        && providerTimestamp != null) {
      return boundedKey(
          "status:"
              + phoneNumberId
              + ":"
              + providerMessageId
              + ":"
              + status
              + ":"
              + providerTimestamp);
    }
    return hashedKey(
        "status|"
            + value(phoneNumberId)
            + "|"
            + value(providerMessageId)
            + "|"
            + value(status)
            + "|"
            + value(providerTimestamp)
            + "|"
            + payload);
  }

  private String boundedKey(String key) {
    return key.length() <= 255 ? key : hashedKey(key);
  }

  private String hashedKey(String material) {
    return "sha256:" + sha256(material);
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
