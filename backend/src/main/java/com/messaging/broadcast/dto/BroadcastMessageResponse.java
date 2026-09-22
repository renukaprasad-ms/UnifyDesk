package com.messaging.broadcast.dto;

import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.enums.BroadcastMessageStatus;
import java.time.Instant;

public record BroadcastMessageResponse(
    String id,
    String groupId,
    String senderId,
    String sourceTemplateId,
    Long sourceTemplateVersion,
    String legacyConnectionId,
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
        stringId(message.getSenderId()),
        stringId(message.getSourceTemplateId()),
        message.getSourceTemplateVersion(),
        stringId(message.getConnectionId()),
        run == null ? null : run.getId().toString(),
        message.getSubject(),
        message.getBodyText(),
        message.getStatus(),
        message.getScheduledAt(),
        message.getCreatedAt(),
        message.getUpdatedAt(),
        stats);
  }

  private static String stringId(Long value) {
    return value == null ? null : value.toString();
  }
}
