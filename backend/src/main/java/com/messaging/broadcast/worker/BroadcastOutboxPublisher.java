package com.messaging.broadcast.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.event.BroadcastRunPrepareRequestedEvent;
import com.messaging.broadcast.service.*;
import com.messaging.kafka.event.EventMetadata;
import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.priority.MessagePriority;
import com.messaging.kafka.producer.EventPublisher;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastOutboxPublisher {

  private final BroadcastOutboxClaimService claimService;
  private final EventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  @Scheduled(fixedDelayString = "${app.broadcast.outbox-poll-interval-ms:250}")
  public void publish() {
    for (BroadcastOutboxClaimService.ClaimedOutbox event : claimService.claim()) {
      try {
        Object payload = payload(event);
        EventMetadata metadata =
            new EventMetadata(
                "broadcast-outbox:" + event.id(),
                event.eventType(),
                "1",
                java.time.Instant.now(),
                event.companyId(),
                null,
                event.aggregateId().toString(),
                null,
                MessagePriority.MEDIUM);
        KafkaEvent<Object> kafkaEvent = KafkaEvent.of(metadata, payload);
        eventPublisher
            .publishAsync("broadcast-run:" + event.aggregateId(), kafkaEvent)
            .get(10, TimeUnit.SECONDS);
        claimService.published(event.id());
      } catch (Exception exception) {
        log.warn(
            "broadcast_outbox_publish_failed id={} aggregateId={} attempt={} reason={}",
            event.id(),
            event.aggregateId(),
            event.attempt(),
            exception.getClass().getSimpleName());
        claimService.failed(event, exception);
      }
    }
  }

  private Object payload(BroadcastOutboxClaimService.ClaimedOutbox event) throws Exception {
    if (BroadcastOutboxService.RUN_PREPARE_EVENT.equals(event.eventType())) {
      return objectMapper.readValue(event.payloadJson(), BroadcastRunPrepareRequestedEvent.class);
    }
    throw new IllegalArgumentException("Unsupported broadcast outbox event " + event.eventType());
  }
}
