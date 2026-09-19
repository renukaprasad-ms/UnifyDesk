package com.messaging.channel.gmail;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

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
    try {
      if (error != null && !error.isBlank()) {
        String returnTo = gmailConnectionService.consumeCancelledStateWithReturn(state);
        return redirect("cancelled", returnTo);
      }

      GmailConnectionService.GmailCompletion completion =
          gmailConnectionService.completeWithReturn(state, code);
      return redirect("connected", completion.returnTo());
    } catch (RuntimeException exception) {
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
