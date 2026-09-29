package com.messaging.channel.meta.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class MetaWhatsAppInboundMessageMapper {

  public MappedMessage map(JsonNode message) {
    String type = text(message, "type");
    String replyToProviderMessageId = text(message.path("context"), "id");
    if ("text".equals(type)) {
      String body = text(message.path("text"), "body");
      return new MappedMessage(
          "TEXT",
          compact(body, 500),
          body,
          null,
          null,
          null,
          replyToProviderMessageId);
    }
    if ("image".equals(type)) {
      JsonNode media = message.path("image");
      String caption = text(media, "caption");
      return new MappedMessage(
          "IMAGE",
          compact(caption == null ? "[Image]" : caption, 500),
          caption,
          text(media, "id"),
          text(media, "mime_type"),
          null,
          replyToProviderMessageId);
    }
    if ("document".equals(type)) {
      JsonNode media = message.path("document");
      String caption = text(media, "caption");
      String fileName = text(media, "filename");
      String preview = caption != null ? caption : fileName != null ? fileName : "[Document]";
      return new MappedMessage(
          "DOCUMENT",
          compact(preview, 500),
          caption,
          text(media, "id"),
          text(media, "mime_type"),
          fileName,
          replyToProviderMessageId);
    }
    String placeholder =
        "[Unsupported WhatsApp message: " + (type == null ? "unknown" : type) + "]";
    return new MappedMessage(
        "UNSUPPORTED",
        placeholder,
        placeholder,
        null,
        null,
        null,
        replyToProviderMessageId);
  }

  private String text(JsonNode node, String field) {
    if (node == null) return null;
    JsonNode value = node.get(field);
    return value == null || !value.isValueNode() || value.asText().isBlank()
        ? null
        : value.asText().trim();
  }

  private String compact(String value, int max) {
    if (value == null) return null;
    String normalized = value.replaceAll("\\s+", " ").trim();
    return normalized.length() <= max ? normalized : normalized.substring(0, max);
  }

  public record MappedMessage(
      String kind,
      String preview,
      String bodyText,
      String providerMediaId,
      String mimeType,
      String fileName,
      String replyToProviderMessageId) {}
}
