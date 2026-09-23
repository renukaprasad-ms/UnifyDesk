package com.messaging.broadcast.dto;

import com.messaging.broadcast.entity.BroadcastGroupMember;
import com.messaging.broadcast.enums.BroadcastGroupMemberStatus;
import java.time.Instant;

public record BroadcastMemberResponse(
    String id,
    String destination,
    String displayName,
    String metadataJson,
    BroadcastGroupMemberStatus status,
    Instant createdAt) {

  public static BroadcastMemberResponse from(BroadcastGroupMember member) {
    return new BroadcastMemberResponse(
        member.getId().toString(),
        member.getDestination(),
        member.getDisplayName(),
        member.getMetadataJson(),
        member.getStatus(),
        member.getCreatedAt());
  }
}
