package com.messaging.channel.meta;

import com.messaging.channel.meta.runtime.MetaWhatsAppRuntimeProperties;
import com.messaging.channel.meta.runtime.MetaWhatsAppWebhookEventStore;
import com.messaging.channel.meta.runtime.MetaWhatsAppWebhookNormalizer;
import com.messaging.channel.meta.runtime.MetaWhatsAppWebhookNormalizer.InvalidPayloadException;
import com.messaging.channel.meta.runtime.MetaWhatsAppWebhookSecrets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/webhooks/meta/whatsapp")
public class MetaWhatsAppWebhookController {
  private final MetaWhatsAppWebhookSecrets secrets;
  private final MetaWhatsAppRuntimeProperties runtimeProperties;
  private final MetaWhatsAppWebhookNormalizer normalizer;
  private final MetaWhatsAppWebhookEventStore eventStore;

  public MetaWhatsAppWebhookController(
      MetaWhatsAppWebhookSecrets secrets,
      MetaWhatsAppRuntimeProperties runtimeProperties,
      MetaWhatsAppWebhookNormalizer normalizer,
      MetaWhatsAppWebhookEventStore eventStore) {
    this.secrets = secrets;
    this.runtimeProperties = runtimeProperties;
    this.normalizer = normalizer;
    this.eventStore = eventStore;
  }

  @GetMapping
  public ResponseEntity<String> verify(
      @RequestParam(name = "hub.mode", required = false) String mode,
      @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
      @RequestParam(name = "hub.challenge", required = false) String challenge) {
    if ("subscribe".equals(mode)
        && hasText(secrets.verifyToken())
        && constantTimeEquals(secrets.verifyToken(), value(verifyToken))) {
      return ResponseEntity.ok(value(challenge));
    }
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Webhook verification failed");
  }

  @PostMapping
  public ResponseEntity<String> receive(
      HttpServletRequest request,
      @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
    long maxBytes = runtimeProperties.getWebhookMaxBody().toBytes();
    long contentLength = request.getContentLengthLong();
    if (contentLength > maxBytes) {
      return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body("Payload too large");
    }
    try {
      int boundedRead = Math.toIntExact(Math.min((long) Integer.MAX_VALUE - 1, maxBytes + 1));
      byte[] exactBody = request.getInputStream().readNBytes(boundedRead);
      return receiveExactBytes(
          exactBody, signature, contentLength < 0 ? null : contentLength);
    } catch (IOException exception) {
      return ResponseEntity.badRequest().body("Invalid webhook payload");
    }
  }

  ResponseEntity<String> receiveExactBytes(
      byte[] body, String signature, Long contentLength) {
    byte[] exactBody = body == null ? new byte[0] : body;
    long maxBytes = runtimeProperties.getWebhookMaxBody().toBytes();
    if ((contentLength != null && contentLength > maxBytes) || exactBody.length > maxBytes) {
      return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body("Payload too large");
    }

    if (!validSignature(exactBody, signature)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid webhook signature");
    }

    try {
      eventStore.persist(normalizer.normalize(exactBody));
      return ResponseEntity.ok("EVENT_RECEIVED");
    } catch (InvalidPayloadException exception) {
      return ResponseEntity.badRequest().body("Invalid webhook payload");
    } catch (DataAccessException exception) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body("Webhook temporarily unavailable");
    }
  }

  private boolean validSignature(byte[] body, String signature) {
    if (!hasText(secrets.appSecret())
        || !hasText(signature)
        || !signature.startsWith("sha256=")) {
      return false;
    }
    try {
      String hex = signature.substring("sha256=".length());
      if (hex.length() != 64) return false;
      byte[] supplied = HexFormat.of().parseHex(hex);
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              secrets.appSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return MessageDigest.isEqual(mac.doFinal(body), supplied);
    } catch (Exception exception) {
      return false;
    }
  }

  private boolean constantTimeEquals(String left, String right) {
    return MessageDigest.isEqual(
        left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String value(String value) {
    return value == null ? "" : value;
  }
}
