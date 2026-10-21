package com.messaging.emailprovider.mailbox.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.messaging.emailprovider.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class MailBounceAddressTests {
  private final MailBounceAddress signer =
      new MailBounceAddress(
          "bounces.example.com", "long-test-secret-at-least-thirty-two-characters");

  @Test
  void signsSpecificRecipientWithShortVerpLocalPart() {
    long intentId = 9223372036854775000L;
    long recipientId = 9223372036854775001L;
    String address = signer.envelopeFrom(intentId, recipientId);
    assertTrue(address.substring(0, address.indexOf('@')).length() <= 64);
    var target = signer.verify(address);
    assertEquals(intentId, target.intentId());
    assertEquals(recipientId, target.recipientId());
  }

  @Test
  void forgedOrCrossDomainBounceAddressFails() {
    String address = signer.envelopeFrom(12345L, 67890L);
    assertThrows(BadRequestException.class, () -> signer.verify(address.replace("12345", "12346")));
    assertThrows(
        BadRequestException.class,
        () -> signer.verify(address.replace("@bounces.example.com", "@attacker.example")));
    assertThrows(
        BadRequestException.class,
        () -> signer.verify("ud+0.67890.0123456789abcdef@bounces.example.com"));
  }

  @Test
  void refusesWeakSecretOrMissingBounceDomain() {
    assertThrows(
        IllegalStateException.class, () -> new MailBounceAddress("bounces.example.com", "short"));
    assertThrows(
        IllegalStateException.class,
        () -> new MailBounceAddress("", "long-test-secret-at-least-thirty-two-characters"));
  }
}
