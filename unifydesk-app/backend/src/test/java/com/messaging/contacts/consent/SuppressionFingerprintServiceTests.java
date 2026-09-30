package com.messaging.contacts.consent;

import static org.junit.jupiter.api.Assertions.*;

import com.messaging.contacts.audience.ContactAudienceProperties;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import org.junit.jupiter.api.Test;

class SuppressionFingerprintServiceTests {

  @Test
  void fingerprintIsKeyedAndDoesNotContainPlaintext() {
    ContactAudienceProperties properties = new ContactAudienceProperties();
    properties.setSuppressionHmacSecret("test-secret-only");
    properties.setSuppressionHmacKeyVersion(7);
    SuppressionFingerprintService service = new SuppressionFingerprintService(properties);

    var fingerprint = service.fingerprint(Channel.EMAIL, "person@example.com");

    assertEquals(7, fingerprint.keyVersion());
    assertFalse(fingerprint.value().contains("person@example.com"));
    assertEquals(fingerprint.value(), service.fingerprint(Channel.EMAIL, "person@example.com").value());
    assertNotEquals(fingerprint.value(), service.fingerprint(Channel.SMS, "person@example.com").value());
  }
}
