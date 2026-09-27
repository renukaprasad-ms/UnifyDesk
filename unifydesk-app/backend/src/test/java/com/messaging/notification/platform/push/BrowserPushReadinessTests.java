package com.messaging.notification.platform.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.messaging.common.credential.CredentialCipher;
import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import org.junit.jupiter.api.Test;

class BrowserPushReadinessTests {

  @Test
  void returnsTrueOnlyWhenVapidAndCipherRoundTripAreReady() {
    NotificationDeliveryProperties properties = configuredProperties();
    CredentialCipher cipher = mock(CredentialCipher.class);
    when(cipher.encrypt("notification-push-readiness")).thenReturn("ciphertext");
    when(cipher.decrypt("ciphertext")).thenReturn("notification-push-readiness");

    assertThat(new BrowserPushReadiness(properties, cipher).ready()).isTrue();
  }

  @Test
  void returnsFalseWhenCipherIsUnavailable() {
    NotificationDeliveryProperties properties = configuredProperties();
    CredentialCipher cipher = mock(CredentialCipher.class);
    when(cipher.encrypt("notification-push-readiness"))
        .thenThrow(new IllegalStateException("missing key"));

    assertThat(new BrowserPushReadiness(properties, cipher).ready()).isFalse();
  }

  @Test
  void returnsFalseBeforeCipherProbeWhenVapidIsIncomplete() {
    NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
    CredentialCipher cipher = mock(CredentialCipher.class);

    assertThat(new BrowserPushReadiness(properties, cipher).ready()).isFalse();
    verifyNoInteractions(cipher);
  }

  private NotificationDeliveryProperties configuredProperties() {
    NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
    properties.getPush().setVapidPublicKey("public");
    properties.getPush().setVapidPrivateKey("private");
    return properties;
  }
}
