package com.messaging.channel.gmail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.observability.webhook.WebhookCallbackAuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/google/gmail")
@RequiredArgsConstructor
public class GmailPubSubWebhookController {

  private static final String CALLBACK_TYPE = "GMAIL_PUBSUB";

  private final GmailOAuthProperties properties;
  private final GmailRealtimeStateService stateService;
  private final ChannelConnectionRepository connectionRepository;
  private final ObjectMapper objectMapper;
  private final WebhookCallbackAuditService webhookAudit;

  @PostMapping
  public ResponseEntity<Void> receive(
      @RequestParam(required = false) String token,
      @RequestHeader HttpHeaders headers,
      @RequestBody(required = false) String rawPayload,
      HttpServletRequest request) {
    Long callbackId =
        auditReceived(
            request,
            headers,
            rawPayload);

    if (!properties.realtimeConfigured()
        || !secureEquals(token, properties.getPubsubPushVerificationToken())) {
      audit(
          () ->
              webhookAudit.markRejected(
                  callbackId, null, "Webhook verification failed", HttpStatus.FORBIDDEN.value()));
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    if (rawPayload == null || rawPayload.isBlank()) {
      audit(
          () ->
              webhookAudit.markIgnored(
                  callbackId, null, null, "Empty Pub/Sub payload", HttpStatus.NO_CONTENT.value()));
      return ResponseEntity.noContent().build();
    }

    PubSubEnvelope envelope;
    try {
      envelope = objectMapper.readValue(rawPayload, PubSubEnvelope.class);
    } catch (Exception exception) {
      log.warn("gmail_pubsub_invalid_envelope reason={}", exception.getClass().getSimpleName());
      audit(
          () ->
              webhookAudit.markFailed(
                  callbackId,
                  null,
                  null,
                  null,
                  "Invalid Pub/Sub envelope: " + exception.getMessage(),
                  HttpStatus.NO_CONTENT.value()));
      // Ack malformed provider payloads so Pub/Sub does not retry them forever.
      return ResponseEntity.noContent().build();
    }

    String eventId = envelope.message() == null ? null : envelope.message().messageId();
    if (envelope.message() == null
        || envelope.message().data() == null
        || envelope.message().data().isBlank()) {
      audit(
          () ->
              webhookAudit.markIgnored(
                  callbackId,
                  eventId,
                  null,
                  "Pub/Sub message data was empty",
                  HttpStatus.NO_CONTENT.value()));
      return ResponseEntity.noContent().build();
    }

    GmailPushData pushData;
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(envelope.message().data());
      pushData = objectMapper.readValue(decoded, GmailPushData.class);
    } catch (Exception exception) {
      log.warn(
          "gmail_pubsub_invalid_payload messageId={} reason={}",
          eventId,
          exception.getClass().getSimpleName());
      audit(
          () ->
              webhookAudit.markFailed(
                  callbackId,
                  eventId,
                  null,
                  null,
                  "Invalid Gmail Pub/Sub data: " + exception.getMessage(),
                  HttpStatus.NO_CONTENT.value()));
      // Ack malformed provider payloads so Pub/Sub does not retry them forever.
      return ResponseEntity.noContent().build();
    }

    if (pushData.emailAddress() == null
        || pushData.emailAddress().isBlank()
        || pushData.historyId() == null
        || pushData.historyId().isBlank()) {
      audit(
          () ->
              webhookAudit.markFailed(
                  callbackId,
                  eventId,
                  pushData,
                  null,
                  "Decoded Gmail payload is missing emailAddress or historyId",
                  HttpStatus.NO_CONTENT.value()));
      return ResponseEntity.noContent().build();
    }

    try {
      List<ChannelConnection> connections =
          connectionRepository.findByProviderTypeAndExternalAccountIdIgnoreCaseAndStatus(
              ProviderType.GMAIL,
              pushData.emailAddress().trim(),
              ChannelConnectionStatus.ACTIVE);

      int enqueued = 0;
      for (ChannelConnection connection : connections) {
        stateService.ensureState(connection);
        if (stateService.enqueueNotification(connection.getId(), pushData.historyId())) {
          enqueued++;
        }
      }

      Map<String, Object> result =
          Map.of(
              "matchedConnections", connections.size(),
              "enqueued", enqueued,
              "subscription", envelope.subscription() == null ? "" : envelope.subscription());

      log.debug(
          "gmail_pubsub_received account={} historyId={} matchedConnections={} enqueued={}",
          pushData.emailAddress(),
          pushData.historyId(),
          connections.size(),
          enqueued);

      audit(
          () ->
              webhookAudit.markProcessed(
                  callbackId, eventId, pushData, result, HttpStatus.NO_CONTENT.value()));
      return ResponseEntity.noContent().build();
    } catch (Exception exception) {
      log.error(
          "gmail_pubsub_processing_failed messageId={} account={} historyId={}",
          eventId,
          pushData.emailAddress(),
          pushData.historyId(),
          exception);
      audit(
          () ->
              webhookAudit.markFailed(
                  callbackId,
                  eventId,
                  pushData,
                  null,
                  exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                  HttpStatus.INTERNAL_SERVER_ERROR.value()));
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  private Long auditReceived(
      HttpServletRequest request, HttpHeaders headers, String rawPayload) {
    try {
      return webhookAudit.recordReceived(
          ProviderType.GMAIL,
          CALLBACK_TYPE,
          request.getRequestURI(),
          request.getMethod(),
          request.getQueryString(),
          request.getRemoteAddr(),
          headers.getFirst(HttpHeaders.CONTENT_TYPE),
          headers,
          rawPayload);
    } catch (Exception exception) {
      log.warn(
          "webhook_callback_audit_failed provider=GMAIL stage=received reason={}",
          exception.getClass().getSimpleName());
      return null;
    }
  }

  private void audit(Runnable action) {
    try {
      action.run();
    } catch (Exception exception) {
      log.warn(
          "webhook_callback_audit_failed provider=GMAIL stage=complete reason={}",
          exception.getClass().getSimpleName());
    }
  }

  private boolean secureEquals(String provided, String expected) {
    if (provided == null || expected == null) return false;
    return MessageDigest.isEqual(
        provided.getBytes(StandardCharsets.UTF_8),
        expected.getBytes(StandardCharsets.UTF_8));
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PubSubEnvelope(PubSubMessage message, String subscription) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PubSubMessage(
      String data,
      @JsonProperty("messageId") String messageId,
      @JsonProperty("publishTime") String publishTime) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GmailPushData(String emailAddress, String historyId) {}
}
