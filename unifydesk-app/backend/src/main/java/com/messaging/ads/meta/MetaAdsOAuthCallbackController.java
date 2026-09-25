package com.messaging.ads.meta;

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
@RequestMapping("/api/v1/oauth/meta-ads")
@RequiredArgsConstructor
public class MetaAdsOAuthCallbackController {

  private final MetaAdsConnectionService connectionService;
  private final MetaAdsProperties properties;

  @GetMapping("/callback")
  public ResponseEntity<Void> callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error) {
    try {
      if (error != null && !error.isBlank()) {
        return redirect("cancelled", connectionService.consumeCancelledState(state));
      }

      MetaAdsConnectionService.Completion completion =
          connectionService.complete(state, code);
      return redirect("connected", completion.returnTo());
    } catch (RuntimeException exception) {
      return redirect("error", "/ad-center");
    }
  }

  private ResponseEntity<Void> redirect(String result, String returnTo) {
    URI location =
        UriComponentsBuilder.fromUriString(properties.getFrontendBaseUrl())
            .path(returnTo == null || returnTo.isBlank() ? "/ad-center" : returnTo)
            .queryParam("metaAds", result)
            .build()
            .encode()
            .toUri();
    return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
  }
}
