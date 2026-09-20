package com.messaging.channel.microsoft;

import com.messaging.channel.microsoft.MicrosoftSubscriptionService.ChangeNotification;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/microsoft")
@RequiredArgsConstructor
public class MicrosoftWebhookController {

  private final MicrosoftSubscriptionService subscriptionService;

  @PostMapping(
      params = "validationToken",
      consumes = MediaType.TEXT_PLAIN_VALUE,
      produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> validate(@RequestParam String validationToken) {
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_PLAIN)
        .body(validationToken);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> notifications(
      @RequestBody(required = false) NotificationEnvelope envelope) {
    if (envelope != null && envelope.value() != null && !envelope.value().isEmpty()) {
      subscriptionService.processNotifications(envelope.value());
    }
    return ResponseEntity.accepted().build();
  }

  public record NotificationEnvelope(List<ChangeNotification> value) {}
}
