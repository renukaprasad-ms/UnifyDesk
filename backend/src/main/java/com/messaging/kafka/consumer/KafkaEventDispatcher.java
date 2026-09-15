package com.messaging.kafka.consumer;

import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.exception.NonRetryableKafkaException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaEventDispatcher {

  private final List<KafkaEventHandler> handlers;

  public void dispatch(KafkaEvent<?> event) {
    handlers.stream()
        .filter(handler -> handler.supports(event.eventType()))
        .findFirst()
        .orElseThrow(
            () -> new NonRetryableKafkaException("Unsupported event type: " + event.eventType()))
        .handle(event);
  }
}
