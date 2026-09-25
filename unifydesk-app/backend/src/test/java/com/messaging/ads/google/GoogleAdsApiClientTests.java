package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GoogleAdsApiClientTests {

  @Test
  void authorizationUrlRequestsOfflineGoogleAdsAccess() {
    GoogleAdsProperties properties = new GoogleAdsProperties();
    properties.setEnabled(true);
    properties.setClientId("client-id.apps.googleusercontent.com");
    properties.setClientSecret("secret");
    properties.setRedirectUri(
        "https://api.unifydesk.in/api/v1/oauth/google-ads/callback");
    properties.setFrontendBaseUrl("https://app.unifydesk.in");
    properties.setApiVersion("v25");
    properties.setScope(
        "openid email profile https://www.googleapis.com/auth/adwords");
    properties.setOauthStateTtl(Duration.ofMinutes(10));

    GoogleAdsApiClient client = new GoogleAdsApiClient(properties);

    String url = client.authorizationUrl("state-123");

    assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
    assertThat(url).contains("client_id=client-id.apps.googleusercontent.com");
    assertThat(url).contains("response_type=code");
    assertThat(url).contains("access_type=offline");
    assertThat(url).contains("prompt=consent");
    assertThat(url).contains("include_granted_scopes=true");
    assertThat(url).contains("state=state-123");
    assertThat(url).contains("www.googleapis.com");
    assertThat(url).contains("auth");
    assertThat(url).contains("adwords");
  }
}
