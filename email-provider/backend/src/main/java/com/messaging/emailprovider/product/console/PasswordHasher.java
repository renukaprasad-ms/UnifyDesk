package com.messaging.emailprovider.product.console;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {
  private static final int ITERATIONS = 310000;
  private static final int KEY_BITS = 256;
  private static final SecureRandom RANDOM = new SecureRandom();

  public String hash(String password) {
    if (password == null || password.length() < 12 || password.length() > 128) {
      throw new IllegalArgumentException("Password must be 12 to 128 characters");
    }
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);
    return "pbkdf2-sha256$" + ITERATIONS + "$"
        + Base64.getEncoder().encodeToString(salt) + "$"
        + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
  }

  public boolean verify(String password, String stored) {
    if (password == null || stored == null) return false;
    try {
      String[] parts = stored.split("\\$");
      if (parts.length != 4 || !"pbkdf2-sha256".equals(parts[0])) return false;
      int iterations = Integer.parseInt(parts[1]);
      if (iterations < 100000 || iterations > 1000000) return false;
      byte[] salt = Base64.getDecoder().decode(parts[2]);
      byte[] expected = Base64.getDecoder().decode(parts[3]);
      return MessageDigest.isEqual(expected, derive(password, salt, iterations));
    } catch (RuntimeException error) {
      return false;
    }
  }

  private byte[] derive(String password, byte[] salt, int iterations) {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (Exception e) {
      throw new IllegalStateException("PBKDF2 is unavailable", e);
    } finally {
      spec.clearPassword();
    }
  }
}
