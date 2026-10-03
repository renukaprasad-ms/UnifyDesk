package com.messaging.broadcast.msg91;

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

class Msg91EmailGatewayComplianceTests {

  @Test
  void payloadCarriesCanonicalUnsubscribeTemplateVariable() {
    Msg91EmailGateway gateway =
        new Msg91EmailGateway(
            mock(CredentialCipher.class), new ObjectMapper(), new BroadcastProperties());

    var payload =
        gateway.buildSendPayload(
            sender(), command(), new Msg91EmailProviderConfig("example.test", null, null, null));

    assertThat(
            payload.path("recipients")
                .get(0)
                .path("variables")
                .path("unsubscribe_url")
                .asText())
        .isEqualTo("https://app.example/unsubscribe?token=token");
  }

  @Test
  void payloadTruthfullyDoesNotClaimUnsupportedOneClickHeaders() {
    Msg91EmailGateway gateway =
        new Msg91EmailGateway(
            mock(CredentialCipher.class), new ObjectMapper(), new BroadcastProperties());

    var payload =
        gateway.buildSendPayload(
            sender(), command(), new Msg91EmailProviderConfig("example.test", null, null, null));

    assertThat(payload.has("headers")).isFalse();
    assertThat(payload.toString()).doesNotContain("List-Unsubscribe-Post");
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
        "template-1",
        Map.of("unsubscribe_url", "https://app.example/unsubscribe?token=token"),
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
