package com.messaging.channel.zoho;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZohoMailApiClientTests {

  @Test
  void authorizationUrlRequestsOfflineMailAccessAndState() {
    ZohoMailOAuthProperties properties = configured();
    ZohoMailApiClient client = new ZohoMailApiClient(properties);

    String url = client.authorizationUrl("state-value");

    assertThat(url)
        .startsWith("https://accounts.zoho.com/oauth/v2/auth?")
        .contains("client_id=client-id")
        .contains("response_type=code")
        .contains("access_type=offline")
        .contains("prompt=consent")
        .contains("state=state-value")
        .contains("ZohoMail.accounts.READ")
        .contains("ZohoMail.folders.READ")
        .contains("ZohoMail.messages.ALL");
  }

  @Test
  void configurationRequiresOAuthSecretsAndRedirectUri() {
    ZohoMailOAuthProperties properties = new ZohoMailOAuthProperties();
    properties.setEnabled(true);

    assertThat(properties.configured()).isFalse();

    properties.setClientId("client-id");
    properties.setClientSecret("client-secret");
    properties.setRedirectUri("https://api.example.com/api/v1/oauth/zoho-mail/callback");

    assertThat(properties.configured()).isTrue();
  }

  private ZohoMailOAuthProperties configured() {
    ZohoMailOAuthProperties properties = new ZohoMailOAuthProperties();
    properties.setEnabled(true);
    properties.setClientId("client-id");
    properties.setClientSecret("client-secret");
    properties.setRedirectUri("https://api.example.com/api/v1/oauth/zoho-mail/callback");
    properties.setFrontendBaseUrl("https://app.example.com");
    return properties;
  }
}
