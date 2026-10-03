package com.messaging.billing.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CashfreeWebhookVerifier {

  private final CashfreeProperties properties;

  public boolean verify(byte[] rawBody, String timestamp, String signature) {
    if (rawBody == null
        || timestamp == null
        || timestamp.isBlank()
        || signature == null
        || signature.isBlank()
        || properties.getClientSecret() == null
        || properties.getClientSecret().isBlank()) {
      return false;
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              properties.getClientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
      byte[] expected = mac.doFinal(rawBody);
      byte[] supplied = Base64.getDecoder().decode(signature);
      return MessageDigest.isEqual(expected, supplied);
    } catch (Exception exception) {
      return false;
    }
  }
}
