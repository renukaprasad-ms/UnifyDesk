package com.messaging.notification.platform.delivery.push;

import com.messaging.common.credential.CredentialCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PushEncryptionReadiness {

  private static final String READINESS_PROBE = "notification-push-readiness";

  private final CredentialCipher credentialCipher;

  public boolean ready() {
    try {
      String ciphertext = credentialCipher.encrypt(READINESS_PROBE);
      return READINESS_PROBE.equals(credentialCipher.decrypt(ciphertext));
    } catch (RuntimeException exception) {
      return false;
    }
  }
}
