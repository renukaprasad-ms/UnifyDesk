package com.messaging.lifecycle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.kafka.consumer.KafkaEventHandler;
import com.messaging.kafka.event.EntityDeletionRequestedEvent;
import com.messaging.kafka.event.KafkaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeletionCleanupEventHandler implements KafkaEventHandler {

  private final ObjectMapper objectMapper;
  private final DeletionCleanupService cleanupService;

  @Override
  public boolean supports(String eventType) {
    return DeletionQueueService.EVENT_TYPE.equals(eventType);
  }

  @Override
  public void handle(KafkaEvent<?> event) {
    cleanupService.cleanup(
        objectMapper.convertValue(event.payload(), EntityDeletionRequestedEvent.class));
  }
}
