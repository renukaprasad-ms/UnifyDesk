package com.messaging.channel.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class GmailQuotaPolicyTests {

  @Test
  void calculatesBackgroundIntervalWithInteractiveReserve() {
    GmailOAuthProperties properties = new GmailOAuthProperties();
    properties.setSyncBatchSize(25);
    properties.setSyncUserQuotaUnitsPerMinute(6000);
    properties.setSyncMessageGetQuotaUnits(20);
    properties.setSyncQuotaUtilizationPercent(80);
    properties.setQuotaInteractiveReservePercent(20);

    GmailQuotaPolicy policy = new GmailQuotaPolicy(properties);

    // 25 * 20 = 500 units. Background budget is
    // 6000 * 80% safety * 80% background = 3840 units/minute.
    assertThat(policy.batchInterval().toMillis()).isEqualTo(7813L);
  }

  @Test
  void schedulesLaterBatchesFromRunCreationTime() {
    GmailOAuthProperties properties = new GmailOAuthProperties();
    properties.setSyncBatchSize(50);
    properties.setSyncUserQuotaUnitsPerMinute(6000);
    properties.setSyncMessageGetQuotaUnits(20);
    properties.setSyncQuotaUtilizationPercent(100);
    properties.setQuotaInteractiveReservePercent(0);

    GmailQuotaPolicy policy = new GmailQuotaPolicy(properties);
    Instant createdAt = Instant.parse("2026-09-19T16:00:00Z");

    assertThat(policy.initialEligibility(createdAt, 0)).isEqualTo(createdAt);
    assertThat(policy.initialEligibility(createdAt, 2))
        .isEqualTo(createdAt.plusSeconds(20));
  }
}
