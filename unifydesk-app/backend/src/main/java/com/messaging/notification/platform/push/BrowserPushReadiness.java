package com.messaging.notification.platform.push;

import com.messaging.common.credential.CredentialCipher;
import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BrowserPushReadiness {

  private static final String READINESS_PROBE = "notification-push-readiness";

  private final NotificationDeliveryProperties properties;
  private final CredentialCipher credentialCipher;

  public boolean ready() {
    if (!properties.getPush().configured()) {
      return false;
    }
    try {
      String ciphertext = credentialCipher.encrypt(READINESS_PROBE);
      return READINESS_PROBE.equals(credentialCipher.decrypt(ciphertext));
    } catch (RuntimeException exception) {
      return false;
    }
  }
}
