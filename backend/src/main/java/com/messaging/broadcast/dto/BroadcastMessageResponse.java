package com.messaging.broadcast.dto;

import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.enums.BroadcastMessageStatus;
import java.time.Instant;

public record BroadcastMessageResponse(
    String id,
    String groupId,
    String connectionId,
    String runId,
    String subject,
    String bodyText,
    BroadcastMessageStatus status,
    Instant scheduledAt,
    Instant createdAt,
    Instant updatedAt,
    BroadcastStatsResponse stats) {

  public static BroadcastMessageResponse from(
      BroadcastMessage message, BroadcastRun run, BroadcastStatsResponse stats) {
    return new BroadcastMessageResponse(
        message.getId().toString(),
        message.getGroupId().toString(),
        message.getConnectionId().toString(),
        run == null ? null : run.getId().toString(),
        message.getSubject(),
        message.getBodyText(),
        message.getStatus(),
        message.getScheduledAt(),
        message.getCreatedAt(),
        message.getUpdatedAt(),
        stats);
  }
}
