package com.messaging.notification.platform.core.outbox;

import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.key.KafkaKeyResolver;
import com.messaging.kafka.priority.MessagePriority;
import com.messaging.kafka.producer.EventPublisher;
import com.messaging.notification.platform.core.config.NotificationPlatformProperties;
import com.messaging.notification.platform.core.event.NotificationMaterializeRequested;
import com.messaging.notification.platform.core.repository.NotificationOutboxRepository;
import com.messaging.notification.platform.core.repository.NotificationOutboxRepository.ClaimedEvent;
import com.messaging.notification.platform.core.repository.NotificationOutboxRepository.EventRow;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxPublisher {

  private final NotificationOutboxRepository repository;
  private final EventPublisher eventPublisher;
  private final KafkaKeyResolver keyResolver;
  private final NotificationPlatformProperties properties;

  @Scheduled(
      fixedDelayString = "${app.notification-platform.core.outbox.poll-interval-ms:750}",
      initialDelayString = "${app.notification-platform.core.outbox.poll-interval-ms:750}")
  public void publishDue() {
    repository.recoverStalePublished();
    for (ClaimedEvent event : repository.claimDue()) {
      publish(event);
    }
  }

  private void publish(ClaimedEvent claimed) {
    EventRow row = claimed.row();
    KafkaEvent<NotificationMaterializeRequested> kafkaEvent =
        new KafkaEvent<>(
            Long.toString(row.id()),
            "notification.materialize.requested",
            "1",
            row.occurredAt(),
            row.companyId(),
            row.recipientUserId(),
            row.correlationId(),
            row.causationId(),
            MessagePriority.valueOf(row.priority()),
            new NotificationMaterializeRequested(
                Long.toString(row.id()), row.eventKey(), row.eventType(), row.schemaVersion()));
    try {
      eventPublisher
          .publishAsync(keyResolver.resolve(kafkaEvent), kafkaEvent)
          .get(
              properties.getCore().getOutbox().getPublishTimeout().toMillis(),
              TimeUnit.MILLISECONDS);
      repository.markPublished(claimed);
    } catch (Exception exception) {
      int attempt = row.publishAttempts() + 1;
      boolean dead = attempt >= properties.getCore().getOutbox().getMaxAttempts();
      Instant next = Instant.now().plus(backoff(attempt));
      repository.markFailed(claimed, dead, next);
      log.warn("Notification event publish failed eventId={} attempt={}", row.id(), attempt);
    }
  }

  private Duration backoff(int attempt) {
    long initial = properties.getCore().getOutbox().getRetryInitialBackoff().toMillis();
    long max = properties.getCore().getOutbox().getRetryMaxBackoff().toMillis();
    int shift = Math.max(0, Math.min(20, attempt - 1));
    long value;
    try {
      value = Math.multiplyExact(initial, 1L << shift);
    } catch (ArithmeticException exception) {
      value = max;
    }
    return Duration.ofMillis(Math.min(max, value));
  }
}
