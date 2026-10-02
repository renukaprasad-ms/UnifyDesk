package com.messaging.broadcast.dto;

import com.messaging.broadcast.dto.BroadcastChannelReadinessResponse.SenderReadiness;
import com.messaging.channel.enums.ChannelType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record BroadcastAudiencePreviewResponse(
    ChannelType channel,
    String purpose,
    BroadcastExpectedSourceDescriptor sourceDescriptor,
    long selectedCount,
    long eligibleCount,
    long excludedCount,
    Map<ExclusionReason, Long> exclusions,
    List<RecipientSample> sampleEligible,
    List<RecipientSample> sampleExcluded,
    SenderReadiness senderReadiness,
    Instant computedAt) {

  public enum ExclusionReason {
    DUPLICATE,
    MISSING_IDENTITY,
    INVALID_IDENTITY,
    OPTED_OUT,
    CONSENT_REQUIRED,
    SUPPRESSED,
    CHANNEL_INELIGIBLE
  }

  public record RecipientSample(
      String contactId, String maskedDestination, String reason) {}
}
