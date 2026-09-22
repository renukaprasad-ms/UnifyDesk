package com.messaging.broadcast.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.event.BroadcastRunPrepareRequestedEvent;
import com.messaging.broadcast.service.BroadcastOutboxService;
import com.messaging.broadcast.service.BroadcastPreparationChunkService;
import com.messaging.kafka.consumer.KafkaEventHandler;
import com.messaging.kafka.event.KafkaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BroadcastRunPrepareEventHandler implements KafkaEventHandler {

  private final BroadcastPreparationChunkService preparationService;
  private final ObjectMapper objectMapper;

  @Override
  public boolean supports(String eventType) {
    return BroadcastOutboxService.RUN_PREPARE_EVENT.equals(eventType);
  }

  @Override
  public void handle(KafkaEvent<?> event) {
    BroadcastRunPrepareRequestedEvent payload =
        objectMapper.convertValue(event.payload(), BroadcastRunPrepareRequestedEvent.class);
    if (payload.runId() == null) {
      throw new IllegalArgumentException("Broadcast preparation event is missing runId");
    }
    preparationService.process(payload.runId(), payload.expectedAfterMemberId());
  }
}
