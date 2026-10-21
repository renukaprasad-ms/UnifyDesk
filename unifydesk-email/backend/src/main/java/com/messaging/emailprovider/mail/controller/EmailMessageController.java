package com.messaging.emailprovider.mail.controller;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.mail.dto.EmailAcceptedResponse;
import com.messaging.emailprovider.mail.dto.EmailMessageResponse;
import com.messaging.emailprovider.mail.dto.EmailRecipientResponse;
import com.messaging.emailprovider.mail.dto.SendEmailRequest;
import com.messaging.emailprovider.mail.service.EmailMessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/companies/{companyId}/emails")
public class EmailMessageController {

  private final EmailMessageService service;

  public EmailMessageController(EmailMessageService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<EmailAcceptedResponse>> send(
      @PathVariable @Positive Long companyId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody SendEmailRequest request) {
    EmailAcceptedResponse response = service.accept(companyId, idempotencyKey, request);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(HttpStatus.ACCEPTED.value(), response, "Email accepted"));
  }

  @GetMapping("/{messageId}")
  public ApiResponse<EmailMessageResponse> get(
      @PathVariable @Positive Long companyId, @PathVariable @Positive Long messageId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), service.get(companyId, messageId), "Email message");
  }

  @GetMapping("/{messageId}/recipients")
  public ApiResponse<List<EmailRecipientResponse>> recipients(
      @PathVariable @Positive Long companyId, @PathVariable @Positive Long messageId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), service.recipients(companyId, messageId), "Email recipients");
  }
}
