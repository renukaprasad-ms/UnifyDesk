package com.messaging.channel.gmail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.kafka.consumer.KafkaEventHandler;
import com.messaging.kafka.event.GmailSyncBatchRequestedEvent;
import com.messaging.kafka.event.KafkaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GmailSyncBatchEventHandler implements KafkaEventHandler {

  private final GmailSyncBatchService batchService;
  private final ObjectMapper objectMapper;

  @Override
  public boolean supports(String eventType) {
    return GmailSyncBatchPublisher.EVENT_TYPE.equals(eventType);
  }

  @Override
  public void handle(KafkaEvent<?> event) {
    GmailSyncBatchRequestedEvent payload =
        objectMapper.convertValue(event.payload(), GmailSyncBatchRequestedEvent.class);

    if (payload.batchId() == null
        || payload.jobId() == null
        || payload.connectionId() == null) {
      throw new IllegalArgumentException("Gmail batch event is incomplete");
    }

    batchService.process(payload.batchId(), payload.jobId(), payload.connectionId());
  }
}
