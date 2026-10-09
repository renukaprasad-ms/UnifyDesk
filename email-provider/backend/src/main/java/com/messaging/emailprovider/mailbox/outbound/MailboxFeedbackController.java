package com.messaging.emailprovider.mailbox.outbound;

import com.messaging.emailprovider.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * INTERNAL only. The existing internal bearer filter AND the dedicated secret
 * are both required. Do not expose this route via a public reverse proxy.
 */
@RestController
@ConditionalOnProperty(prefix="app.mail.feedback",name="enabled",havingValue="true")
@RequestMapping("/internal/v1/mail/outbound")
public class MailboxFeedbackController {
  private final MailFeedbackAuthenticator authenticator;
  private final MailboxDeliveryFeedbackService service;

  public MailboxFeedbackController(MailFeedbackAuthenticator authenticator,
      MailboxDeliveryFeedbackService service) {
    this.authenticator=authenticator;
    this.service=service;
  }

  @PostMapping("/feedback")
  public ResponseEntity<ApiResponse<MailboxDeliveryFeedbackService.Result>> feedback(
      @RequestHeader("X-UnifyDesk-Mail-Feedback-Token") String token,
      @Valid @RequestBody MailboxDeliveryFeedbackService.Event event) {
    authenticator.verify(token);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(200,service.accept(event),
            "Trusted MTA feedback recorded; no message requeued"));
  }
}
