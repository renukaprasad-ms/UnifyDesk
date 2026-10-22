package com.messaging.emailprovider.mailbox;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.product.console.ConsoleAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@ConditionalOnProperty(prefix = "app.mail.inbound", name = "enabled", havingValue = "true")
@RequestMapping("/console/v1/mail/mailboxes/{mailboxId}/access")
public class MailboxAccessController {
  private final MailboxAccessService access;

  public MailboxAccessController(MailboxAccessService access) {
    this.access = access;
  }

  public record NewGrant(
      @NotBlank String userId,
      @jakarta.validation.constraints.NotNull Boolean canRead,
      @jakarta.validation.constraints.NotNull Boolean canSend,
      @jakarta.validation.constraints.NotNull Boolean canDelegate) {}

  @GetMapping
  public ResponseEntity<ApiResponse<List<MailboxAccessService.Grant>>> list(
      HttpServletRequest req, @PathVariable @Positive long mailboxId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                200,
                access.list(ConsoleAuthFilter.require(req), mailboxId),
                "Explicit mailbox permissions"));
  }

  @PutMapping
  public ApiResponse<Void> grant(
      HttpServletRequest req,
      @PathVariable @Positive long mailboxId,
      @Valid @RequestBody NewGrant body) {
    access.grant(
        ConsoleAuthFilter.require(req),
        mailboxId,
        MailboxReadController.parseId(body.userId()),
        body.canRead(),
        body.canSend(),
        body.canDelegate());
    return ApiResponse.success(200, "Mailbox access updated");
  }

  @DeleteMapping("/{userId}")
  public ApiResponse<Void> revoke(
      HttpServletRequest req,
      @PathVariable @Positive long mailboxId,
      @PathVariable @Positive long userId) {
    access.revoke(ConsoleAuthFilter.require(req), mailboxId, userId);
    return ApiResponse.success(200, "Mailbox access revoked");
  }
}
