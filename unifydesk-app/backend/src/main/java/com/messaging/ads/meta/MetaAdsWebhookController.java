package com.messaging.ads.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/meta/ads")
@RequiredArgsConstructor
public class MetaAdsWebhookController {

  private final MetaAdsProperties properties;
  private final ObjectMapper objectMapper;

  @GetMapping
  public ResponseEntity<String> verify(
      @RequestParam(name = "hub.mode", required = false) String mode,
      @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
      @RequestParam(name = "hub.challenge", required = false) String challenge) {
    if ("subscribe".equals(mode)
        && hasText(properties.getWebhookVerifyToken())
        && secureEquals(properties.getWebhookVerifyToken(), verifyToken)) {
      return ResponseEntity.ok(value(challenge));
    }
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Webhook verification failed");
  }

  @PostMapping
  public ResponseEntity<String> receive(
      @RequestBody(required = false) String body,
      @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
    String payload = value(body);
    if (!validSignature(payload, signature)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid webhook signature");
    }

    try {
      JsonNode decoded = objectMapper.readTree(payload);
      int entries = decoded.path("entry").isArray() ? decoded.path("entry").size() : 0;
      log.info("meta_ads_webhook_received entries={}", entries);
      return ResponseEntity.ok("EVENT_RECEIVED");
    } catch (Exception exception) {
      log.warn("meta_ads_webhook_invalid_json reason={}", exception.getClass().getSimpleName());
      return ResponseEntity.badRequest().body("Invalid webhook payload");
    }
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

  private boolean secureEquals(String expected, String supplied) {
    return MessageDigest.isEqual(
        value(expected).getBytes(StandardCharsets.UTF_8),
        value(supplied).getBytes(StandardCharsets.UTF_8));
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String value(String value) {
    return value == null ? "" : value;
  }
}
