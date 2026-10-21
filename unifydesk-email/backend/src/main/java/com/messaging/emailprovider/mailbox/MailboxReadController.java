package com.messaging.emailprovider.mailbox;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.product.console.ConsoleAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.io.InputStream;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Session-authenticated mailbox endpoints; never exposed through a project API key. */
@Validated
@RestController
@ConditionalOnProperty(prefix = "app.mail.inbound", name = "enabled", havingValue = "true")
@RequestMapping("/console/v1/mail/mailboxes")
public class MailboxReadController {
  private final MailboxReadService mailbox;

  public MailboxReadController(MailboxReadService mailbox) {
    this.mailbox = mailbox;
  }

  /** Snowflake IDs are strings on the wire to avoid JavaScript precision loss. */
  public record Activate(@NotBlank String userId) {}

  @PostMapping("/{mailboxId}/activate")
  public ApiResponse<MailboxReadService.MailboxView> activate(
      HttpServletRequest req,
      @PathVariable @Positive long mailboxId,
      @Valid @RequestBody Activate body) {
    return ApiResponse.success(
        200,
        mailbox.activate(ConsoleAuthFilter.require(req), mailboxId, parseId(body.userId())),
        "Mailbox activated with explicit owner grant");
  }

  static long parseId(String value) {
    try {
      if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
        throw new NumberFormatException();
      }
      return Long.parseLong(value);
    } catch (NumberFormatException invalid) {
      throw new BadRequestException("Invalid user ID");
    }
  }

  @GetMapping("/{mailboxId}/messages")
  public ApiResponse<List<MailboxReadService.Message>> messages(
      HttpServletRequest req,
      @PathVariable @Positive long mailboxId,
      @RequestParam(defaultValue = "INBOX") String folder,
      @RequestParam(required = false) Long beforeId,
      @RequestParam(defaultValue = "30") int limit) {
    return ApiResponse.success(
        200,
        mailbox.list(ConsoleAuthFilter.require(req), mailboxId, folder, beforeId, limit),
        "Mail messages");
  }

  @GetMapping("/{mailboxId}/messages/{messageId}/preview")
  public ResponseEntity<ApiResponse<MailboxReadService.MessagePreview>> preview(
      HttpServletRequest req,
      @PathVariable @Positive long mailboxId,
      @PathVariable @Positive long messageId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(
            ApiResponse.success(
                200,
                mailbox.preview(ConsoleAuthFilter.require(req), mailboxId, messageId),
                "Safe plain-text message preview"));
  }

  @GetMapping("/{mailboxId}/messages/{messageId}/raw")
  public ResponseEntity<StreamingResponseBody> raw(
      HttpServletRequest req,
      @PathVariable @Positive long mailboxId,
      @PathVariable @Positive long messageId) {
    MailboxReadService.RawMail file =
        mailbox.raw(ConsoleAuthFilter.require(req), mailboxId, messageId);
    StreamingResponseBody body =
        out -> {
          try (InputStream stream = file.body()) {
            stream.transferTo(out);
          }
        };
    return ResponseEntity.status(HttpStatus.OK)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(file.sizeBytes())
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"message.eml\"")
        .header("X-Content-Type-Options", "nosniff")
        .body(body);
  }
}
