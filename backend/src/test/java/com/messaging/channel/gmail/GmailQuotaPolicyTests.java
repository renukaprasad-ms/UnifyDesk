package com.messaging.channel.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class GmailQuotaPolicyTests {

  @Test
  void calculatesSafeIntervalFromPerUserQuota() {
    GmailOAuthProperties properties = new GmailOAuthProperties();
    properties.setSyncBatchSize(50);
    properties.setSyncUserQuotaUnitsPerMinute(6000);
    properties.setSyncMessageGetQuotaUnits(20);
    properties.setSyncQuotaUtilizationPercent(90);

    GmailQuotaPolicy policy = new GmailQuotaPolicy(properties);

    assertThat(policy.batchInterval().toMillis()).isEqualTo(11112L);
  }

  @Test
  void schedulesLaterBatchesFromRunCreationTime() {
    GmailOAuthProperties properties = new GmailOAuthProperties();
    properties.setSyncBatchSize(50);
    properties.setSyncUserQuotaUnitsPerMinute(6000);
    properties.setSyncMessageGetQuotaUnits(20);
    properties.setSyncQuotaUtilizationPercent(100);

    GmailQuotaPolicy policy = new GmailQuotaPolicy(properties);
    Instant createdAt = Instant.parse("2026-09-19T16:00:00Z");

    assertThat(policy.initialEligibility(createdAt, 0)).isEqualTo(createdAt);
    assertThat(policy.initialEligibility(createdAt, 2))
        .isEqualTo(createdAt.plusSeconds(20));
  }
}
