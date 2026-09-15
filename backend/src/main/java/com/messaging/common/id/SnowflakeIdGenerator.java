package com.messaging.common.id;

public final class SnowflakeIdGenerator {

  private static final long CUSTOM_EPOCH = 1_704_067_200_000L;
  private static final long NODE_ID_BITS = 10L;
  private static final long SEQUENCE_BITS = 12L;
  private static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1;
  private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
  private static final long NODE_ID_SHIFT = SEQUENCE_BITS;
  private static final long TIMESTAMP_SHIFT = NODE_ID_BITS + SEQUENCE_BITS;

  private final long nodeId;
  private long lastTimestamp = -1L;
  private long sequence = 0L;

  public SnowflakeIdGenerator(long nodeId) {
    if (nodeId < 0 || nodeId > MAX_NODE_ID) {
      throw new IllegalArgumentException("Node id must be between 0 and " + MAX_NODE_ID);
    }
    this.nodeId = nodeId;
  }

  public synchronized long nextId() {
    long timestamp = currentTimestamp();
    if (timestamp < lastTimestamp) {
      throw new IllegalStateException("Clock moved backwards");
    }

    if (timestamp == lastTimestamp) {
      sequence = (sequence + 1) & MAX_SEQUENCE;
      if (sequence == 0) {
        timestamp = waitForNextMillis(timestamp);
      }
    } else {
      sequence = 0L;
    }

    lastTimestamp = timestamp;
    return ((timestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT) | (nodeId << NODE_ID_SHIFT) | sequence;
  }

  public static long resolveNodeId() {
    String configuredNodeId = System.getenv("SNOWFLAKE_NODE_ID");
    if (configuredNodeId == null || configuredNodeId.isBlank()) {
      throw new IllegalStateException("SNOWFLAKE_NODE_ID environment variable is required");
    }
    return Long.parseLong(configuredNodeId.trim());
  }

  private long waitForNextMillis(long timestamp) {
    long nextTimestamp = currentTimestamp();
    while (nextTimestamp <= timestamp) {
      nextTimestamp = currentTimestamp();
    }
    return nextTimestamp;
  }

  private long currentTimestamp() {
    return System.currentTimeMillis();
  }
}
