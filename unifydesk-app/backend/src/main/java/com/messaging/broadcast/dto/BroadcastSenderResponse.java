package com.messaging.broadcast.dto;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.*;
import java.time.Instant;

public record BroadcastSenderResponse(
    String id,
    String connectionId,
    String channelConnectionId,
    BroadcastSenderProvider provider,
    BroadcastSenderManagementMode managementMode,
    BroadcastSenderStatus status,
    String fromAddress,
    String displayName,
    String replyToAddress,
    String connectionName,
    String region,
    boolean productionAccessEnabled,
    boolean sendingEnabled,
    Double max24HourSend,
    Double maxSendRate,
    Double sentLast24Hours,
    Instant lastValidatedAt) {

  public static BroadcastSenderResponse from(
      BroadcastSender sender, BroadcastSenderConnection connection) {
    boolean managed =
        connection.getManagementMode() == BroadcastSenderManagementMode.MESSAGING_MANAGED;
    return new BroadcastSenderResponse(
        sender.getId().toString(),
        connection.getId().toString(),
        connection.getChannelConnectionId().toString(),
        sender.getProvider(),
        connection.getManagementMode(),
        sender.getStatus(),
        sender.getFromAddress(),
        sender.getDisplayName(),
        sender.getReplyToAddress(),
        connection.getName(),
        managed ? "MANAGED" : connection.getRegion(),
        managed ? connection.isSendingEnabled() : connection.isProductionAccessEnabled(),
        connection.isSendingEnabled(),
        managed ? null : connection.getMax24HourSend(),
        managed ? null : connection.getMaxSendRate(),
        managed ? null : connection.getSentLast24Hours(),
        managed ? null : connection.getLastValidatedAt());
  }
}
