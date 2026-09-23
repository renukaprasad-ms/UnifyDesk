package com.messaging.channel.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.common.credential.AesGcmCredentialCipher;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmCredentialCipherTests {

  @Test
  void encryptsAndDecryptsCredentialPayload() {
    String key =
        Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    AesGcmCredentialCipher cipher = new AesGcmCredentialCipher(key);

    String encrypted = cipher.encrypt("secret-token");

    assertThat(encrypted).isNotEqualTo("secret-token");
    assertThat(cipher.decrypt(encrypted)).isEqualTo("secret-token");
  }

  @Test
  void producesDifferentCiphertextForSamePayload() {
    String key =
        Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    AesGcmCredentialCipher cipher = new AesGcmCredentialCipher(key);

    assertThat(cipher.encrypt("same-secret")).isNotEqualTo(cipher.encrypt("same-secret"));
  }

  @Test
  void rejectsInvalidMasterKeyLength() {
    String key = Base64.getEncoder().encodeToString("short-key".getBytes(StandardCharsets.UTF_8));
    AesGcmCredentialCipher cipher = new AesGcmCredentialCipher(key);

    assertThatThrownBy(() -> cipher.encrypt("secret"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("exactly 32 bytes");
  }
}
