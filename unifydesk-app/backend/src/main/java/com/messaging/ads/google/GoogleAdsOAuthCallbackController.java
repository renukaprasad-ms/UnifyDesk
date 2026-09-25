package com.messaging.ads.google;

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
@RequestMapping("/api/v1/oauth/google-ads")
@RequiredArgsConstructor
public class GoogleAdsOAuthCallbackController {

  private final GoogleAdsConnectionService connectionService;
  private final GoogleAdsProperties properties;

  @GetMapping("/callback")
  public ResponseEntity<Void> callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error) {
    try {
      if (error != null && !error.isBlank()) {
        log.warn("Google Ads OAuth callback returned an OAuth error error={}", error);
        return redirect("cancelled", connectionService.consumeCancelledState(state));
      }

      GoogleAdsConnectionService.Completion completion =
          connectionService.complete(state, code);
      return redirect("connected", completion.returnTo());
    } catch (RuntimeException exception) {
      log.warn(
          "Google Ads OAuth callback failed errorType={} message={}",
          exception.getClass().getSimpleName(),
          exception.getMessage());
      return redirect("error", "/ad-center");
    }
  }

  private ResponseEntity<Void> redirect(String result, String returnTo) {
    URI location =
        UriComponentsBuilder.fromUriString(properties.getFrontendBaseUrl())
            .path(returnTo == null || returnTo.isBlank() ? "/ad-center" : returnTo)
            .queryParam("googleAds", result)
            .build()
            .encode()
            .toUri();

    return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
  }
}
