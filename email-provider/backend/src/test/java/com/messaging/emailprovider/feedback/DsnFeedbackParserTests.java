package com.messaging.emailprovider.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DsnFeedbackParserTests {

  private final DsnFeedbackParser parser = new DsnFeedbackParser();

  @Test
  void parsesPermanentFailure() {
    String dsn =
        """
        Content-Type: message/delivery-status

        Final-Recipient: rfc822; user@example.net
        Action: failed
        Status: 5.1.1
        Diagnostic-Code: smtp; 550 5.1.1 user unknown
        """;

    var result = parser.parse("bounce+100.200@bounce.reachlyo.com", dsn);

    assertEquals(200L, result.recipientId());
    assertEquals(EmailEventType.BOUNCED, result.eventType());
    assertEquals("5.1.1", result.smtpStatusCode());
    assertEquals(EmailEventSource.DSN, result.source());
  }

  @Test
  void parsesDelayedDelivery() {
    String dsn =
        """
        Action: delayed
        Status: 4.7.0
        Diagnostic-Code: smtp; 421 4.7.0 try later
        """;

    var result = parser.parse("bounce+100.201@bounce.reachlyo.com", dsn);

    assertEquals(201L, result.recipientId());
    assertEquals(EmailEventType.DEFERRED, result.eventType());
  }
}
