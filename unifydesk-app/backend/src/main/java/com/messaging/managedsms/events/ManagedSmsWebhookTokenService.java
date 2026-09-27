package com.messaging.managedsms.events;

import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedSmsWebhookTokenService {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final ManagedSmsRuntimeProperties properties;

  public boolean matches(String providerAccountKey, String suppliedToken) {
    if (providerAccountKey == null
        || providerAccountKey.isBlank()
        || suppliedToken == null
        || suppliedToken.isBlank()) {
      return false;
    }
    String current = derive(properties.getWebhookTokenKey(), providerAccountKey);
    if (constantTimeEquals(current, suppliedToken.trim())) return true;

    if (!previousKeyActive()) return false;
    String previous = derive(properties.getWebhookPreviousTokenKey(), providerAccountKey);
    return constantTimeEquals(previous, suppliedToken.trim());
  }

  private boolean previousKeyActive() {
    String raw = properties.getWebhookPreviousTokenValidUntil();
    if (raw == null || raw.isBlank()) return false;
    try {
      return java.time.Instant.parse(raw.trim()).isAfter(java.time.Instant.now());
    } catch (RuntimeException exception) {
      return false;
    }
  }

  public String tokenForAccount(String providerAccountKey) {
    String token = derive(properties.getWebhookTokenKey(), providerAccountKey);
    if (token == null) {
      throw new IllegalStateException("Managed SMS webhook token key is not configured");
    }
    return token;
  }

  private String derive(String serverKey, String providerAccountKey) {
    if (serverKey == null
        || serverKey.getBytes(StandardCharsets.UTF_8).length < 32
        || providerAccountKey == null
        || providerAccountKey.isBlank()) {
      return null;
    }
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(serverKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      return HexFormat.of()
          .formatHex(mac.doFinal(providerAccountKey.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to derive managed SMS webhook token", exception);
    }
  }

  private boolean constantTimeEquals(String expected, String supplied) {
    if (expected == null || supplied == null) return false;
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII),
        supplied.getBytes(StandardCharsets.US_ASCII));
  }
}
