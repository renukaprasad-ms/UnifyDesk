package com.messaging.emailprovider.queue;

import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class EmailOutboxPublisher {

  private final EmailOutboxService service;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final int batchSize;
  private final int staleAfterSeconds;
  private final int maxAttempts;

  public EmailOutboxPublisher(
      EmailOutboxService service,
      KafkaTemplate<String, String> kafkaTemplate,
      @Value("${app.outbox.batch-size:50}") int batchSize,
      @Value("${app.outbox.stale-after-seconds:120}") int staleAfterSeconds,
      @Value("${app.outbox.max-attempts:20}") int maxAttempts) {
    this.service = service;
    this.kafkaTemplate = kafkaTemplate;
    this.batchSize = batchSize;
    this.staleAfterSeconds = staleAfterSeconds;
    this.maxAttempts = maxAttempts;
  }

  @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:250}")
  public void publish() {
    for (EmailOutbox row : service.claim(batchSize, staleAfterSeconds)) {
      try {
        kafkaTemplate
            .send(row.getTopic(), row.getEventKey(), row.getPayload())
            .get(10, TimeUnit.SECONDS);
        service.markPublished(row.getId());
      } catch (Exception exception) {
        log.warn(
            "outbox_publish_failed outboxId={} topic={}", row.getId(), row.getTopic(), exception);
        service.markFailure(row.getId(), exception.getMessage(), maxAttempts);
      }
    }
  }
}
