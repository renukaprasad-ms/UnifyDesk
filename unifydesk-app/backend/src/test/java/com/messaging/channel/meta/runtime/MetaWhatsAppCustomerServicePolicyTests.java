package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MetaWhatsAppCustomerServicePolicyTests {

  @Test
  void freeFormAllowedInsideTrustedCustomerWindow() {
    MetaWhatsAppRuntimeProperties properties = new MetaWhatsAppRuntimeProperties();
    properties.setCustomerServiceWindow(Duration.ofHours(24));
    MetaWhatsAppCustomerServicePolicy policy =
        new MetaWhatsAppCustomerServicePolicy(properties);
    Instant inbound = Instant.parse("2026-09-28T10:00:00Z");

    assertThat(policy.freeFormAllowed(inbound, inbound.plus(Duration.ofHours(23)))).isTrue();
    assertThat(policy.expiresAt(inbound)).isEqualTo(inbound.plus(Duration.ofHours(24)));
  }

  @Test
  void freeFormBlockedAtOrOutsideTrustedCustomerWindow() {
    MetaWhatsAppRuntimeProperties properties = new MetaWhatsAppRuntimeProperties();
    MetaWhatsAppCustomerServicePolicy policy =
        new MetaWhatsAppCustomerServicePolicy(properties);
    Instant inbound = Instant.parse("2026-09-28T10:00:00Z");

    assertThat(policy.freeFormAllowed(inbound, inbound.plus(Duration.ofHours(24)))).isFalse();
    assertThat(policy.freeFormAllowed(null, Instant.now())).isFalse();
  }
}
