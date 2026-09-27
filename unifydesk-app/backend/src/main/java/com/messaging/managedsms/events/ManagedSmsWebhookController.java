package com.messaging.managedsms.events;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/managed-sms")
@RequiredArgsConstructor
public class ManagedSmsWebhookController {

  private final ManagedSmsEventInboxService inboxService;

  @PostMapping(path = "/msg91", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> msg91(
      @RequestBody byte[] body,
      @RequestHeader(value = "X-UnifyDesk-Managed-Sms-Secret", required = false) String secret) {
    inboxService.receive(body, secret);
    return ResponseEntity.ok().build();
  }
}
