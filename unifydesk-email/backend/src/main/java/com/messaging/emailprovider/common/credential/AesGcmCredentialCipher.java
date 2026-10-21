package com.messaging.emailprovider.common.credential;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AesGcmCredentialCipher implements CredentialCipher {

  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final String configuredKey;

  public AesGcmCredentialCipher(
      @Value("${app.security.credentials.master-key:}") String configuredKey) {
    this.configuredKey = configuredKey;
  }

  @Override
  public String encrypt(String plaintext) {
    if (plaintext == null) {
      throw new IllegalArgumentException("Credential payload cannot be null");
    }
    try {
      byte[] iv = new byte[IV_LENGTH];
      SECURE_RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder()
          .encodeToString(
              ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to encrypt email-provider credential", exception);
    }
  }

  @Override
  public String decrypt(String ciphertext) {
    if (ciphertext == null || ciphertext.isBlank()) {
      throw new IllegalArgumentException("Encrypted credential payload cannot be blank");
    }
    try {
      byte[] payload = Base64.getDecoder().decode(ciphertext);
      if (payload.length <= IV_LENGTH) {
        throw new IllegalArgumentException("Encrypted credential payload is invalid");
      }

      byte[] iv = new byte[IV_LENGTH];
      byte[] encrypted = new byte[payload.length - IV_LENGTH];
      System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
      System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new IllegalStateException("Unable to decrypt email-provider credential", exception);
    }
  }

  private SecretKeySpec key() {
    if (configuredKey == null || configuredKey.isBlank()) {
      throw new IllegalStateException(
          "app.security.credentials.master-key must be configured before storing DKIM keys");
    }

    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(configuredKey);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(
          "app.security.credentials.master-key must be Base64 encoded", exception);
    }

    if (decoded.length != 32) {
      throw new IllegalStateException(
          "app.security.credentials.master-key must decode to exactly 32 bytes for AES-256");
    }
    return new SecretKeySpec(decoded, "AES");
  }
}
