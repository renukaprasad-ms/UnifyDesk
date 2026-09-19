package com.messaging.channel.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GmailApiClientTests {

  @Test
  void messagesListUriEncodesSearchQueryExactlyOnce() {
    GmailOAuthProperties properties = new GmailOAuthProperties();
    GmailApiClient client = new GmailApiClient(properties);

    var uri =
        client.messagesListUri(
            100, "after:1787183999 before:1789862400", "next/page+token");

    assertThat(uri.getQuery())
        .contains("q=after:1787183999 before:1789862400")
        .contains("pageToken=next/page+token");
    assertThat(uri.toASCIIString())
        .contains("q=after:1787183999%20before:1789862400")
        .doesNotContain("%2520")
        .doesNotContain("%253A");
  }

  @Test
  void authorizationUrlRequestsOfflineGmailModifyAccessAndState() {
    GmailOAuthProperties properties = new GmailOAuthProperties();
    properties.setEnabled(true);
    properties.setClientId("client-id");
    properties.setClientSecret("client-secret");
    properties.setRedirectUri("https://api.example.com/api/v1/oauth/gmail/callback");
    properties.setFrontendBaseUrl("https://app.example.com");

    GmailApiClient client = new GmailApiClient(properties);

    String url = client.authorizationUrl("state-value");

    assertThat(url)
        .startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
        .contains("client_id=client-id")
        .contains("response_type=code")
        .contains("access_type=offline")
        .contains("include_granted_scopes=true")
        .contains("prompt=consent")
        .contains("state=state-value")
        .contains("gmail.modify");
  }
}
