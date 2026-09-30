package com.messaging.contacts.consent;

import java.time.Instant;

public final class ConsentTypes {

  private ConsentTypes() {}

  public enum Channel {
    EMAIL,
    SMS,
    WHATSAPP
  }

  public enum Purpose {
    MARKETING
  }

  public enum PreferenceState {
    UNKNOWN,
    OPTED_IN,
    OPTED_OUT
  }

  public enum PreferenceSource {
    USER_UI,
    CONTACT_ACTION,
    IMPORT,
    API,
    INBOX,
    MANAGED_EMAIL_EVENT,
    MANAGED_SMS_EVENT,
    META_WHATSAPP,
    SYSTEM_MIGRATION
  }

  public enum ActorType {
    USER,
    CONTACT,
    IMPORT,
    API,
    PROVIDER,
    SYSTEM
  }

  public enum SuppressionReason {
    MANUAL,
    HARD_BOUNCE,
    COMPLAINT,
    PROVIDER_SUPPRESSED,
    DND,
    INVALID_DESTINATION,
    ABUSE_POLICY,
    LEGAL_POLICY
  }

  public enum SuppressionState {
    ACTIVE,
    LIFTED
  }

  public enum EligibilityDecision {
    ELIGIBLE,
    INELIGIBLE
  }

  public enum EligibilityReason {
    ELIGIBLE,
    CONTACT_INACTIVE,
    IDENTITY_MISSING,
    IDENTITY_MISMATCH,
    OPTED_OUT,
    CONSENT_REQUIRED,
    SUPPRESSED,
    POLICY_BLOCKED
  }

  public record PreferenceCommand(
      long companyId,
      long contactId,
      Long identityId,
      Channel channel,
      Purpose purpose,
      PreferenceState state,
      PreferenceSource source,
      String sourceRef,
      ActorType actorType,
      String actorId,
      String reason,
      Instant occurredAt,
      String idempotencyKey) {}

  public record PreferenceSnapshot(
      long id,
      long contactId,
      Long identityId,
      Channel channel,
      Purpose purpose,
      PreferenceState state,
      PreferenceSource source,
      String sourceRef,
      Instant occurredAt,
      Instant updatedAt,
      long version) {}

  public record SuppressionCommand(
      long companyId,
      long contactId,
      Long identityId,
      Channel channel,
      SuppressionReason reasonCode,
      PreferenceSource source,
      String sourceRef,
      ActorType actorType,
      String actorId,
      String reason,
      Instant occurredAt,
      String idempotencyKey) {}

  public record SuppressionSnapshot(
      long id,
      Long contactId,
      Long identityId,
      String identityFingerprint,
      Integer fingerprintKeyVersion,
      Channel channel,
      SuppressionReason reasonCode,
      PreferenceSource source,
      String sourceRef,
      SuppressionState state,
      Instant suppressedAt,
      Instant liftedAt,
      long version) {}

  public record ContactChannelIdentity(
      long contactId,
      Long identityId,
      String normalizedDestination,
      String displayName) {}

  public record EligibilityContext(String provider, String jurisdiction, String policyVersionHint) {}

  public record EligibilityResult(
      EligibilityDecision decision,
      EligibilityReason reason,
      Long identityId,
      String normalizedDestination,
      String policyVersion,
      Instant evaluatedAt) {}
}
