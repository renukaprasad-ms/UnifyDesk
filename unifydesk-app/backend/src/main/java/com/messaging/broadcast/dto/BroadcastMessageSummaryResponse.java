package com.messaging.broadcast.dto;

import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.enums.BroadcastAudienceSourceType;
import com.messaging.broadcast.enums.BroadcastMessageStatus;
import com.messaging.channel.enums.ChannelType;
import java.time.Instant;

public record BroadcastMessageSummaryResponse(
    String id,
    String groupId,
    BroadcastAudienceSourceType audienceSourceType,
    String audienceSourceId,
    String audienceSnapshotId,
    ChannelType channelType,
    String senderId,
    String sourceTemplateId,
    Long sourceTemplateVersion,
    String legacyConnectionId,
    String runId,
    String subject,
    String bodyPreview,
    BroadcastMessageStatus status,
    Instant scheduledAt,
    Instant createdAt,
    Instant updatedAt,
    BroadcastStatsResponse stats) {

  public static BroadcastMessageSummaryResponse from(
      BroadcastMessage message, BroadcastRun run, BroadcastStatsResponse stats) {
    return new BroadcastMessageSummaryResponse(
        message.getId().toString(),
        stringId(message.getGroupId()),
        message.getAudienceSourceType(),
        stringId(message.getAudienceSourceId()),
        run == null
            ? stringId(message.getFrozenAudienceSnapshotId())
            : stringId(run.getAudienceSnapshotId()),
        message.getChannelType(),
        stringId(message.getSenderId()),
        stringId(message.getSourceTemplateId()),
        message.getSourceTemplateVersion(),
        stringId(message.getConnectionId()),
        run == null ? null : run.getId().toString(),
        message.getSubject(),
        preview(message.getBodyText()),
        message.getStatus(),
        message.getScheduledAt(),
        message.getCreatedAt(),
        message.getUpdatedAt(),
        stats);
  }

  private static String stringId(Long value) {
    return value == null ? null : value.toString();
  }

  private static String preview(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.replaceAll("\\s+", " ").trim();
    return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
  }
}
