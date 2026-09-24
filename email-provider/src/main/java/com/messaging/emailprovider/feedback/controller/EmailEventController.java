package com.messaging.emailprovider.feedback.controller;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.feedback.EmailEventQueryService;
import com.messaging.emailprovider.feedback.dto.EmailEventResponse;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/companies/{companyId}/emails/{messageId}/events")
public class EmailEventController {

  private final EmailEventQueryService service;

  public EmailEventController(EmailEventQueryService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<EmailEventResponse>> list(
      @PathVariable @Positive Long companyId, @PathVariable @Positive Long messageId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), service.list(companyId, messageId), "Email events");
  }
}
