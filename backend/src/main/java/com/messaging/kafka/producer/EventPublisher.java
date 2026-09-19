package com.messaging.kafka.producer;

import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.key.KafkaKeyResolver;
import com.messaging.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {

  private final KafkaTemplate<String, KafkaEvent<?>> kafkaTemplate;
  private final KafkaTopics kafkaTopics;
  private final KafkaKeyResolver keyResolver;

  public void publish(KafkaEvent<?> event) {
    String topic = kafkaTopics.topic(event.priority());
    String key = keyResolver.resolve(event);
    log.info(
        "KAFKA_DIAG publish eventId={} eventType={} companyId={} userId={} correlationId={} priority={} topic={} key={}",
        event.eventId(),
        event.eventType(),
        event.companyId(),
        event.userId(),
        event.correlationId(),
        event.priority(),
        topic,
        key);
    kafkaTemplate
        .send(topic, key, event)
        .whenComplete(
            (result, exception) -> {
              if (exception != null) {
                log.error(
                    "KAFKA_DIAG publish_failed eventId={} eventType={} topic={} reason={}",
                    event.eventId(),
                    event.eventType(),
                    topic,
                    exception.getClass().getSimpleName(),
                    exception);
              } else {
                log.debug(
                    "KAFKA_DIAG publish_ack eventId={} eventType={} topic={} partition={} offset={}",
                    event.eventId(),
                    event.eventType(),
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
              }
            });
  }
}
