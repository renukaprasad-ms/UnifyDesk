package com.messaging.emailprovider.queue;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailOutboxService {

  private final EmailOutboxRepository repository;

  public EmailOutboxService(EmailOutboxRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public EmailOutbox enqueue(String topic, String key, String payload) {
    EmailOutbox outbox = new EmailOutbox();
    outbox.setTopic(topic);
    outbox.setEventKey(key);
    outbox.setPayload(payload);
    return repository.save(outbox);
  }

  @Transactional
  public List<EmailOutbox> claim(int limit, int staleAfterSeconds) {
    Instant now = Instant.now();
    repository.recoverStale(now.minusSeconds(staleAfterSeconds), now);
    List<EmailOutbox> rows = repository.findClaimable(now, limit);
    for (EmailOutbox row : rows) {
      row.setStatus(OutboxStatus.PROCESSING);
      row.setClaimedAt(now);
      row.setNextAttemptAt(null);
      row.setAttempts(row.getAttempts() + 1);
    }
    return repository.saveAll(rows);
  }

  @Transactional
  public void markPublished(Long id) {
    EmailOutbox row = repository.findById(id).orElseThrow();
    row.setStatus(OutboxStatus.PUBLISHED);
    row.setPublishedAt(Instant.now());
    row.setClaimedAt(null);
    row.setNextAttemptAt(null);
    row.setLastError(null);
    repository.save(row);
  }

  @Transactional
  public void markFailure(Long id, String error, int maxAttempts) {
    EmailOutbox row = repository.findById(id).orElseThrow();
    row.setClaimedAt(null);
    row.setLastError(truncate(error));
    if (row.getAttempts() >= maxAttempts) {
      row.setStatus(OutboxStatus.FAILED);
      row.setNextAttemptAt(null);
    } else {
      row.setStatus(OutboxStatus.PENDING);
      long seconds = Math.min(300L, 1L << Math.min(row.getAttempts(), 8));
      row.setNextAttemptAt(Instant.now().plusSeconds(seconds));
    }
    repository.save(row);
  }

  private String truncate(String value) {
    if (value == null) {
      return "Unknown Kafka publish failure";
    }
    return value.length() <= 2048 ? value : value.substring(0, 2048);
  }
}
