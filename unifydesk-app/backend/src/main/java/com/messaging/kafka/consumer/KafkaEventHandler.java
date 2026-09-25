package com.messaging.kafka.consumer;

import com.messaging.kafka.event.KafkaEvent;

public interface KafkaEventHandler {

  boolean supports(String eventType);

  void handle(KafkaEvent<?> event);
}
