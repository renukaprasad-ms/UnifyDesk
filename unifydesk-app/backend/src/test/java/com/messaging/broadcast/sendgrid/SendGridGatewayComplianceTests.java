package com.messaging.broadcast.sendgrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.transport.BroadcastEmailCommand;
import com.messaging.broadcast.transport.BroadcastEmailComplianceMetadata;
import com.messaging.common.credential.CredentialCipher;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SendGridGatewayComplianceTests {

  @Test
  void payloadContainsStandardOneClickHeaders() {
    SendGridGateway gateway =
        new SendGridGateway(
            mock(CredentialCipher.class), new ObjectMapper(), new BroadcastProperties());

    var payload = gateway.buildPayload(sender(), command());

    assertThat(payload.path("headers").path("List-Unsubscribe").asText())
        .isEqualTo("<https://api.example/unsubscribe/token>");
    assertThat(payload.path("headers").path("List-Unsubscribe-Post").asText())
        .isEqualTo("List-Unsubscribe=One-Click");
  }

  @Test
  void payloadWithoutComplianceMetadataDoesNotInventHeaders() {
    SendGridGateway gateway =
        new SendGridGateway(
            mock(CredentialCipher.class), new ObjectMapper(), new BroadcastProperties());
    BroadcastEmailCommand command =
        new BroadcastEmailCommand(
            "recipient@example.test",
            "Subject",
            "Text",
            "<p>Text</p>",
            null,
            Map.of(),
            null,
            "corr");

    assertThat(gateway.buildPayload(sender(), command).has("headers")).isFalse();
  }

  private BroadcastSender sender() {
    BroadcastSender sender = new BroadcastSender();
    sender.setFromAddress("sender@example.test");
    return sender;
  }

  private BroadcastEmailCommand command() {
    return new BroadcastEmailCommand(
        "recipient@example.test",
        "Subject",
        "Text",
        "<p>Text</p>",
        null,
        Map.of(),
        null,
        "corr",
        null,
        null,
        null,
        null,
        new BroadcastEmailComplianceMetadata(
            "https://app.example/unsubscribe?token=token",
            "https://api.example/unsubscribe/token"));
  }
}
