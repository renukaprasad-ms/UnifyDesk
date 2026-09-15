package com.messaging.kafka.event;

import com.messaging.kafka.priority.MessagePriority;
import java.time.Instant;
import java.util.UUID;

public record EventMetadata(
    String eventId,
    String eventType,
    String eventVersion,
    Instant occurredAt,
    Long companyId,
    Long userId,
    String correlationId,
    String causationId,
    MessagePriority priority) {

  public static EventMetadata create(
      String eventType,
      String eventVersion,
      Long companyId,
      Long userId,
      String correlationId,
      String causationId,
      MessagePriority priority) {
    String resolvedCorrelationId =
        correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    return new EventMetadata(
        UUID.randomUUID().toString(),
        eventType,
        eventVersion,
        Instant.now(),
        companyId,
        userId,
        resolvedCorrelationId,
        causationId,
        priority);
  }
}
