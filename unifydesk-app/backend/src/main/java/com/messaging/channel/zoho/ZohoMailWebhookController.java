package com.messaging.channel.zoho;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/zoho-mail")
@RequiredArgsConstructor
public class ZohoMailWebhookController {

  private final ZohoMailWebhookService webhookService;

  @PostMapping("/{connectionId}")
  public ResponseEntity<Void> receive(
      @PathVariable Long connectionId,
      @RequestParam(required = false) String token,
      @RequestHeader(name = "X-Hook-Signature", required = false) String signature,
      @RequestHeader(name = "X-Hook-Secret", required = false) String initialSecret,
      @RequestBody(required = false) byte[] body) {
    try {
      if (!webhookService.authenticateAndQueue(
          connectionId, token, signature, initialSecret, body)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
      // Zoho requires HTTP 200 during initial registration. Polling is async.
      return ResponseEntity.ok().build();
    } catch (RuntimeException exception) {
      log.warn("zoho_webhook_reception_failed connectionId={} reason={}",
          connectionId, exception.getClass().getSimpleName());
      // Retryable failure. Never acknowledge an event that was not persisted.
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
  }
}
