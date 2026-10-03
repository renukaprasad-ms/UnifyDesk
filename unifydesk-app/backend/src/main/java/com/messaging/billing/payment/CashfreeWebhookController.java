package com.messaging.billing.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/cashfree")
@RequiredArgsConstructor
public class CashfreeWebhookController {

  private final CashfreeWebhookVerifier verifier;
  private final CashfreeWebhookService webhookService;

  @PostMapping
  public ResponseEntity<Void> receive(
      @RequestBody byte[] rawBody,
      @RequestHeader(value = "x-webhook-timestamp", required = false) String timestamp,
      @RequestHeader(value = "x-webhook-signature", required = false) String signature) {
    if (!verifier.verify(rawBody, timestamp, signature)) {
      return ResponseEntity.status(401).build();
    }
    webhookService.receive(rawBody);
    return ResponseEntity.ok().build();
  }
}
