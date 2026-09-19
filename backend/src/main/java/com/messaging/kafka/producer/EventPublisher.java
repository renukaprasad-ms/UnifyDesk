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
    publish(keyResolver.resolve(event), event);
  }

  public void publish(String key, KafkaEvent<?> event) {
    String topic = kafkaTopics.topic(event.priority());
    kafkaTemplate.send(topic, key, event);
  }
}
