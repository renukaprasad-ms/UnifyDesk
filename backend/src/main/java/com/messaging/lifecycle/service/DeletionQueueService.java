package com.messaging.lifecycle.service;

import com.messaging.kafka.event.EntityDeletionRequestedEvent;
import com.messaging.kafka.event.EventMetadata;
import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.priority.MessagePriority;
import com.messaging.kafka.producer.EventPublisher;
import com.messaging.lifecycle.enums.LifecycleEntityType;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeletionQueueService {

  public static final String EVENT_TYPE = "entity.deletion.requested";

  private final EventPublisher eventPublisher;

  public void enqueue(
      LifecycleEntityType entityType,
      Long entityId,
      Long companyId,
      Long requestedByUserId) {
    EventMetadata metadata =
        EventMetadata.create(
            EVENT_TYPE,
            "1",
            companyId,
            requestedByUserId,
            null,
            null,
            MessagePriority.LOW);

    eventPublisher.publish(
        KafkaEvent.of(
            metadata,
            new EntityDeletionRequestedEvent(
                entityType, entityId, companyId, requestedByUserId, Instant.now())));
  }
}
