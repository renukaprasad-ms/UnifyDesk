package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class CashfreeWebhookVerifierTests {

  @Test
  void verifiesExactTimestampPlusRawBodyAndRejectsMutation() throws Exception {
    CashfreeProperties properties = new CashfreeProperties();
    properties.setClientSecret("test-secret");
    CashfreeWebhookVerifier verifier = new CashfreeWebhookVerifier(properties);

    String timestamp = "1720000000000";
    byte[] body = "{\"data\":{\"value\":1}}".getBytes(StandardCharsets.UTF_8);
    String signature = sign(properties.getClientSecret(), timestamp, body);

    assertTrue(verifier.verify(body, timestamp, signature));
    assertFalse(
        verifier.verify(
            "{\"data\": { \"value\": 1 }}".getBytes(StandardCharsets.UTF_8),
            timestamp,
            signature));
    assertFalse(verifier.verify(body, timestamp + "1", signature));
  }

  private String sign(String secret, String timestamp, byte[] body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(mac.doFinal(body));
  }
}
