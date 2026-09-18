package com.messaging.channel.meta;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/meta/whatsapp")
@RequiredArgsConstructor
public class MetaWhatsAppWebhookController {

  private final MetaWhatsAppProperties properties;

  @GetMapping
  public ResponseEntity<String> verify(
      @RequestParam(name = "hub.mode", required = false) String mode,
      @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
      @RequestParam(name = "hub.challenge", required = false) String challenge) {
    if ("subscribe".equals(mode)
        && hasText(properties.getWebhookVerifyToken())
        && MessageDigest.isEqual(
            properties.getWebhookVerifyToken().getBytes(StandardCharsets.UTF_8),
            value(verifyToken).getBytes(StandardCharsets.UTF_8))) {
      return ResponseEntity.ok(value(challenge));
    }
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Webhook verification failed");
  }

  @PostMapping
  public ResponseEntity<String> receive(
      @RequestBody String body,
      @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
    if (!validSignature(body, signature)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid webhook signature");
    }

    // Payload parsing, idempotency, and inbox ingestion are implemented in the webhook phase.
    return ResponseEntity.ok("EVENT_RECEIVED");
  }

  private boolean validSignature(String body, String signature) {
    if (!hasText(properties.getAppSecret())
        || !hasText(signature)
        || !signature.startsWith("sha256=")) {
      return false;
    }

    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              properties.getAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] expected = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      byte[] supplied = HexFormat.of().parseHex(signature.substring("sha256=".length()));
      return MessageDigest.isEqual(expected, supplied);
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      return false;
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String value(String value) {
    return value == null ? "" : value;
  }
}
