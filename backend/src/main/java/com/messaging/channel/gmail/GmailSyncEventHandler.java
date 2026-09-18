package com.messaging.channel.gmail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.kafka.consumer.KafkaEventHandler;
import com.messaging.kafka.event.GmailSyncRequestedEvent;
import com.messaging.kafka.event.KafkaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GmailSyncEventHandler implements KafkaEventHandler {

  private final GmailSyncService syncService;
  private final ObjectMapper objectMapper;

  @Override
  public boolean supports(String eventType) {
    return GmailSyncQueueService.EVENT_TYPE.equals(eventType);
  }

  @Override
  public void handle(KafkaEvent<?> event) {
    GmailSyncRequestedEvent payload =
        objectMapper.convertValue(event.payload(), GmailSyncRequestedEvent.class);

    if (event.companyId() == null || payload.connectionId() == null) {
      throw new IllegalArgumentException("Gmail sync event is missing company or connection id");
    }

    syncService.sync(event.companyId(), payload.connectionId());
  }
}
