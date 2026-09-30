package com.messaging.contacts.consent;

import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.EligibilityDecision;
import com.messaging.contacts.consent.ConsentTypes.EligibilityReason;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSource;
import com.messaging.contacts.consent.ConsentTypes.PreferenceState;
import com.messaging.contacts.consent.ConsentTypes.Purpose;
import com.messaging.contacts.consent.ConsentTypes.SuppressionReason;
import java.time.Instant;

public final class ConsentApiModel {

  private ConsentApiModel() {}

  public record PreferenceRequest(
      PreferenceState state,
      String identityId,
      PreferenceSource source,
      String sourceRef,
      Instant occurredAt,
      String reason,
      String idempotencyKey) {}

  public record PreferenceResponse(
      String id,
      Channel channel,
      Purpose purpose,
      PreferenceState state,
      String identityId,
      PreferenceSource source,
      String sourceRef,
      Instant occurredAt,
      Instant updatedAt,
      String reason,
      long version) {}

  public record SuppressionRequest(
      String identityId,
      Channel channel,
      SuppressionReason reasonCode,
      String sourceRef,
      Instant occurredAt,
      String note,
      String idempotencyKey) {}

  public record LiftSuppressionRequest(String reason, String idempotencyKey) {}

  public record SuppressionResponse(
      String id,
      Channel channel,
      String identityId,
      SuppressionReason reasonCode,
      PreferenceSource source,
      String sourceRef,
      String state,
      Instant createdAt,
      Instant liftedAt,
      boolean liftable,
      boolean active,
      long version) {}

  public record EligibilityResponse(
      EligibilityDecision decision,
      EligibilityReason reason,
      String identityId,
      String normalizedDestination,
      String policyVersion,
      Instant evaluatedAt) {}
}
