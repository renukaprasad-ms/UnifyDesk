package com.messaging.broadcast.dto;

import com.messaging.broadcast.entity.BroadcastGroup;
import com.messaging.channel.enums.ChannelType;
import java.time.Instant;

public record BroadcastGroupResponse(
    String id,
    String companyId,
    String name,
    String description,
    ChannelType channelType,
    long memberCount,
    Instant createdAt,
    Instant updatedAt) {

  public static BroadcastGroupResponse from(BroadcastGroup group) {
    return new BroadcastGroupResponse(
        group.getId().toString(),
        group.getCompanyId().toString(),
        group.getName(),
        group.getDescription(),
        group.getChannelType(),
        group.getMemberCount(),
        group.getCreatedAt(),
        group.getUpdatedAt());
  }
}
