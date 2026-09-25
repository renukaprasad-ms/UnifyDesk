package com.messaging.broadcast.dto;

import com.messaging.broadcast.entity.BroadcastSetupSession;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.enums.BroadcastSetupStatus;
import com.messaging.channel.enums.ChannelType;
import java.time.Instant;

public record BroadcastSetupSessionResponse(
    String id,
    Long companyId,
    ChannelType channelType,
    BroadcastSenderProvider provider,
    int currentStep,
    BroadcastSetupStatus status,
    String setupDataJson,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt) {

  public static BroadcastSetupSessionResponse from(BroadcastSetupSession session) {
    return new BroadcastSetupSessionResponse(
        session.getId().toString(),
        session.getCompanyId(),
        session.getChannelType(),
        session.getProvider(),
        session.getCurrentStep(),
        session.getStatus(),
        session.getSetupDataJson(),
        session.getCreatedAt(),
        session.getUpdatedAt(),
        session.getCompletedAt());
  }
}
