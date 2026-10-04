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
    assertSignedMillisAccepted(NOW.minusSeconds(30));
    assertSignedSecondsAccepted(NOW.minusSeconds(30));
  }

  @Test
  void malformedTimestampIsRejectedEvenWhenSigned() throws Exception {
    String timestamp = "not-a-numeric-epoch";
    assertFalse(verifier.verify(body, timestamp, sign(properties.getClientSecret(), timestamp, body)));
  }

  @Test
  void tooOldValidlySignedTimestampIsRejected() throws Exception {
    assertSignedMillisRejected(NOW.minusSeconds(301));
    assertSignedSecondsRejected(NOW.minusSeconds(301));
  }

  @Test
  void excessivelyFutureValidlySignedTimestampIsRejected() throws Exception {
    assertSignedMillisRejected(NOW.plusSeconds(61));
    assertSignedSecondsRejected(NOW.plusSeconds(61));
  }

  @Test
  void exactPastBoundaryIsAccepted() throws Exception {
    assertSignedMillisAccepted(NOW.minusSeconds(300));
    assertSignedSecondsAccepted(NOW.minusSeconds(300));
  }

  @Test
  void exactFutureSkewBoundaryIsAccepted() throws Exception {
    assertSignedMillisAccepted(NOW.plusSeconds(60));
    assertSignedSecondsAccepted(NOW.plusSeconds(60));
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
  void signatureUsesExactOriginalTimestampString() throws Exception {
    String timestamp = "0" + Long.toString(NOW.getEpochSecond());
    String signature = sign(properties.getClientSecret(), timestamp, body);

    assertTrue(verifier.verify(body, timestamp, signature));
    assertFalse(
        verifier.verify(
            body,
            Long.toString(NOW.getEpochSecond()),
            signature));
  }

  @Test
  void numericOverflowIsRejected() throws Exception {
    String timestamp = "999999999999999999999999999999999999";
    assertFalse(verifier.verify(body, timestamp, sign(properties.getClientSecret(), timestamp, body)));
  }

  @Test
  void implausibleEpochValueIsRejected() throws Exception {
    String timestamp = "1";
    assertFalse(verifier.verify(body, timestamp, sign(properties.getClientSecret(), timestamp, body)));
  }

  @Test
  void invalidFreshnessConfigurationFailsClosed() throws Exception {
    properties.setWebhookMaxAgeSeconds(0);
    assertSignedMillisRejected(NOW);
    properties.setWebhookMaxAgeSeconds(-1);
    assertSignedMillisRejected(NOW);
    properties.setWebhookMaxAgeSeconds(3601);
    assertSignedMillisRejected(NOW);

    properties.setWebhookMaxAgeSeconds(300);
    properties.setWebhookFutureSkewSeconds(0);
    assertSignedMillisRejected(NOW);
    properties.setWebhookFutureSkewSeconds(-1);
    assertSignedMillisRejected(NOW);
    properties.setWebhookFutureSkewSeconds(601);
    assertSignedMillisRejected(NOW);
  }

  private void assertSignedMillisAccepted(Instant at) throws Exception {
    assertTimestampAccepted(Long.toString(at.toEpochMilli()));
  }

  private void assertSignedSecondsAccepted(Instant at) throws Exception {
    assertTimestampAccepted(Long.toString(at.getEpochSecond()));
  }

  private void assertSignedMillisRejected(Instant at) throws Exception {
    assertTimestampRejected(Long.toString(at.toEpochMilli()));
  }

  private void assertSignedSecondsRejected(Instant at) throws Exception {
    assertTimestampRejected(Long.toString(at.getEpochSecond()));
  }

  private void assertTimestampAccepted(String timestamp) throws Exception {
    assertTrue(verifier.verify(body, timestamp, sign(properties.getClientSecret(), timestamp, body)));
  }

  private void assertTimestampRejected(String timestamp) throws Exception {
    assertFalse(verifier.verify(body, timestamp, sign(properties.getClientSecret(), timestamp, body)));
  }

  private String sign(String secret, String timestamp, byte[] rawBody) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(mac.doFinal(rawBody));
  }
}
