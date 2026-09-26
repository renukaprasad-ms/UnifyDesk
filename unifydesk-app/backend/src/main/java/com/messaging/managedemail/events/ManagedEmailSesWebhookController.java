package com.messaging.managedemail.events;

import com.messaging.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/managed-email")
@RequiredArgsConstructor
public class ManagedEmailSesWebhookController {

  private final SnsMessageVerifier verifier;
  private final ManagedEmailProviderEventProcessor processor;

  @PostMapping("/ses")
  public ResponseEntity<Void> ses(@RequestBody byte[] body) {
    try {
      SnsEnvelope envelope = verifier.verify(body);
      processor.process(envelope);
      return ResponseEntity.ok().build();
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Invalid Managed Email provider event");
    }
  }
}
