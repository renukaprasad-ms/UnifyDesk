package com.messaging.emailprovider.mailbox.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.mailbox.outbound.MailboxDeliveryFeedbackService.Type;
import org.junit.jupiter.api.Test;

class MailboxDeliveryFeedbackServiceTests {
  @Test
  void deliveryEventsOnlyAdvanceMonotonically() {
    assertEquals(
        "DEFERRED", MailboxDeliveryFeedbackService.transition("NOT_CONFIRMED", Type.DEFERRED));
    assertEquals(
        "DELIVERED", MailboxDeliveryFeedbackService.transition("DEFERRED", Type.DELIVERED));
    assertEquals("BOUNCED", MailboxDeliveryFeedbackService.transition("DEFERRED", Type.BOUNCED));
    assertEquals("DELIVERED", MailboxDeliveryFeedbackService.transition("DELIVERED", Type.BOUNCED));
    assertEquals("BOUNCED", MailboxDeliveryFeedbackService.transition("BOUNCED", Type.DELIVERED));
    assertEquals(
        "COMPLAINED", MailboxDeliveryFeedbackService.transition("BOUNCED", Type.COMPLAINED));
    assertEquals(
        "COMPLAINED", MailboxDeliveryFeedbackService.transition("COMPLAINED", Type.DELIVERED));
  }

  @Test
  void mailIdentifiersAreStrictPositive64BitStrings() {
    assertEquals(9223372036854775807L, MailboxDeliveryFeedbackService.id("9223372036854775807"));
    for (String invalid :
        new String[] {
          "",
          "0",
          "-1",
          "+1",
          "1.0",
          "01",
          "9223372036854775808",
          "3\nBcc:other@example.com",
          " 123",
          "123 "
        }) {
      assertThrows(BadRequestException.class, () -> MailboxDeliveryFeedbackService.id(invalid));
    }
  }
}
