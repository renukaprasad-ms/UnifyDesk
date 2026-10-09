package com.messaging.emailprovider.mailbox.compose;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.product.console.ConsoleAuthFilter;
import com.messaging.emailprovider.product.console.ConsoleAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Bearer-session-only draft endpoints, never public API-key endpoints. */
@Validated
@RestController
@ConditionalOnProperty(prefix = "app.mail.compose", name = "enabled", havingValue = "true")
@RequestMapping("/console/v1/mail/mailboxes/{mailboxId}/drafts")
public class MailComposeController {
  private final MailComposeService compose;

  public MailComposeController(MailComposeService compose) {
    this.compose=compose;
  }

  public record DraftRequest(
      @Size(max=20) List<String> to,
      @Size(max=20) List<String> cc,
      @Size(max=20) List<String> bcc,
      @Size(max=998) String subject,
      @Size(max=65536) String bodyText,
      String replyToMessageId) {
    MailComposeService.DraftInput toInput() {
      return new MailComposeService.DraftInput(to,cc,bcc,subject,bodyText,replyToMessageId);
    }
  }

  public record UpdateRequest(@NotNull @Positive Long revision,
      @NotNull @Valid DraftRequest draft) {}

  @PostMapping
  public ResponseEntity<ApiResponse<MailComposeService.Draft>> create(
      HttpServletRequest context,@PathVariable @Positive long mailboxId,
      @Valid @RequestBody DraftRequest body) {
    return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(201,
            compose.create(principal(context),mailboxId,body.toInput()),
            "Draft saved; no email was sent"));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<MailComposeService.DraftSummary>>> list(
      HttpServletRequest context,@PathVariable @Positive long mailboxId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(200,compose.list(principal(context),mailboxId),
            "Your drafts"));
  }

  @GetMapping("/{draftId}")
  public ResponseEntity<ApiResponse<MailComposeService.Draft>> get(
      HttpServletRequest context,@PathVariable @Positive long mailboxId,
      @PathVariable @Positive long draftId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(200,compose.get(principal(context),mailboxId,draftId),
            "Draft details"));
  }

  @PutMapping("/{draftId}")
  public ResponseEntity<ApiResponse<MailComposeService.Draft>> update(
      HttpServletRequest context,@PathVariable @Positive long mailboxId,
      @PathVariable @Positive long draftId,@Valid @RequestBody UpdateRequest body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(200,compose.update(principal(context),mailboxId,
            draftId,body.revision(),body.draft().toInput()),"Draft saved"));
  }

  @PostMapping("/{draftId}/hold")
  public ResponseEntity<ApiResponse<MailComposeService.HeldSubmission>> hold(
      HttpServletRequest context,@PathVariable @Positive long mailboxId,
      @PathVariable @Positive long draftId,
      @RequestHeader(value="Idempotency-Key",required=false) String key) {
    if(key==null || key.isBlank()) throw new BadRequestException("Idempotency-Key required");
    return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(202,
            compose.hold(principal(context),mailboxId,draftId,key),
            "Held for future transport: email has NOT been sent"));
  }

  private static ConsoleAuthService.Principal principal(HttpServletRequest context) {
    return ConsoleAuthFilter.require(context);
  }
}
