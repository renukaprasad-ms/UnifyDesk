package com.messaging.emailprovider.product.unsubscribe;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.mail.repository.EmailMessageRepository;
import com.messaging.emailprovider.mail.repository.EmailRecipientRepository;
import com.messaging.emailprovider.suppression.SuppressionReason;
import com.messaging.emailprovider.suppression.SuppressionService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/unsubscribe/{token}")
public class EmailUnsubscribeController {
  private final UnsubscribeTokenService tokens;
  private final EmailMessageRepository messages;
  private final EmailRecipientRepository recipients;
  private final SuppressionService suppression;

  public EmailUnsubscribeController(
      UnsubscribeTokenService tokens,
      EmailMessageRepository messages,
      EmailRecipientRepository recipients,
      SuppressionService suppression) {
    this.tokens = tokens;
    this.messages = messages;
    this.recipients = recipients;
    this.suppression = suppression;
  }

  @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> confirm(@PathVariable String token) {
    tokens.verify(token);
    // GET never unsubscribes: preview bots and mail scanners follow links.
    String content =
        "<!doctype html><html lang='en'><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>Unsubscribe — UnifyDesk Email</title></head><body>"
            + "<main style='font-family:sans-serif;margin:10vh auto;max-width:470px;padding:25px'>"
            + "<h1>Unsubscribe</h1><p>Stop marketing email from this sender?</p>"
            + "<form method='post' action='/v1/unsubscribe/"
            + token
            + "'>"
            + "<button type='submit'>Confirm unsubscribe</button></form></main></body></html>";
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            "Content-Security-Policy",
            "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'")
        .contentType(MediaType.TEXT_HTML)
        .body(content);
  }

  @PostMapping
  @Transactional
  public ApiResponse<Void> unsubscribe(@PathVariable String token) {
    var payload = tokens.verify(token);
    var message = messages.findById(payload.messageId()).orElse(null);
    var recipient = recipients.findById(payload.recipientId()).orElse(null);
    if (message == null
        || recipient == null
        || !message.isMarketing()
        || !recipient.getMessageId().equals(message.getId())) {
      throw new BadRequestException("Invalid unsubscribe link");
    }
    suppression.suppressIfAbsent(
        message.getCompanyId(), recipient.getRecipientAddress(), SuppressionReason.UNSUBSCRIBE);
    return ApiResponse.success(200, "Unsubscribed from marketing email");
  }
}
