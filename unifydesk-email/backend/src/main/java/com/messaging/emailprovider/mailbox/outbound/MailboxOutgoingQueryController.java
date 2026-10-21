package com.messaging.emailprovider.mailbox.outbound;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.product.console.ConsoleAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** User-scoped SMTP handoff status. No marketing API key access. */
@Validated
@RestController
@ConditionalOnProperty(prefix = "app.mail.compose", name = "enabled", havingValue = "true")
@RequestMapping("/console/v1/mail/mailboxes/{mailboxId}/outgoing")
public class MailboxOutgoingQueryController {
  private final MailboxOutgoingQueryService query;

  public MailboxOutgoingQueryController(MailboxOutgoingQueryService query) {
    this.query = query;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<MailboxOutgoingQueryService.Outgoing>>> list(
      HttpServletRequest context, @PathVariable @Positive long mailboxId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                200,
                query.list(ConsoleAuthFilter.require(context), mailboxId),
                "Outgoing handoff status"));
  }

  @GetMapping("/{intentId}/recipients")
  public ResponseEntity<ApiResponse<List<MailboxOutgoingQueryService.Recipient>>> recipients(
      HttpServletRequest context,
      @PathVariable @Positive long mailboxId,
      @PathVariable @Positive long intentId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                200,
                query.recipients(ConsoleAuthFilter.require(context), mailboxId, intentId),
                "Recipient handoff state"));
  }
}
