package com.messaging.channel.microsoft;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MicrosoftApiClientTests {

  @Test
  void authorizationUrlRequestsOfflineMailPermissionsAndState() {
    MicrosoftOAuthProperties properties = configuredProperties();
    MicrosoftApiClient client = new MicrosoftApiClient(properties);

    String url = client.authorizationUrl("state-value");

    assertThat(url)
        .startsWith("https://login.microsoftonline.com/common/oauth2/v2.0/authorize?")
        .contains("client_id=client-id")
        .contains("response_type=code")
        .contains("response_mode=query")
        .contains("state=state-value")
        .contains("offline_access")
        .contains("Mail.ReadWrite")
        .contains("Mail.Send");
  }

  @Test
  void initialDeltaUriSelectsOnlyRequiredMessageFields() {
    MicrosoftOAuthProperties properties = configuredProperties();
    properties.setDeltaPageSize(50);
    MicrosoftApiClient client = new MicrosoftApiClient(properties);

    var uri = client.initialDeltaUri("inbox");
    String value = uri.toASCIIString();

    assertThat(value)
        .startsWith("https://graph.microsoft.com/v1.0/me/mailFolders/inbox/messages/delta?")
        .contains("%24select=")
        .contains("conversationId")
        .contains("internetMessageId")
        .contains("%24expand=")
        .contains("attachments")
        .contains("%24top=50")
        .doesNotContain("%2524");
  }

  @Test
  void configurationRequiresOAuthSecretsAndRedirectUri() {
    MicrosoftOAuthProperties properties = new MicrosoftOAuthProperties();
    properties.setEnabled(true);

    assertThat(properties.configured()).isFalse();

    properties.setClientId("client-id");
    properties.setClientSecret("secret");
    properties.setRedirectUri("https://api.example.com/api/v1/oauth/microsoft/callback");

    assertThat(properties.configured()).isTrue();
  }

  @Test
  void webhookConfigurationRequiresNotificationUrl() {
    MicrosoftOAuthProperties properties = configuredProperties();
    properties.setWebhookNotificationUrl("");

    assertThat(properties.webhookConfigured()).isFalse();

    properties.setWebhookNotificationUrl("https://api.example.com/api/v1/webhooks/microsoft");

    assertThat(properties.webhookConfigured()).isTrue();
  }

  private MicrosoftOAuthProperties configuredProperties() {
    MicrosoftOAuthProperties properties = new MicrosoftOAuthProperties();
    properties.setEnabled(true);
    properties.setClientId("client-id");
    properties.setClientSecret("client-secret");
    properties.setRedirectUri("https://api.example.com/api/v1/oauth/microsoft/callback");
    properties.setFrontendBaseUrl("https://app.example.com");
    properties.setTokenRefreshSkew(Duration.ofMinutes(2));
    return properties;
  }
}
