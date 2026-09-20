package com.messaging.channel.gmail;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GmailQuotaPolicy {

  private final GmailOAuthProperties properties;

  public Duration batchInterval() {
    int batchSize =
        Math.min(GmailApiClient.MAX_BATCH_SIZE, Math.max(1, properties.getSyncBatchSize()));
    long unitsPerBatch =
        Math.multiplyExact((long) batchSize, Math.max(1, properties.getSyncMessageGetQuotaUnits()));

    long configuredQuota = Math.max(1, properties.getSyncUserQuotaUnitsPerMinute());
    int utilization = Math.clamp(properties.getSyncQuotaUtilizationPercent(), 1, 100);
    long usableQuota = Math.max(1, (configuredQuota * utilization) / 100);
    int reserve = Math.clamp(properties.getQuotaInteractiveReservePercent(), 0, 90);
    long backgroundQuota = Math.max(1, (usableQuota * (100L - reserve)) / 100L);

    long numerator = Math.multiplyExact(unitsPerBatch, 60_000L);
    long intervalMs = Math.max(1, (numerator + backgroundQuota - 1) / backgroundQuota);
    return Duration.ofMillis(intervalMs);
  }

  public Instant initialEligibility(Instant runCreatedAt, int batchIndex) {
    Instant base = runCreatedAt == null ? Instant.now() : runCreatedAt;
    if (batchIndex <= 0) {
      return base;
    }
    try {
      return base.plus(batchInterval().multipliedBy(batchIndex));
    } catch (ArithmeticException exception) {
      return Instant.now().plus(batchInterval());
    }
  }

  public Instant nextEligibility() {
    return Instant.now().plus(batchInterval());
  }
}
