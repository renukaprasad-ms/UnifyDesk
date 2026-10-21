package com.messaging.emailprovider.mailbox.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.messaging.emailprovider.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class MailboxDsnProcessorTests {
  @Test
  void parsesOneStrictDsnRecipientAndMatchingStatus() {
    var bounced =
        MailboxDsnProcessor.parse(
            """
        Content-Type: message/delivery-status
        Final-Recipient: rfc822; victim@example.net
        Action: failed
        Status: 5.1.1
        """);
    assertEquals("victim@example.net", bounced.address());
    assertEquals(MailboxDeliveryFeedbackService.Type.BOUNCED, bounced.type());
    assertEquals("5.1.1", bounced.status());

    var delayed =
        MailboxDsnProcessor.parse(
            """
        Final-Recipient: rfc822; user@example.net
        Action: delayed
        Status: 4.2.0
        """);
    assertEquals(MailboxDeliveryFeedbackService.Type.DEFERRED, delayed.type());
  }

  @Test
  void rejectsAmbiguousMultiRecipientAndConflictingResults() {
    assertThrows(
        BadRequestException.class,
        () ->
            MailboxDsnProcessor.parse(
                """
        Final-Recipient: rfc822; a@example.net
        Action: failed
        Status: 5.1.1
        Final-Recipient: rfc822; b@example.net
        Action: failed
        Status: 5.1.1
        """));
    assertThrows(
        BadRequestException.class,
        () ->
            MailboxDsnProcessor.parse(
                """
        Final-Recipient: rfc822; a@example.net
        Action: delivered
        Status: 5.1.1
        """));
    assertThrows(
        BadRequestException.class,
        () ->
            MailboxDsnProcessor.parse(
                """
        Final-Recipient: rfc822; a@example.net
        Action: failed
        Status: 4.1.1
        """));
  }
}
