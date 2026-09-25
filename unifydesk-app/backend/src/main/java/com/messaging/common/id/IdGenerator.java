package com.messaging.common.id;

public final class IdGenerator {

  private static final SnowflakeIdGenerator SNOWFLAKE =
      new SnowflakeIdGenerator(SnowflakeIdGenerator.resolveNodeId());

  private IdGenerator() {}

  public static long nextId() {
    return SNOWFLAKE.nextId();
  }
}
