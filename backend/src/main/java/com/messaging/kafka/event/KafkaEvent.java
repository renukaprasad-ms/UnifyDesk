package com.messaging.kafka.event;

import com.messaging.kafka.priority.MessagePriority;
import java.time.Instant;

public record KafkaEvent<T>(
    String eventId,
    String eventType,
    String eventVersion,
    Instant occurredAt,
    Long companyId,
    Long userId,
    String correlationId,
    String causationId,
    MessagePriority priority,
    T payload) {

  public static <T> KafkaEvent<T> of(EventMetadata metadata, T payload) {
    return new KafkaEvent<>(
        metadata.eventId(),
        metadata.eventType(),
        metadata.eventVersion(),
        metadata.occurredAt(),
        metadata.companyId(),
        metadata.userId(),
        metadata.correlationId(),
        metadata.causationId(),
        metadata.priority(),
        payload);
  }
}
