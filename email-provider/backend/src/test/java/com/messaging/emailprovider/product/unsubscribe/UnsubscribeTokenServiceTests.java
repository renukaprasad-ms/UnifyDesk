package com.messaging.emailprovider.product.unsubscribe;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UnsubscribeTokenServiceTests {
  private final UnsubscribeTokenService signer = new UnsubscribeTokenService(
      "testing-only-32-character-unsubscribe-signing-key", "https://email.unifydesk.in");

  @Test
  void verifiesSignedIdsAndRejectsTampering() {
    String token = signer.sign(120L, 450L);
    var match = signer.verify(token);
    assertEquals(120L, match.messageId());
    assertEquals(450L, match.recipientId());
    assertTrue(signer.url(120L, 450L).startsWith("https://email.unifydesk.in/v1/unsubscribe/"));
    assertThrows(RuntimeException.class,
        () -> signer.verify(token.replace("120.", "121.")));
  }

  @Test
  void missingSecretFailsClosed() {
    var absent = new UnsubscribeTokenService("", "https://email.unifydesk.in");
    assertThrows(RuntimeException.class, () -> absent.sign(1L, 2L));
  }
}
