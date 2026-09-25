package com.messaging.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.delivery.enums.EmailBounceType;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.InboxMessageDirection;
import org.junit.jupiter.api.Test;

class EmailDeliveryReportParserTests {

  private final EmailDeliveryReportParser parser = new EmailDeliveryReportParser();

  @Test
  void parsesStructuredHardBounce() {
    InboxMessage message = inbound();
    message.setFromAddress("mailer-daemon@example.net");
    message.setSubject("Delivery Status Notification (Failure)");
    message.setBodyText(
        """
        Final-Recipient: rfc822; missing@example.com
        Original-Message-ID: <broadcast.123@example.test>
        X-Messaging-Broadcast-Recipient: 550e8400-e29b-41d4-a716-446655440000
        Status: 5.1.1
        Diagnostic-Code: smtp; 550 5.1.1 user unknown
        """);

    var detected = parser.parse(message);

    assertThat(detected).isPresent();
    assertThat(detected.orElseThrow().normalizedFailedRecipient())
        .isEqualTo("missing@example.com");
    assertThat(detected.orElseThrow().originalMessageId())
        .isEqualTo("<broadcast.123@example.test>");
    assertThat(detected.orElseThrow().providerCorrelationId())
        .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    assertThat(detected.orElseThrow().statusCode()).isEqualTo("5.1.1");
    assertThat(detected.orElseThrow().bounceType()).isEqualTo(EmailBounceType.HARD);
  }

  @Test
  void parsesMicrosoftStyleHumanReadableNdr() {
    InboxMessage message = inbound();
    message.setFromAddress("postmaster@outlook.com");
    message.setSubject("Undeliverable: Product update");
    message.setBodyText(
        "Your message to missing@example.com couldn't be delivered. "
            + "The remote mail system returned 5.1.1.");

    var detected = parser.parse(message);

    assertThat(detected).isPresent();
    assertThat(detected.orElseThrow().normalizedFailedRecipient())
        .isEqualTo("missing@example.com");
    assertThat(detected.orElseThrow().statusCode()).isEqualTo("5.1.1");
    assertThat(detected.orElseThrow().bounceType()).isEqualTo(EmailBounceType.HARD);
  }

  @Test
  void ignoresOrdinaryInboundEmail() {
    InboxMessage message = inbound();
    message.setFromAddress("customer@example.com");
    message.setSubject("Question about pricing");
    message.setBodyText("Can you send me the pricing details?");

    assertThat(parser.parse(message)).isEmpty();
  }

  private InboxMessage inbound() {
    InboxMessage message = new InboxMessage();
    message.setDirection(InboxMessageDirection.INBOUND);
    return message;
  }
}
