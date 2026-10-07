package com.messaging.channel.zoho;

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
@RequestMapping("/api/v1/oauth/zoho-mail")
@RequiredArgsConstructor
public class ZohoMailOAuthCallbackController {

  private final ZohoMailConnectionService connectionService;
  private final ZohoMailOAuthProperties properties;

  @GetMapping("/callback")
  public ResponseEntity<Void> callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error,
      @RequestParam(name = "accounts-server", required = false) String accountsServer) {
    try {
      if (error != null && !error.isBlank()) {
        String returnTo = connectionService.consumeCancelledStateWithReturn(state);
        return redirect("cancelled", returnTo);
      }

      ZohoMailConnectionService.ZohoMailCompletion completion =
          connectionService.completeWithReturn(state, code, accountsServer);
      return redirect("connected", completion.returnTo());
    } catch (RuntimeException exception) {
      log.warn(
          "zoho_mail_oauth_callback_failed reason={} message={}",
          exception.getClass().getSimpleName(),
          exception.getMessage());
      return redirect("error", "/company/connections");
    }
  }

  private ResponseEntity<Void> redirect(String result, String returnTo) {
    URI location =
        UriComponentsBuilder.fromUriString(properties.getFrontendBaseUrl())
            .path(returnTo == null || returnTo.isBlank() ? "/company/connections" : returnTo)
            .queryParam("zohoMail", result)
            .build()
            .encode()
            .toUri();
    return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
  }
}
