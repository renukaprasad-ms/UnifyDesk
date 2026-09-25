package com.messaging.broadcast.dto;

import com.messaging.broadcast.enums.BroadcastRunStatus;

public record BroadcastProgressEvent(
    String runId,
    String broadcastId,
    String groupId,
    BroadcastRunStatus status,
    BroadcastStatsResponse stats,
    int progressPercent) {}
