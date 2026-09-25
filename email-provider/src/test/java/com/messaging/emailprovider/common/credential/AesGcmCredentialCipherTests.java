package com.messaging.emailprovider.common.credential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmCredentialCipherTests {

  private final AesGcmCredentialCipher cipher =
      new AesGcmCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));

  @Test
  void encryptsAndDecryptsCredential() {
    String plaintext = "private-dkim-key";
    String encrypted = cipher.encrypt(plaintext);

    assertNotEquals(plaintext, encrypted);
    assertEquals(plaintext, cipher.decrypt(encrypted));
  }

  @Test
  void usesUniqueIvPerEncryption() {
    String first = cipher.encrypt("same-value");
    String second = cipher.encrypt("same-value");

    assertNotEquals(first, second);
  }
}
