package com.messaging.channel.microsoft;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MicrosoftWebhookPayloadTests {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void acceptsMicrosoftFieldsThatAreNotUsedByOurProcessingLogic() throws Exception {
    String payload =
        """
        {
          "value": [
            {
              "subscriptionId": "subscription-1",
              "clientState": "expected-state",
              "changeType": "updated",
              "resource": "me/messages/message-1",
              "subscriptionExpirationDateTime": "2026-09-22T10:00:00Z",
              "tenantId": "tenant-1",
              "resourceData": {
                "id": "message-1",
                "@odata.type": "#Microsoft.Graph.Message"
              }
            }
          ],
          "validationTokens": ["ignored-by-current-processing"]
        }
        """;

    MicrosoftWebhookController.NotificationEnvelope envelope =
        objectMapper.readValue(
            payload, MicrosoftWebhookController.NotificationEnvelope.class);

    assertThat(envelope.value()).hasSize(1);
    MicrosoftSubscriptionService.ChangeNotification notification =
        envelope.value().getFirst();
    assertThat(notification.subscriptionId()).isEqualTo("subscription-1");
    assertThat(notification.clientState()).isEqualTo("expected-state");
    assertThat(notification.changeType()).isEqualTo("updated");
    assertThat(notification.resource()).isEqualTo("me/messages/message-1");
  }
}
