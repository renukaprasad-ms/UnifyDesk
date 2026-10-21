package com.messaging.emailprovider.product.webhook;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WebhookSigningTests {
  private final WebhookSigning signer = new WebhookSigning();

  @Test
  void signsTimestampAndPayloadAndInvalidatesTampering() {
    String valid = signer.sign("secret", "1760000000", "{\"status\":\"DELIVERED\"}");
    assertTrue(valid.startsWith("v1="));
    assertEquals(valid, signer.sign("secret", "1760000000", "{\"status\":\"DELIVERED\"}"));
    assertNotEquals(valid, signer.sign("secret", "1760000001", "{\"status\":\"DELIVERED\"}"));
    assertNotEquals(valid, signer.sign("secret", "1760000000", "{\"status\":\"BOUNCED\"}"));
  }
}
