package com.messaging.channel.microsoft;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/oauth/microsoft")
@RequiredArgsConstructor
public class MicrosoftOAuthCallbackController {

  private final MicrosoftConnectionService connectionService;
  private final MicrosoftOAuthProperties properties;

  @GetMapping("/callback")
  public ResponseEntity<Void> callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error) {
    try {
      if (error != null && !error.isBlank()) {
        String returnTo = connectionService.consumeCancelledStateWithReturn(state);
        return redirect("cancelled", returnTo);
      }

      MicrosoftConnectionService.MicrosoftCompletion completion =
          connectionService.completeWithReturn(state, code);
      return redirect("connected", completion.returnTo());
    } catch (RuntimeException exception) {
      return redirect("error", "/company/connections");
    }
  }

  private ResponseEntity<Void> redirect(String result, String returnTo) {
    URI location =
        UriComponentsBuilder.fromUriString(properties.getFrontendBaseUrl())
            .path(returnTo == null || returnTo.isBlank() ? "/company/connections" : returnTo)
            .queryParam("microsoft", result)
            .build()
            .encode()
            .toUri();
    return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
  }
}
