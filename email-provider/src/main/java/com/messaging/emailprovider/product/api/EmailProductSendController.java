package com.messaging.emailprovider.product.api;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.mail.dto.EmailAcceptedResponse;
import com.messaging.emailprovider.mail.dto.EmailMessageResponse;
import com.messaging.emailprovider.mail.dto.SendEmailRequest;
import com.messaging.emailprovider.mail.service.EmailMessageService;
import com.messaging.emailprovider.product.auth.EmailProductKeyFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/v1/emails")
public class EmailProductSendController {
  private final EmailMessageService emails;

  public EmailProductSendController(EmailMessageService emails) {
    this.emails = emails;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<EmailAcceptedResponse>> send(
      HttpServletRequest context,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody SendEmailRequest request) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BadRequestException("Idempotency-Key is required for public API sends");
    }
    Long projectId = EmailProductKeyFilter.require(context).projectId();
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(202, emails.accept(projectId, idempotencyKey, request),
            "Email accepted for asynchronous delivery"));
  }

  @GetMapping("/{messageId}")
  public ApiResponse<EmailMessageResponse> get(HttpServletRequest context,
      @PathVariable @Positive Long messageId) {
    Long projectId = EmailProductKeyFilter.require(context).projectId();
    return ApiResponse.success(200, emails.get(projectId, messageId), "Email details");
  }
}
