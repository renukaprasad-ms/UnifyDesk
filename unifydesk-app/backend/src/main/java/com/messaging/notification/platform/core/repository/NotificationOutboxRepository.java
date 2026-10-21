package com.messaging.notification.platform.core.repository;

import com.messaging.notification.platform.core.config.NotificationPlatformProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class NotificationOutboxRepository {
  private final NotificationEventJpaRepository repository;
  private final NotificationPlatformProperties properties;

  @Transactional
  public List<ClaimedEvent> claimDue() {
    Instant now = Instant.now();
    return repository.claimDue(now, properties.getCore().getOutbox().getBatchSize()).stream()
        .map(
            event -> {
              EventRow row =
                  new EventRow(
                      event.getId(),
                      event.getEventKey(),
                      event.getEventType(),
                      event.getSchemaVersion(),
                      event.getCompanyId(),
                      event.getRecipientUserId(),
                      event.getCorrelationId(),
                      event.getCausationId(),
                      event.getPriority(),
                      event.getOccurredAt(),
                      event.getPublishAttempts());
              String token = UUID.randomUUID().toString();
              event.setPublishState("CLAIMED");
              event.setPublishAttempts(event.getPublishAttempts() + 1);
              event.setClaimToken(token);
              event.setClaimExpiresAt(now.plus(properties.getCore().getOutbox().getLease()));
              event.setUpdatedAt(now);
              return new ClaimedEvent(row, token);
            })
        .toList();
  }

  @Transactional
  public void markPublished(ClaimedEvent claimed) {
    repository.markPublished(claimed.row().id(), claimed.token(), Instant.now());
  }

  @Transactional
  public void markFailed(ClaimedEvent claimed, boolean dead, Instant next) {
    repository.markFailed(
        claimed.row().id(), claimed.token(), dead ? "DEAD" : "PENDING", next, Instant.now());
  }

  @Transactional
  public void recoverStalePublished() {
    Instant now = Instant.now();
    repository.recoverStalePublished(
        now.minus(properties.getCore().getOutbox().getRecoveryAfter()),
        now,
        properties.getCore().getOutbox().getBatchSize());
  }

  public record ClaimedEvent(EventRow row, String token) {}

  public record EventRow(
      long id,
      String eventKey,
      String eventType,
      int schemaVersion,
      Long companyId,
      Long recipientUserId,
      String correlationId,
      String causationId,
      String priority,
      Instant occurredAt,
      int publishAttempts) {}
}
