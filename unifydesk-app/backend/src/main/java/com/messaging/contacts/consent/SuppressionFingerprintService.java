package com.messaging.contacts.consent;

import com.messaging.contacts.audience.ContactAudienceProperties;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SuppressionFingerprintService {

  private final ContactAudienceProperties properties;

  public Fingerprint fingerprint(Channel channel, String normalizedDestination) {
    if (normalizedDestination == null || normalizedDestination.isBlank()) {
      throw new IllegalArgumentException("Normalized destination is required");
    }
    String secret = properties.getSuppressionHmacSecret();
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("Contacts suppression HMAC secret is not configured");
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest =
          mac.doFinal(
              (channel.name() + "\u0000" + normalizedDestination)
                  .getBytes(StandardCharsets.UTF_8));
      return new Fingerprint(
          Base64.getUrlEncoder().withoutPadding().encodeToString(digest),
          properties.getSuppressionHmacKeyVersion());
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to compute suppression fingerprint", exception);
    }
  }

  public record Fingerprint(String value, int keyVersion) {}
}
