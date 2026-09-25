package com.messaging.emailprovider.validation.worker;

import com.messaging.emailprovider.validation.service.ValidationBatchWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "app",
    name = {"kafka.enabled", "validation.enabled"},
    havingValue = "true")
public class ValidationBatchListener {

  private final ValidationBatchWorker worker;

  public ValidationBatchListener(ValidationBatchWorker worker) {
    this.worker = worker;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.validation-batch:email.validation.batch}",
      groupId = "${app.validation.kafka-group:email-validator}")
  public void onBatch(String batchId) {
    try {
      worker.process(Long.parseLong(batchId));
    } catch (NumberFormatException exception) {
      log.error("invalid_validation_batch_event payload={}", batchId);
    }
  }
}
