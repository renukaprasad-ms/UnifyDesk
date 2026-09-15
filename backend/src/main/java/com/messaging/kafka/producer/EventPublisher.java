package com.messaging.kafka.producer;

import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.key.KafkaKeyResolver;
import com.messaging.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventPublisher {

  private static final Logger LOGGER = LoggerFactory.getLogger(EventPublisher.class);

  private final KafkaTemplate<String, KafkaEvent<?>> kafkaTemplate;
  private final KafkaTopics kafkaTopics;
  private final KafkaKeyResolver keyResolver;

  public void publish(KafkaEvent<?> event) {
    String topic = kafkaTopics.topic(event.priority());
    String key = keyResolver.resolve(event);
    kafkaTemplate
        .send(topic, key, event)
        .whenComplete(
            (result, exception) -> {
              if (exception != null) {
                return;
              }
              if (isEmailEvent(event)) {
                LOGGER.info(
                    "Email event enqueued eventId={} eventType={} topic={} partition={} offset={} correlationId={}",
                    event.eventId(),
                    event.eventType(),
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    event.correlationId());
              }
            });
  }

  private boolean isEmailEvent(KafkaEvent<?> event) {
    return "auth.otp.requested".equals(event.eventType());
  }
}
