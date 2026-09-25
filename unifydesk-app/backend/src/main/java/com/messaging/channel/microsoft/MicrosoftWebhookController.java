package com.messaging.channel.microsoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.microsoft.MicrosoftSubscriptionService.ChangeNotification;
import com.messaging.channel.microsoft.MicrosoftSubscriptionService.NotificationProcessResult;
import com.messaging.observability.webhook.WebhookCallbackAuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/microsoft")
@RequiredArgsConstructor
public class MicrosoftWebhookController {

  private static final String VALIDATION_CALLBACK = "MICROSOFT_VALIDATION";
  private static final String NOTIFICATION_CALLBACK = "MICROSOFT_CHANGE_NOTIFICATION";

  private final MicrosoftSubscriptionService subscriptionService;
  private final WebhookCallbackAuditService webhookAudit;
  private final ObjectMapper objectMapper;

  @PostMapping(
      params = "validationToken",
      consumes = MediaType.TEXT_PLAIN_VALUE,
      produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> validate(
      @RequestParam String validationToken,
      @RequestHeader HttpHeaders headers,
      HttpServletRequest request) {
    Long callbackId = auditReceived(VALIDATION_CALLBACK, request, headers, null);
    audit(
        () ->
            webhookAudit.markProcessed(
                callbackId,
                null,
                null,
                Map.of("validation", "accepted"),
                HttpStatus.OK.value()));
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(validationToken);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> notifications(
      @RequestHeader HttpHeaders headers,
      @RequestBody(required = false) String rawPayload,
      HttpServletRequest request) {
    Long callbackId = auditReceived(NOTIFICATION_CALLBACK, request, headers, rawPayload);

    if (rawPayload == null || rawPayload.isBlank()) {
      audit(
          () ->
              webhookAudit.markIgnored(
                  callbackId,
                  null,
                  null,
                  "Empty Microsoft notification payload",
                  HttpStatus.ACCEPTED.value()));
      return ResponseEntity.accepted().build();
    }

    NotificationEnvelope envelope;
    try {
      envelope = objectMapper.readValue(rawPayload, NotificationEnvelope.class);
    } catch (Exception exception) {
      audit(
          () ->
              webhookAudit.markFailed(
                  callbackId,
                  null,
                  null,
                  null,
                  "Invalid Microsoft notification payload: " + exception.getMessage(),
                  HttpStatus.BAD_REQUEST.value()));
      return ResponseEntity.badRequest().build();
    }

    List<ChangeNotification> notifications =
        envelope.value() == null ? List.of() : envelope.value();
    String eventId =
        notifications.stream()
            .filter(item -> item != null && item.subscriptionId() != null)
            .map(ChangeNotification::subscriptionId)
            .findFirst()
            .orElse(null);

    try {
      NotificationProcessResult result = subscriptionService.processNotifications(notifications);
      audit(
          () ->
              webhookAudit.markProcessed(
                  callbackId,
                  eventId,
                  envelope,
                  result,
                  HttpStatus.ACCEPTED.value()));
      return ResponseEntity.accepted().build();
    } catch (Exception exception) {
      log.error("microsoft_webhook_processing_failed eventId={}", eventId, exception);
      audit(
          () ->
              webhookAudit.markFailed(
                  callbackId,
                  eventId,
                  envelope,
                  null,
                  exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                  HttpStatus.INTERNAL_SERVER_ERROR.value()));
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  private Long auditReceived(
      String callbackType,
      HttpServletRequest request,
      HttpHeaders headers,
      String rawPayload) {
    try {
      return webhookAudit.recordReceived(
          ProviderType.MICROSOFT,
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
          "webhook_callback_audit_failed provider=MICROSOFT stage=received reason={}",
          exception.getClass().getSimpleName());
      return null;
    }
  }

  private void audit(Runnable action) {
    try {
      action.run();
    } catch (Exception exception) {
      log.warn(
          "webhook_callback_audit_failed provider=MICROSOFT stage=complete reason={}",
          exception.getClass().getSimpleName());
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record NotificationEnvelope(List<ChangeNotification> value) {}
}
