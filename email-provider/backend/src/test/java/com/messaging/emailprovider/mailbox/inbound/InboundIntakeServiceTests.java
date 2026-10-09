package com.messaging.emailprovider.mailbox.inbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.messaging.emailprovider.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class InboundIntakeServiceTests {
  @Test
  void parsesRecipientFromEnvelopeNotMessageHeaders() {
    InboundIntakeService.Recipient target = InboundIntakeService.parseRecipient("Sales@Example.COM");
    assertEquals("sales", target.localPart());
    assertEquals("example.com", target.domain());
  }

  @Test
  void rejectsInvalidRecipientValues() {
    for (String address : new String[]{"", "abc", "@example.com",
        "x@@example.com", "x@example.com\nBCC:victim@example.com",
        "../x@example.com", "x@localhost", "x@bad..example.com"}) {
      assertThrows(BadRequestException.class,
          () -> InboundIntakeService.parseRecipient(address), address);
    }
  }
}
