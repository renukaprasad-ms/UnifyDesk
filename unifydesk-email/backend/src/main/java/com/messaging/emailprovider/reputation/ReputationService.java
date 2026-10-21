package com.messaging.emailprovider.reputation;

import com.messaging.emailprovider.common.id.IdGenerator;
import com.messaging.emailprovider.feedback.EmailEventType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReputationService {

  private final ReputationDailyRepository repository;

  public ReputationService(ReputationDailyRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void recordAccepted(Long companyId, Long domainId, int count, Instant occurredAt) {
    if (count <= 0) {
      return;
    }
    increment(companyId, domainId, occurredAt, count, 0, 0, 0, 0);
  }

  @Transactional
  public void recordEvent(
      Long companyId, Long domainId, EmailEventType eventType, Instant occurredAt) {
    long delivered = eventType == EmailEventType.DELIVERED ? 1 : 0;
    long bounced = eventType == EmailEventType.BOUNCED ? 1 : 0;
    long deferred = eventType == EmailEventType.DEFERRED ? 1 : 0;
    long complaints = eventType == EmailEventType.COMPLAINED ? 1 : 0;
    increment(companyId, domainId, occurredAt, 0, delivered, bounced, deferred, complaints);
  }

  private void increment(
      Long companyId,
      Long domainId,
      Instant occurredAt,
      long accepted,
      long delivered,
      long bounced,
      long deferred,
      long complaints) {
    Instant now = Instant.now();
    LocalDate date = occurredAt.atZone(ZoneOffset.UTC).toLocalDate();
    repository.increment(
        IdGenerator.nextId(),
        companyId,
        domainId,
        date,
        accepted,
        delivered,
        bounced,
        deferred,
        complaints,
        now);
  }
}
