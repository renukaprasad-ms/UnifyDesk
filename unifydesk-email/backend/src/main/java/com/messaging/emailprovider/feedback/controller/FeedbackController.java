package com.messaging.emailprovider.feedback.controller;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.feedback.DsnFeedbackParser;
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
  private final DsnFeedbackParser dsnFeedbackParser;

  public FeedbackController(
      FeedbackAuthenticator authenticator,
      FeedbackService service,
      DsnFeedbackParser dsnFeedbackParser) {
    this.authenticator = authenticator;
    this.service = service;
    this.dsnFeedbackParser = dsnFeedbackParser;
  }

  @PostMapping({"/event", "/postfix"})
  public ApiResponse<FeedbackResultResponse> event(
      @RequestHeader("X-UnifyDesk-Feedback-Token") String token,
      @Valid @RequestBody PostfixFeedbackRequest request) {
    authenticator.verify(token);
    return ApiResponse.success(
        HttpStatus.OK.value(), service.apply(request, null), "Feedback processed");
  }

  @PostMapping(
      value = "/dsn",
      consumes = {"text/plain", "message/rfc822", "application/octet-stream"})
  public ApiResponse<FeedbackResultResponse> dsn(
      @RequestHeader("X-UnifyDesk-Feedback-Token") String token,
      @RequestHeader("X-UnifyDesk-Envelope-Recipient") String envelopeRecipient,
      @RequestBody String rawMessage) {
    authenticator.verify(token);
    PostfixFeedbackRequest request = dsnFeedbackParser.parse(envelopeRecipient, rawMessage);
    return ApiResponse.success(
        HttpStatus.OK.value(), service.apply(request, rawMessage), "DSN feedback processed");
  }
}
