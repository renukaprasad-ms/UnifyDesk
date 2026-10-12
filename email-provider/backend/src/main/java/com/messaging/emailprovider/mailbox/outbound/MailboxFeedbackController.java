package com.messaging.emailprovider.mailbox.outbound;

import com.messaging.emailprovider.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import com.messaging.emailprovider.common.exception.BadRequestException;
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
  private final MailboxDsnProcessor dsn;

  public MailboxFeedbackController(MailFeedbackAuthenticator authenticator,
      MailboxDeliveryFeedbackService service,MailboxDsnProcessor dsn) {
    this.authenticator=authenticator;
    this.service=service;
    this.dsn=dsn;
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

  /**
   * Only the trusted local Postfix bounce adapter may call this endpoint.
   * Reads at most 64 KiB + 1 byte even when Content-Length is missing.
   */
  @PostMapping(value="/dsn",consumes={"message/rfc822","text/plain","application/octet-stream"})
  public ResponseEntity<ApiResponse<MailboxDeliveryFeedbackService.Result>> dsn(
      @RequestHeader("X-UnifyDesk-Mail-Feedback-Token") String token,
      @RequestHeader("X-UnifyDesk-Envelope-To") String returnPath,
      HttpServletRequest request) throws IOException {
    authenticator.verify(token);
    byte[] raw=request.getInputStream().readNBytes(65537);
    if(raw.length==0 || raw.length>65536) {
      throw new BadRequestException("DSN payload must be between 1 and 65536 bytes");
    }
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(200,dsn.receive(returnPath,raw),
            "Authenticated DSN correlated; no message requeued"));
  }
}
