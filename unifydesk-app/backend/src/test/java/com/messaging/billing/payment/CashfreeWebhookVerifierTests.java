package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CashfreeWebhookVerifierTests {

  private static final Instant NOW = Instant.parse("2026-10-04T04:00:00Z");

  private CashfreeProperties properties;
  private CashfreeWebhookVerifier verifier;
  private byte[] body;

  @BeforeEach
  void setUp() {
    properties = new CashfreeProperties();
    properties.setClientSecret("test-secret");
    properties.setWebhookMaxAgeSeconds(300);
    properties.setWebhookFutureSkewSeconds(60);
    verifier =
        new CashfreeWebhookVerifier(
            properties, Clock.fixed(NOW, ZoneOffset.UTC));
    body = "{\"data\":{\"value\":1}}".getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void validFreshTimestampAndSignatureAreAccepted() throws Exception {
    assertSignedAccepted(NOW.minusSeconds(30));
  }

  @Test
  void malformedTimestampIsRejectedEvenWhenSigned() throws Exception {
    String timestamp = "not-an-epoch-millis";
    assertFalse(verifier.verify(body, timestamp, sign(properties.getClientSecret(), timestamp, body)));
  }

  @Test
  void tooOldValidlySignedTimestampIsRejected() throws Exception {
    assertSignedRejected(NOW.minusSeconds(301));
  }

  @Test
  void excessivelyFutureValidlySignedTimestampIsRejected() throws Exception {
    assertSignedRejected(NOW.plusSeconds(61));
  }

  @Test
  void exactPastBoundaryIsAccepted() throws Exception {
    assertSignedAccepted(NOW.minusSeconds(300));
  }

  @Test
  void exactFutureSkewBoundaryIsAccepted() throws Exception {
    assertSignedAccepted(NOW.plusSeconds(60));
  }

  @Test
  void badSignatureStillRejected() {
    String timestamp = Long.toString(NOW.toEpochMilli());
    assertFalse(verifier.verify(body, timestamp, "ZmFrZQ=="));
  }

  @Test
  void rawBodyMutationStillRejected() throws Exception {
    String timestamp = Long.toString(NOW.toEpochMilli());
    String signature = sign(properties.getClientSecret(), timestamp, body);
    assertFalse(
        verifier.verify(
            "{\"data\": { \"value\": 1 }}".getBytes(StandardCharsets.UTF_8),
            timestamp,
            signature));
  }

  @Test
  void invalidFreshnessConfigurationFailsClosed() throws Exception {
    properties.setWebhookMaxAgeSeconds(0);
    assertSignedRejected(NOW);
    properties.setWebhookMaxAgeSeconds(-1);
    assertSignedRejected(NOW);
    properties.setWebhookMaxAgeSeconds(3601);
    assertSignedRejected(NOW);

    properties.setWebhookMaxAgeSeconds(300);
    properties.setWebhookFutureSkewSeconds(0);
    assertSignedRejected(NOW);
    properties.setWebhookFutureSkewSeconds(-1);
    assertSignedRejected(NOW);
    properties.setWebhookFutureSkewSeconds(601);
    assertSignedRejected(NOW);
  }

  private void assertSignedAccepted(Instant at) throws Exception {
    String timestamp = Long.toString(at.toEpochMilli());
    assertTrue(verifier.verify(body, timestamp, sign(properties.getClientSecret(), timestamp, body)));
  }

  private void assertSignedRejected(Instant at) throws Exception {
    String timestamp = Long.toString(at.toEpochMilli());
    assertFalse(verifier.verify(body, timestamp, sign(properties.getClientSecret(), timestamp, body)));
  }

  private String sign(String secret, String timestamp, byte[] rawBody) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(mac.doFinal(rawBody));
  }
}
