package com.messaging.billing.payment;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping({"/api/v1/webhooks/razorpay", "/api/v1/billing/webhooks/razorpay"})
@RequiredArgsConstructor
public class RazorpayWebhookController {

  private final RazorpayWebhookVerifier verifier;
  private final RazorpayWebhookService webhookService;

  @PostMapping
  public ResponseEntity<Void> receive(
      @RequestBody byte[] rawBody,
      @RequestHeader(value = "x-razorpay-signature", required = false) String signature) {
    if (!verifier.verify(rawBody, signature)) {
      log.warn("Invalid signature for Razorpay webhook call");
      return ResponseEntity.status(401).build();
    }
    webhookService.receiveVerified(rawBody, Instant.now());
    return ResponseEntity.ok().build();
  }
}
