package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MetaWhatsAppMessageStateMachineTests {
  private final MetaWhatsAppMessageStateMachine stateMachine =
      new MetaWhatsAppMessageStateMachine();

  @Test
  void deliveredCannotRegressToSent() {
    boolean apply =
        stateMachine.shouldApply(
            "DELIVERED",
            30,
            Instant.parse("2026-01-01T10:00:00Z"),
            stateMachine.providerState("sent"),
            Instant.parse("2026-01-01T10:01:00Z"));

    assertThat(apply).isFalse();
  }

  @Test
  void duplicateStatusAtSameProviderTimestampIsIgnored() {
    var delivered = stateMachine.providerState("delivered");
    boolean apply =
        stateMachine.shouldApply(
            "DELIVERED",
            30,
            Instant.parse("2026-01-01T10:00:00Z"),
            delivered,
            Instant.parse("2026-01-01T10:00:00Z"));

    assertThat(apply).isFalse();
  }

  @Test
  void readCannotRegressToLateFailure() {
    boolean apply =
        stateMachine.shouldApply(
            "READ",
            40,
            Instant.parse("2026-01-01T10:00:00Z"),
            stateMachine.providerState("failed"),
            Instant.parse("2026-01-01T10:02:00Z"));

    assertThat(apply).isFalse();
  }


  @Test
  void olderFailureCannotOverwriteNewerSentState() {
    boolean apply =
        stateMachine.shouldApply(
            "SENT",
            20,
            Instant.parse("2026-01-01T10:02:00Z"),
            stateMachine.providerState("failed"),
            Instant.parse("2026-01-01T10:01:00Z"));

    assertThat(apply).isFalse();
  }

  @Test
  void sentCanAdvanceToDeliveredAndRead() {
    assertThat(
            stateMachine.shouldApply(
                "SENT",
                20,
                Instant.parse("2026-01-01T10:00:00Z"),
                stateMachine.providerState("delivered"),
                Instant.parse("2026-01-01T10:01:00Z")))
        .isTrue();
    assertThat(stateMachine.providerState("read").rank()).isGreaterThan(30);
  }
}
