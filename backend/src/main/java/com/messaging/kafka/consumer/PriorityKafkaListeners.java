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
      log.warn(
          "KAFKA_DIAG consume_null topic={} partition={} offset={}",
          record.topic(),
          record.partition(),
          record.offset());
      acknowledgment.acknowledge();
      return;
    }

    log.info(
        "KAFKA_DIAG consume eventId={} eventType={} topic={} partition={} offset={} key={} companyId={} correlationId={}",
        event.eventId(),
        event.eventType(),
        record.topic(),
        record.partition(),
        record.offset(),
        record.key(),
        event.companyId(),
        event.correlationId());

    if (idempotencyService.isProcessed(event)) {
      log.info(
          "KAFKA_DIAG duplicate_skip eventId={} eventType={} topic={} partition={} offset={}",
          event.eventId(),
          event.eventType(),
          record.topic(),
          record.partition(),
          record.offset());
      acknowledgment.acknowledge();
      return;
    }

    dispatcher.dispatch(event);
    idempotencyService.markProcessed(event);
    acknowledgment.acknowledge();
    log.info(
        "KAFKA_DIAG consume_ack eventId={} eventType={} topic={} partition={} offset={}",
        event.eventId(),
        event.eventType(),
        record.topic(),
        record.partition(),
        record.offset());
  }
}
