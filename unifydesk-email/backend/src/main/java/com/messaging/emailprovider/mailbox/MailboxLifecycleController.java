package com.messaging.emailprovider.mailbox;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.product.console.ConsoleAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@ConditionalOnProperty(prefix = "app.mail.inbound", name = "enabled", havingValue = "true")
@RequestMapping("/console/v1/mail/mailboxes/{mailboxId}/messages/{messageId}")
public class MailboxLifecycleController {
  private final MailboxLifecycleService lifecycle;

  public MailboxLifecycleController(MailboxLifecycleService lifecycle) {
    this.lifecycle = lifecycle;
  }

  public record Move(@NotBlank String folder) {}

  @PostMapping("/move")
  public ApiResponse<Void> move(
      HttpServletRequest req,
      @PathVariable @Positive long mailboxId,
      @PathVariable @Positive long messageId,
      @Valid @RequestBody Move body) {
    lifecycle.move(ConsoleAuthFilter.require(req), mailboxId, messageId, body.folder());
    return ApiResponse.success(200, "Message moved");
  }

  @PostMapping("/restore")
  public ApiResponse<Void> restore(
      HttpServletRequest req,
      @PathVariable @Positive long mailboxId,
      @PathVariable @Positive long messageId) {
    lifecycle.restore(ConsoleAuthFilter.require(req), mailboxId, messageId);
    return ApiResponse.success(200, "Message restored");
  }
}
