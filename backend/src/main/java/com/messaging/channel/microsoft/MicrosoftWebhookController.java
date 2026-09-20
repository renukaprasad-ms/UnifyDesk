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

  @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> webhook(
      @RequestParam(required = false) String validationToken,
      @RequestBody(required = false) NotificationEnvelope envelope) {
    if (validationToken != null) {
      return ResponseEntity.ok()
          .contentType(MediaType.TEXT_PLAIN)
          .body(validationToken);
    }

    if (envelope != null) {
      subscriptionService.processNotifications(envelope.value());
    }
    return ResponseEntity.accepted().body("");
  }

  public record NotificationEnvelope(List<ChangeNotification> value) {}
}
