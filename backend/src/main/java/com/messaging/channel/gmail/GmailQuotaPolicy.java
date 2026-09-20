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

    long numerator = Math.multiplyExact(unitsPerBatch, 60_000L);
    long intervalMs = Math.max(1, (numerator + usableQuota - 1) / usableQuota);
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
