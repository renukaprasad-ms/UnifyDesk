package com.messaging.kafka.consumer;

import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.exception.NonRetryableKafkaException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventDispatcher {

  private final List<KafkaEventHandler> handlers;

  public void dispatch(KafkaEvent<?> event) {
    log.info(
        "KAFKA_DIAG dispatch eventId={} eventType={} companyId={} correlationId={}",
        event.eventId(),
        event.eventType(),
        event.companyId(),
        event.correlationId());
    handlers.stream()
        .filter(handler -> handler.supports(event.eventType()))
        .findFirst()
        .orElseThrow(
            () -> new NonRetryableKafkaException("Unsupported event type: " + event.eventType()))
        .handle(event);
  }
}
