package com.messaging.billing.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayWebhookVerifier {

  private final RazorpayProperties properties;

  public boolean verify(byte[] rawBody, String signature) {
    if (!properties.isEnabled()
        || signature == null
        || signature.isBlank()
        || rawBody == null) {
      return false;
    }

    String secret = properties.getWebhookSecret();
    if (secret == null || secret.isBlank()) {
      log.warn("Razorpay webhook secret is not configured; rejecting webhook");
      return false;
    }

    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] hash = mac.doFinal(rawBody);
      String expected = HexFormat.of().formatHex(hash);

      return MessageDigest.isEqual(
          expected.getBytes(StandardCharsets.US_ASCII),
          signature.trim().getBytes(StandardCharsets.US_ASCII));
    } catch (Exception ex) {
      log.warn("Razorpay webhook signature verification error: {}", ex.getMessage());
      return false;
    }
  }

  public boolean verifyPaymentSignature(
      String orderId, String paymentId, String signature) {
    if (orderId == null || paymentId == null || signature == null) {
      return false;
    }

    String secret = properties.getKeySecret();
    if (secret == null || secret.isBlank()) {
      return false;
    }

    try {
      String payload = orderId + "|" + paymentId;
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      String expected = HexFormat.of().formatHex(hash);

      return MessageDigest.isEqual(
          expected.getBytes(StandardCharsets.US_ASCII),
          signature.trim().getBytes(StandardCharsets.US_ASCII));
    } catch (Exception ex) {
      log.warn("Razorpay payment signature verification error: {}", ex.getMessage());
      return false;
    }
  }
}
