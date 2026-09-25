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
        connection.getRegion(),
        connection.isProductionAccessEnabled(),
        connection.isSendingEnabled(),
        connection.getMax24HourSend(),
        connection.getMaxSendRate(),
        connection.getSentLast24Hours(),
        connection.getLastValidatedAt());
  }
}
