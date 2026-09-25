package com.messaging.channel.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.enums.ProviderType;
import com.messaging.observability.webhook.WebhookCallbackAuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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
@RequestMapping("/api/v1/webhooks/meta/whatsapp")
@RequiredArgsConstructor
public class MetaWhatsAppWebhookController {

  private static final String VERIFY_CALLBACK = "WHATSAPP_VERIFICATION";
  private static final String EVENT_CALLBACK = "WHATSAPP_EVENT";

  private final MetaWhatsAppProperties properties;
  private final WebhookCallbackAuditService webhookAudit;
  private final ObjectMapper objectMapper;

  @GetMapping
  public ResponseEntity<String> verify(
      @RequestParam(name = "hub.mode", required = false) String mode,
      @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
      @RequestParam(name = "hub.challenge", required = false) String challenge,
      @RequestHeader HttpHeaders headers,
      HttpServletRequest request) {
    Long callbackId = auditReceived(VERIFY_CALLBACK, request, headers, null);
    if ("subscribe".equals(mode)
        && hasText(properties.getWebhookVerifyToken())
        && MessageDigest.isEqual(
            properties.getWebhookVerifyToken().getBytes(StandardCharsets.UTF_8),
            value(verifyToken).getBytes(StandardCharsets.UTF_8))) {
      audit(
          () ->
              webhookAudit.markProcessed(
                  callbackId,
                  null,
                  null,
                  Map.of("verification", "accepted"),
                  HttpStatus.OK.value()));
      return ResponseEntity.ok(value(challenge));
    }

    audit(
        () ->
            webhookAudit.markRejected(
                callbackId,
                null,
                "Webhook verification failed",
                HttpStatus.FORBIDDEN.value()));
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Webhook verification failed");
  }

  @PostMapping
  public ResponseEntity<String> receive(
      @RequestBody(required = false) String body,
      @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
      @RequestHeader HttpHeaders headers,
      HttpServletRequest request) {
    Long callbackId = auditReceived(EVENT_CALLBACK, request, headers, body);

    if (!validSignature(value(body), signature)) {
      audit(
          () ->
              webhookAudit.markRejected(
                  callbackId,
                  null,
                  "Invalid webhook signature",
                  HttpStatus.UNAUTHORIZED.value()));
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid webhook signature");
    }

    JsonNode decodedPayload;
    try {
      decodedPayload = objectMapper.readTree(value(body));
    } catch (Exception exception) {
      audit(
          () ->
              webhookAudit.markFailed(
                  callbackId,
                  null,
                  null,
                  null,
                  "Invalid WhatsApp webhook JSON: " + exception.getMessage(),
                  HttpStatus.BAD_REQUEST.value()));
      return ResponseEntity.badRequest().body("Invalid webhook payload");
    }

    Map<String, Object> result =
        Map.of(
            "entries", decodedPayload.path("entry").isArray() ? decodedPayload.path("entry").size() : 0,
            "accepted", true);

    // Payload-specific inbox ingestion remains provider-specific; all inbound callbacks are
    // audited here so platform admins can inspect delivery before that processing is expanded.
    audit(
        () ->
            webhookAudit.markProcessed(
                callbackId,
                null,
                decodedPayload,
                result,
                HttpStatus.OK.value()));
    return ResponseEntity.ok("EVENT_RECEIVED");
  }

  private Long auditReceived(
      String callbackType,
      HttpServletRequest request,
      HttpHeaders headers,
      String rawPayload) {
    try {
      return webhookAudit.recordReceived(
          ProviderType.META_WHATSAPP,
          callbackType,
          request.getRequestURI(),
          request.getMethod(),
          request.getQueryString(),
          request.getRemoteAddr(),
          headers.getFirst(HttpHeaders.CONTENT_TYPE),
          headers,
          rawPayload);
    } catch (Exception exception) {
      log.warn(
          "webhook_callback_audit_failed provider=META_WHATSAPP stage=received reason={}",
          exception.getClass().getSimpleName());
      return null;
    }
  }

  private void audit(Runnable action) {
    try {
      action.run();
    } catch (Exception exception) {
      log.warn(
          "webhook_callback_audit_failed provider=META_WHATSAPP stage=complete reason={}",
          exception.getClass().getSimpleName());
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

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String value(String value) {
    return value == null ? "" : value;
  }
}
