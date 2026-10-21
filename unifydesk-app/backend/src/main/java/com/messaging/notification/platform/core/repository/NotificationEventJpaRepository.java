package com.messaging.notification.platform.core.repository;

import com.messaging.notification.platform.core.entity.NotificationEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationEventJpaRepository extends JpaRepository<NotificationEvent, Long> {
  Optional<NotificationEvent> findByEventKey(String eventKey);

  // Atomic deduplication must not abort the caller's company/message transaction.
  @Modifying
  @Query(
      value =
          """
      insert into notification_events (id,event_key,event_type,schema_version,company_id,actor_user_id,recipient_mode,recipient_user_id,required_permission_key,subject_type,subject_id,priority,group_key,variables_json,correlation_id,causation_id,occurred_at,publish_state,publish_attempts,next_publish_at,created_at,updated_at)
      values (:#{#event.id},:#{#event.eventKey},:#{#event.eventType},:#{#event.schemaVersion},:#{#event.companyId},:#{#event.actorUserId},:#{#event.recipientMode},:#{#event.recipientUserId},:#{#event.requiredPermissionKey},:#{#event.subjectType},:#{#event.subjectId},:#{#event.priority},:#{#event.groupKey},cast(:#{#event.variablesJson} as jsonb),:#{#event.correlationId},:#{#event.causationId},:#{#event.occurredAt},:#{#event.publishState},:#{#event.publishAttempts},:#{#event.nextPublishAt},:#{#event.createdAt},:#{#event.updatedAt})
      on conflict (event_key) do nothing
      """,
      nativeQuery = true)
  int insertIfAbsent(@Param("event") NotificationEvent event);

  @Query(
      value =
          """
      select * from notification_events
      where (publish_state='PENDING' and next_publish_at <= :now)
         or (publish_state='CLAIMED' and claim_expires_at < :now)
      order by next_publish_at,id
      limit :batchSize for update skip locked
      """,
      nativeQuery = true)
  List<NotificationEvent> claimDue(@Param("now") Instant now, @Param("batchSize") int batchSize);

  @Modifying
  @Query(
      """
      update NotificationEvent e set e.publishState='PUBLISHED', e.publishedAt=:now,
          e.claimToken=null, e.claimExpiresAt=null, e.lastErrorCode=null, e.updatedAt=:now
      where e.id=:id and e.claimToken=:token
      """)
  int markPublished(@Param("id") long id, @Param("token") String token, @Param("now") Instant now);

  @Modifying
  @Query(
      """
      update NotificationEvent e set e.publishState=:state, e.nextPublishAt=:next,
          e.claimToken=null, e.claimExpiresAt=null, e.lastErrorCode='KAFKA_PUBLISH_FAILED', e.updatedAt=:now
      where e.id=:id and e.claimToken=:token
      """)
  int markFailed(
      @Param("id") long id,
      @Param("token") String token,
      @Param("state") String state,
      @Param("next") Instant next,
      @Param("now") Instant now);

  @Modifying
  @Query(
      value =
          """
      update notification_events set publish_state='PENDING',next_publish_at=:now,updated_at=:now
      where id in (
        select e.id from notification_events e
        where e.publish_state='PUBLISHED' and e.materialized_at is null and e.published_at < :cutoff
          and not exists (select 1 from notification_fanout_jobs j where j.event_id=e.id)
        order by e.published_at,e.id limit :batchSize
      )
      """,
      nativeQuery = true)
  int recoverStalePublished(
      @Param("cutoff") Instant cutoff,
      @Param("now") Instant now,
      @Param("batchSize") int batchSize);
}
