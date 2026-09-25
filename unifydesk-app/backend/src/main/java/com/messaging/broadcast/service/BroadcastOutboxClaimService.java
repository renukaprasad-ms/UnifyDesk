package com.messaging.broadcast.service;

import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.entity.BroadcastOutbox;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastOutboxClaimService {

  private final BroadcastOutboxRepository repository;
  private final BroadcastRunRepository runRepository;
  private final BroadcastMessageRepository messageRepository;
  private final BroadcastProperties properties;

  @Transactional
  public List<ClaimedOutbox> claim() {
    Instant now = Instant.now();
    repository.recoverStale(now.minus(properties.getOutboxStaleAfter()), now);
    List<BroadcastOutbox> events =
        repository.findClaimableForUpdate(now, Math.max(1, properties.getOutboxBatchSize()));
    for (BroadcastOutbox event : events) {
      event.setStatus(BroadcastOutboxStatus.PROCESSING);
      event.setAttemptCount(event.getAttemptCount() + 1);
      event.setClaimedAt(now);
      event.setLastError(null);
    }
    repository.saveAll(events);
    return events.stream()
        .map(
            event ->
                new ClaimedOutbox(
                    event.getId(),
                    event.getCompanyId(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getEventType(),
                    event.getPayloadJson(),
                    event.getAttemptCount()))
        .toList();
  }

  @Transactional
  public void published(Long id) {
    repository
        .findById(id)
        .ifPresent(
            event -> {
              event.setStatus(BroadcastOutboxStatus.PUBLISHED);
              event.setPublishedAt(Instant.now());
              event.setClaimedAt(null);
              event.setLastError(null);
              repository.save(event);
            });
  }

  @Transactional
  public void failed(ClaimedOutbox claimed, Throwable error) {
    BroadcastOutbox event = repository.findById(claimed.id()).orElse(null);
    if (event == null || event.getStatus() == BroadcastOutboxStatus.PUBLISHED) return;

    String message = safeMessage(error);
    if (event.getAttemptCount() >= Math.max(1, properties.getOutboxMaxAttempts())) {
      event.setStatus(BroadcastOutboxStatus.FAILED);
      event.setClaimedAt(null);
      event.setLastError(message);
      failAggregate(event, message);
    } else {
      event.setStatus(BroadcastOutboxStatus.RETRY);
      event.setClaimedAt(null);
      event.setLastError(message);
      event.setAvailableAt(Instant.now().plus(retryDelay(event.getAttemptCount())));
    }
    repository.save(event);
  }

  private void failAggregate(BroadcastOutbox event, String error) {
    if (!"BROADCAST_RUN".equals(event.getAggregateType())) return;
    runRepository
        .findById(event.getAggregateId())
        .ifPresent(
            run -> {
              if (run.getStatus() == BroadcastRunStatus.PREPARING) {
                run.setStatus(BroadcastRunStatus.FAILED);
                run.setCompletedAt(Instant.now());
                runRepository.save(run);
                messageRepository
                    .findById(run.getBroadcastMessageId())
                    .ifPresent(
                        broadcast -> {
                          broadcast.setStatus(BroadcastMessageStatus.FAILED);
                          messageRepository.save(broadcast);
                        });
              }
            });
  }

  private Duration retryDelay(int attempt) {
    long base = Math.max(1000L, properties.getRetryInitialBackoff().toMillis());
    long max = Math.max(base, properties.getRetryMaxBackoff().toMillis());
    int exponent = Math.min(Math.max(0, attempt - 1), 16);
    long delay = Math.min(max, base * (1L << exponent));
    return Duration.ofMillis(delay);
  }

  private String safeMessage(Throwable error) {
    if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
      return error == null ? "Unknown outbox error" : error.getClass().getSimpleName();
    }
    String value = error.getMessage();
    return value.length() <= 1000 ? value : value.substring(0, 1000);
  }

  public record ClaimedOutbox(
      Long id,
      Long companyId,
      String aggregateType,
      Long aggregateId,
      String eventType,
      String payloadJson,
      int attempt) {}
}
