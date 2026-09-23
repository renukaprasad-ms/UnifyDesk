package com.messaging.kafka.producer;

import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.key.KafkaKeyResolver;
import com.messaging.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
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
    publishAsync(key, event);
  }

  public CompletableFuture<SendResult<String, KafkaEvent<?>>> publishAsync(
      String key, KafkaEvent<?> event) {
    String topic = kafkaTopics.topic(event.priority());
    return kafkaTemplate.send(topic, key, event);
  }
}
