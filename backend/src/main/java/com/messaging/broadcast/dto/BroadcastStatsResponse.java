package com.messaging.broadcast.dto;

import com.messaging.broadcast.entity.BroadcastStats;

public record BroadcastStatsResponse(
    long total,
    long pending,
    long processing,
    long accepted,
    long failed,
    long bounced,
    long unknown,
    long skipped) {

  public static BroadcastStatsResponse from(BroadcastStats stats) {
    if (stats == null) return new BroadcastStatsResponse(0, 0, 0, 0, 0, 0, 0, 0);
    return new BroadcastStatsResponse(
        stats.getTotalCount(),
        stats.getPendingCount(),
        stats.getProcessingCount(),
        stats.getAcceptedCount(),
        stats.getFailedCount(),
        stats.getBouncedCount(),
        stats.getUnknownCount(),
        stats.getSkippedCount());
  }

  public long processed() {
    return accepted + failed + bounced + unknown + skipped;
  }
}
