package com.messaging.notification.platform.delivery.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.notification.platform.delivery.policy.ProviderAttemptRecoveryPolicy.RecoveryAction;
import org.junit.jupiter.api.Test;

class ProviderAttemptRecoveryPolicyTests {

  private final ProviderAttemptRecoveryPolicy policy = new ProviderAttemptRecoveryPolicy();

  @Test
  void preparedAttemptIsDefinitelyPreProviderAndCanBeReclaimed() {
    assertThat(policy.decide("PREPARED", "UNKNOWN", false)).isEqualTo(RecoveryAction.RECLAIM);
  }

  @Test
  void inFlightAttemptWithoutCompletionIsAmbiguous() {
    assertThat(policy.decide("IN_FLIGHT", "UNKNOWN", false)).isEqualTo(RecoveryAction.UNKNOWN);
  }

  @Test
  void completedAcceptedAttemptMustBeReconciledWithoutResend() {
    assertThat(policy.decide("COMPLETED", "ACCEPTED", true)).isEqualTo(RecoveryAction.ACCEPTED);
  }

  @Test
  void completedTransientAttemptRemainsRetryable() {
    assertThat(policy.decide("COMPLETED", "TRANSIENT_FAILURE", true))
        .isEqualTo(RecoveryAction.RETRY);
  }
}
