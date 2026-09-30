package com.messaging.broadcast.dto;

import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.enums.BroadcastAudienceSourceType;
import com.messaging.broadcast.enums.BroadcastMessageStatus;
import com.messaging.channel.enums.ChannelType;
import java.time.Instant;
import java.util.Map;

public record BroadcastMessageResponse(
    String id,
    String groupId,
    BroadcastAudienceSourceType audienceSourceType,
    String audienceSourceId,
    String audienceSnapshotId,
    String senderId,
    String sourceTemplateId,
    Long sourceTemplateVersion,
    String managedSmsTemplateId,
    Long managedSmsTemplateVersion,
    String legacyConnectionId,
    String runId,
    String subject,
    String bodyText,
    String bodyHtml,
    Map<String, String> templateVariables,
    BroadcastMessageStatus status,
    Instant scheduledAt,
    Instant createdAt,
    Instant updatedAt,
    BroadcastStatsResponse stats) {

  public BroadcastMessageResponse(
      String id,
      String groupId,
      String senderId,
      String sourceTemplateId,
      Long sourceTemplateVersion,
      String managedSmsTemplateId,
      Long managedSmsTemplateVersion,
      String legacyConnectionId,
      String runId,
      String subject,
      String bodyText,
      String bodyHtml,
      Map<String, String> templateVariables,
      BroadcastMessageStatus status,
      Instant scheduledAt,
      Instant createdAt,
      Instant updatedAt,
      BroadcastStatsResponse stats) {
    this(
        id,
        groupId,
        null,
        null,
        null,
        senderId,
        sourceTemplateId,
        sourceTemplateVersion,
        managedSmsTemplateId,
        managedSmsTemplateVersion,
        legacyConnectionId,
        runId,
        subject,
        bodyText,
        bodyHtml,
        templateVariables,
        status,
        scheduledAt,
        createdAt,
        updatedAt,
        stats);
  }

  public BroadcastMessageResponse(
      String id,
      String groupId,
      String senderId,
      String sourceTemplateId,
      Long sourceTemplateVersion,
      String legacyConnectionId,
      String runId,
      String subject,
      String bodyText,
      String bodyHtml,
      Map<String, String> templateVariables,
      BroadcastMessageStatus status,
      Instant scheduledAt,
      Instant createdAt,
      Instant updatedAt,
      BroadcastStatsResponse stats) {
    this(
        id,
        groupId,
        null,
        null,
        null,
        senderId,
        sourceTemplateId,
        sourceTemplateVersion,
        null,
        null,
        legacyConnectionId,
        runId,
        subject,
        bodyText,
        bodyHtml,
        templateVariables,
        status,
        scheduledAt,
        createdAt,
        updatedAt,
        stats);
  }

  public static BroadcastMessageResponse from(
      BroadcastMessage message,
      BroadcastRun run,
      BroadcastStatsResponse stats,
      Map<String, String> templateVariables) {
    boolean managedSms =
        message.getChannelType() == ChannelType.SMS && message.getSourceTemplateId() != null;
    return new BroadcastMessageResponse(
        message.getId().toString(),
        stringId(message.getGroupId()),
        message.getAudienceSourceType(),
        stringId(message.getAudienceSourceId()),
        run == null ? null : stringId(run.getAudienceSnapshotId()),
        stringId(message.getSenderId()),
        stringId(message.getSourceTemplateId()),
        message.getSourceTemplateVersion(),
        managedSms ? stringId(message.getSourceTemplateId()) : null,
        managedSms ? message.getSourceTemplateVersion() : null,
        stringId(message.getConnectionId()),
        run == null ? null : run.getId().toString(),
        message.getSubject(),
        message.getBodyText(),
        message.getBodyHtml(),
        templateVariables,
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
