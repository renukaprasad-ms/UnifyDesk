package com.messaging.emailprovider.product.console;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238, HMAC-SHA1, 30-second periods. Secrets are 160 random bits in Base32. */
public final class Totp {
  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

  private Totp() {}

  public static String newSecret() {
    byte[] bytes = new byte[20];
    new SecureRandom().nextBytes(bytes);
    StringBuilder encoded = new StringBuilder(32);
    int bits = 0, value = 0;
    for (byte next : bytes) {
      value = (value << 8) | (next & 255);
      bits += 8;
      while (bits >= 5) {
        bits -= 5;
        encoded.append(ALPHABET.charAt((value >>> bits) & 31));
      }
    }
    return encoded.toString();
  }

  static String code(String secret, long counter, int digits) {
    if (secret == null
        || !secret.matches("[A-Z2-7]{32}")
        || counter < 0
        || (digits != 6 && digits != 8))
      throw new IllegalArgumentException("Invalid authenticator parameters");
    byte[] decoded = new byte[20];
    int bits = 0, value = 0, index = 0;
    for (char next : secret.toCharArray()) {
      value = (value << 5) | ALPHABET.indexOf(next);
      bits += 5;
      if (bits >= 8) {
        bits -= 8;
        decoded[index++] = (byte) (value >>> bits);
      }
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(decoded, "HmacSHA1"));
      byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
      int offset = hash[hash.length - 1] & 15;
      int number =
          ((hash[offset] & 127) << 24)
              | ((hash[offset + 1] & 255) << 16)
              | ((hash[offset + 2] & 255) << 8)
              | (hash[offset + 3] & 255);
      return String.format(
          Locale.ROOT, "%0" + digits + "d", number % (digits == 6 ? 1000000 : 100000000));
    } catch (Exception failure) {
      throw new IllegalStateException("Authenticator unavailable", failure);
    }
  }

  static long match(String secret, String supplied, long epochSeconds, long lastCounter) {
    if (supplied == null || !supplied.matches("[0-9]{6}")) return -1;
    long current = epochSeconds / 30;
    for (long counter = current - 1; counter <= current + 1; counter++) {
      if (counter >= 0
          && counter > lastCounter
          && MessageDigest.isEqual(
              code(secret, counter, 6).getBytes(StandardCharsets.US_ASCII),
              supplied.getBytes(StandardCharsets.US_ASCII))) return counter;
    }
    return -1;
  }
}
