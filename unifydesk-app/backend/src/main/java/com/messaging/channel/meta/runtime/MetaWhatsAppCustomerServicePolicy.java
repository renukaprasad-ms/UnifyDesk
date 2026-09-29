package com.messaging.channel.meta.runtime;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class MetaWhatsAppCustomerServicePolicy {

  private final Duration window;

  public MetaWhatsAppCustomerServicePolicy(MetaWhatsAppRuntimeProperties properties) {
    this.window = properties.getCustomerServiceWindow();
  }

  public Instant expiresAt(Instant lastCustomerMessageAt) {
    return lastCustomerMessageAt == null ? null : lastCustomerMessageAt.plus(window);
  }

  public boolean freeFormAllowed(Instant lastCustomerMessageAt, Instant now) {
    Instant expiresAt = expiresAt(lastCustomerMessageAt);
    return expiresAt != null && now.isBefore(expiresAt);
  }
}
