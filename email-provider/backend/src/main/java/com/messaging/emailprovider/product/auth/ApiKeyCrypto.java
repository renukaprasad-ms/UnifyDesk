package com.messaging.emailprovider.product.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class ApiKeyCrypto {
  private static final SecureRandom RANDOM = new SecureRandom();
  private ApiKeyCrypto() {}

  public static String random(int bytes) {
    byte[] buf = new byte[bytes];
    RANDOM.nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }

  /** Hex prefix cannot contain the underscore delimiter used in API key tokens. */
  public static String prefix() {
    byte[] bytes = new byte[6];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  public static String hash(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  public static boolean matches(String presented, String expectedHash) {
    return MessageDigest.isEqual(
        hash(presented).getBytes(StandardCharsets.US_ASCII),
        expectedHash.getBytes(StandardCharsets.US_ASCII));
  }
}
