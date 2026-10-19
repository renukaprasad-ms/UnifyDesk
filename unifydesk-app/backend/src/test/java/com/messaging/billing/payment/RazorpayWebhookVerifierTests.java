package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RazorpayWebhookVerifierTests {

  private RazorpayProperties properties;
  private RazorpayWebhookVerifier verifier;

  @BeforeEach
  void setUp() {
    properties = new RazorpayProperties();
    properties.setEnabled(true);
    properties.setWebhookSecret("test_webhook_secret");
    properties.setKeySecret("test_key_secret");
    verifier = new RazorpayWebhookVerifier(properties);
  }

  @Test
  void validWebhookSignatureIsAccepted() throws Exception {
    byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
    String signature = computeHmacHex(properties.getWebhookSecret(), body);

    assertTrue(verifier.verify(body, signature));
  }

  @Test
  void invalidWebhookSignatureIsRejected() {
    byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
    assertFalse(verifier.verify(body, "invalid_hex_signature"));
  }

  @Test
  void validPaymentSignatureIsAccepted() throws Exception {
    String orderId = "order_123456";
    String paymentId = "pay_789012";
    String payload = orderId + "|" + paymentId;
    String signature = computeHmacHex(properties.getKeySecret(), payload.getBytes(StandardCharsets.UTF_8));

    assertTrue(verifier.verifyPaymentSignature(orderId, paymentId, signature));
  }

  @Test
  void invalidPaymentSignatureIsRejected() {
    assertFalse(verifier.verifyPaymentSignature("order_123", "pay_456", "bad_signature"));
  }

  private String computeHmacHex(String secret, byte[] data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(data));
  }
}
