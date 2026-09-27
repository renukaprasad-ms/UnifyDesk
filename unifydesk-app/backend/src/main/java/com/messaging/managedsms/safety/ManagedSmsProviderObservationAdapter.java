package com.messaging.managedsms.safety;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface ManagedSmsProviderObservationAdapter {

  Optional<ProviderObservation> observe(String providerAccountKey, String providerClientRef);

  record ProviderObservation(
      String providerAccountKey,
      String providerClientRef,
      BigDecimal balance,
      BigDecimal creditLimit,
      BigDecimal maxSendRate,
      boolean sendingEnabled,
      Instant observedAt,
      Instant validUntil,
      String source) {}
}
