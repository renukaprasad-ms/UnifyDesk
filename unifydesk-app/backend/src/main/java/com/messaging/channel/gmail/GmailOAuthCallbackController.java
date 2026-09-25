package com.messaging.channel.gmail;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@RestController
@RequestMapping("/api/v1/oauth/gmail")
@RequiredArgsConstructor
public class GmailOAuthCallbackController {

  private final GmailConnectionService gmailConnectionService;
  private final GmailOAuthProperties properties;

  @GetMapping("/callback")
  public ResponseEntity<Void> callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error) {
    log.info(
        "gmail_oauth_callback_start hasCode={} hasState={} providerError={}",
        code != null && !code.isBlank(),
        state != null && !state.isBlank(),
        error != null && !error.isBlank());

    try {
      if (error != null && !error.isBlank()) {
        log.warn("gmail_oauth_callback_provider_error error={}", error);
        String returnTo = gmailConnectionService.consumeCancelledStateWithReturn(state);
        return redirect("cancelled", returnTo);
      }

      GmailConnectionService.GmailCompletion completion =
          gmailConnectionService.completeWithReturn(state, code);
      log.info(
          "gmail_oauth_callback_connected connectionId={}",
          completion.connection().connectionId());
      return redirect("connected", completion.returnTo());
    } catch (RuntimeException exception) {
      log.error(
          "gmail_oauth_callback_failed exceptionType={} message={}",
          exception.getClass().getSimpleName(),
          exception.getMessage(),
          exception);
      return redirect("error", "/company/connections");
    }
  }

  private ResponseEntity<Void> redirect(String result, String returnTo) {
    URI location =
        UriComponentsBuilder.fromUriString(properties.getFrontendBaseUrl())
            .path(returnTo == null || returnTo.isBlank() ? "/company/connections" : returnTo)
            .queryParam("gmail", result)
            .build()
            .encode()
            .toUri();
    return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
  }
}
