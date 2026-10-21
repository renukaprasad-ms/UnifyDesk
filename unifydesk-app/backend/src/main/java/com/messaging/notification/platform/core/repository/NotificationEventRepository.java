package com.messaging.notification.platform.core.repository;

import com.messaging.common.id.IdGenerator;
import com.messaging.notification.platform.core.entity.NotificationEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class NotificationEventRepository {
  private final NotificationEventJpaRepository repository;

  @Transactional(propagation = Propagation.MANDATORY)
  public long insertPending(
      String eventKey,
      String eventType,
      Long companyId,
      Long actorUserId,
      String recipientMode,
      Long recipientUserId,
      String permission,
      String subjectType,
      String subjectId,
      String groupKey,
      String json,
      Instant occurredAt) {
    Instant now = Instant.now();
    NotificationEvent event = new NotificationEvent();
    event.setId(IdGenerator.nextId());
    event.setEventKey(eventKey);
    event.setEventType(eventType);
    event.setSchemaVersion(1);
    event.setCompanyId(companyId);
    event.setActorUserId(actorUserId);
    event.setRecipientMode(recipientMode);
    event.setRecipientUserId(recipientUserId);
    event.setRequiredPermissionKey(permission);
    event.setSubjectType(subjectType);
    event.setSubjectId(subjectId);
    event.setPriority("MEDIUM");
    event.setGroupKey(groupKey);
    event.setVariablesJson(json);
    event.setCorrelationId(UUID.randomUUID().toString());
    event.setOccurredAt(occurredAt);
    event.setPublishState("PENDING");
    event.setPublishAttempts(0);
    event.setNextPublishAt(now);
    event.setCreatedAt(now);
    event.setUpdatedAt(now);
    if (repository.insertIfAbsent(event) == 1) return event.getId();
    return repository
        .findByEventKey(eventKey)
        .orElseThrow(
            () -> new IllegalStateException("Notification event conflict without existing row"))
        .getId();
  }
}
