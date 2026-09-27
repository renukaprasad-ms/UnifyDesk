package com.messaging.notification.platform.delivery.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.messaging.common.credential.CredentialCipher;
import org.junit.jupiter.api.Test;

class PushEncryptionReadinessTests {

  @Test
  void returnsTrueOnlyWhenCipherRoundTripWorks() {
    CredentialCipher cipher = mock(CredentialCipher.class);
    when(cipher.encrypt("notification-push-readiness")).thenReturn("ciphertext");
    when(cipher.decrypt("ciphertext")).thenReturn("notification-push-readiness");

    assertThat(new PushEncryptionReadiness(cipher).ready()).isTrue();
  }

  @Test
  void returnsFalseWithoutUsableEncryptionKey() {
    CredentialCipher cipher = mock(CredentialCipher.class);
    when(cipher.encrypt("notification-push-readiness"))
        .thenThrow(new IllegalStateException("missing key"));

    assertThat(new PushEncryptionReadiness(cipher).ready()).isFalse();
  }
}
