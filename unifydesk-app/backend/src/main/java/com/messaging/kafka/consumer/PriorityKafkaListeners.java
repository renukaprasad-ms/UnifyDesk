package com.messaging.kafka.consumer;

import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.idempotency.KafkaEventIdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PriorityKafkaListeners {

  private final KafkaEventDispatcher dispatcher;
  private final KafkaEventIdempotencyService idempotencyService;

  public PriorityKafkaListeners(
      KafkaEventDispatcher dispatcher, KafkaEventIdempotencyService idempotencyService) {
    this.dispatcher = dispatcher;
    this.idempotencyService = idempotencyService;
  }

  @KafkaListener(
      id = "criticalKafkaListener",
      topics = "${app.kafka.topics.critical.name}",
      groupId = "${app.kafka.topics.critical.consumer-group}",
      containerFactory = "criticalKafkaListenerContainerFactory")
  public void critical(
      ConsumerRecord<String, KafkaEvent<?>> record, Acknowledgment acknowledgment) {
    process(record, acknowledgment);
  }

  @KafkaListener(
      id = "highKafkaListener",
      topics = "${app.kafka.topics.high.name}",
      groupId = "${app.kafka.topics.high.consumer-group}",
      containerFactory = "highKafkaListenerContainerFactory")
  public void high(ConsumerRecord<String, KafkaEvent<?>> record, Acknowledgment acknowledgment) {
    process(record, acknowledgment);
  }

  @KafkaListener(
      id = "mediumKafkaListener",
      topics = "${app.kafka.topics.medium.name}",
      groupId = "${app.kafka.topics.medium.consumer-group}",
      containerFactory = "mediumKafkaListenerContainerFactory")
  public void medium(ConsumerRecord<String, KafkaEvent<?>> record, Acknowledgment acknowledgment) {
    process(record, acknowledgment);
  }

  @KafkaListener(
      id = "lowKafkaListener",
      topics = "${app.kafka.topics.low.name}",
      groupId = "${app.kafka.topics.low.consumer-group}",
      containerFactory = "lowKafkaListenerContainerFactory")
  public void low(ConsumerRecord<String, KafkaEvent<?>> record, Acknowledgment acknowledgment) {
    process(record, acknowledgment);
  }

  private void process(
      ConsumerRecord<String, KafkaEvent<?>> record, Acknowledgment acknowledgment) {
    KafkaEvent<?> event = record.value();

    if (event == null) {
      acknowledgment.acknowledge();
      return;
    }

    try {
      if (idempotencyService.isProcessed(event)) {
        acknowledgment.acknowledge();
        return;
      }

      dispatcher.dispatch(event);
      idempotencyService.markProcessed(event);
      acknowledgment.acknowledge();
    } catch (RuntimeException exception) {
      log.warn(
          "kafka_event_processing_failed topic={} partition={} offset={} key={} eventId={} eventType={} priority={} errorType={} errorMessage={}",
          record.topic(),
          record.partition(),
          record.offset(),
          record.key(),
          event.eventId(),
          event.eventType(),
          event.priority(),
          exception.getClass().getName(),
          exception.getMessage(),
          exception);
      throw exception;
    }
  }
}
