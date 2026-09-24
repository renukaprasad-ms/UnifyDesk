package com.messaging.emailprovider.feedback.controller;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.feedback.FeedbackAuthenticator;
import com.messaging.emailprovider.feedback.FeedbackService;
import com.messaging.emailprovider.feedback.dto.FeedbackResultResponse;
import com.messaging.emailprovider.feedback.dto.PostfixFeedbackRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/feedback")
@ConditionalOnProperty(prefix = "app.feedback", name = "enabled", havingValue = "true")
public class FeedbackController {

  private final FeedbackAuthenticator authenticator;
  private final FeedbackService service;

  public FeedbackController(FeedbackAuthenticator authenticator, FeedbackService service) {
    this.authenticator = authenticator;
    this.service = service;
  }

  @PostMapping("/postfix")
  public ApiResponse<FeedbackResultResponse> postfix(
      @RequestHeader("X-Reachlyo-Feedback-Token") String token,
      @Valid @RequestBody PostfixFeedbackRequest request) {
    authenticator.verify(token);
    return ApiResponse.success(
        HttpStatus.OK.value(), service.apply(request, null), "Feedback processed");
  }
}
