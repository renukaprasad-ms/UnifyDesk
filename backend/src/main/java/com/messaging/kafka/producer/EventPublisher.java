package com.messaging.kafka.producer;

import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.key.KafkaKeyResolver;
import com.messaging.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventPublisher {

  private final KafkaTemplate<String, KafkaEvent<?>> kafkaTemplate;
  private final KafkaTopics kafkaTopics;
  private final KafkaKeyResolver keyResolver;

  public void publish(KafkaEvent<?> event) {
    String topic = kafkaTopics.topic(event.priority());
    String key = keyResolver.resolve(event);
    kafkaTemplate.send(topic, key, event);
  }
}
