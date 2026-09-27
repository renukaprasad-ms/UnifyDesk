package com.messaging.notification.platform.delivery.policy;

import org.springframework.stereotype.Component;

@Component
public class ProviderAttemptRecoveryPolicy {

  public RecoveryAction decide(String providerCallState, String outcome, boolean completed) {
    if (providerCallState == null) {
      return RecoveryAction.UNKNOWN;
    }
    return switch (providerCallState) {
      case "PREPARED" -> RecoveryAction.RECLAIM;
      case "IN_FLIGHT" -> completed ? completedOutcome(outcome) : RecoveryAction.UNKNOWN;
      case "COMPLETED" -> completed ? completedOutcome(outcome) : RecoveryAction.UNKNOWN;
      default -> RecoveryAction.UNKNOWN;
    };
  }

  private RecoveryAction completedOutcome(String outcome) {
    if (outcome == null) {
      return RecoveryAction.UNKNOWN;
    }
    return switch (outcome) {
      case "ACCEPTED" -> RecoveryAction.ACCEPTED;
      case "TRANSIENT_FAILURE", "RATE_LIMITED" -> RecoveryAction.RETRY;
      case "PERMANENT_FAILURE" -> RecoveryAction.PERMANENT_FAILURE;
      case "SUPPRESSED_POLICY" -> RecoveryAction.SUPPRESSED;
      case "UNKNOWN" -> RecoveryAction.UNKNOWN;
      default -> RecoveryAction.UNKNOWN;
    };
  }

  public enum RecoveryAction {
    RECLAIM,
    ACCEPTED,
    RETRY,
    PERMANENT_FAILURE,
    SUPPRESSED,
    UNKNOWN
  }
}
