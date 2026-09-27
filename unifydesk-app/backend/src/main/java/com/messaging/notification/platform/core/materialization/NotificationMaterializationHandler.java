package com.messaging.notification.platform.core.materialization;

import com.messaging.kafka.consumer.KafkaEventHandler;
import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.exception.NonRetryableKafkaException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationMaterializationHandler implements KafkaEventHandler {

  private final JdbcTemplate jdbcTemplate;
  private final NotificationMaterializer materializer;

  @Override
  public boolean supports(String eventType) {
    return "notification.materialize.requested".equals(eventType);
  }

  @Override
  public void handle(KafkaEvent<?> event) {
    long eventId;
    try {
      eventId = Long.parseLong(event.eventId());
    } catch (RuntimeException exception) {
      throw new NonRetryableKafkaException("Invalid notification event id", exception);
    }

    EventTarget target =
        jdbcTemplate
            .query(
                """
                select recipient_mode,recipient_user_id,company_id
                from notification_events where id=?
                """,
                rs ->
                    rs.next()
                        ? new EventTarget(
                            rs.getString("recipient_mode"),
                            (Long) rs.getObject("recipient_user_id"),
                            (Long) rs.getObject("company_id"))
                        : null,
                eventId);
    if (target == null) {
      throw new NonRetryableKafkaException("Notification event not found");
    }

    if ("USER".equals(target.mode())) {
      if (target.userId() == null) {
        throw new NonRetryableKafkaException("Notification direct recipient missing");
      }
      materializer.materialize(eventId, target.userId());
      jdbcTemplate.update(
          "update notification_events set materialized_at=coalesce(materialized_at,now()),updated_at=now() where id=?",
          eventId);
      return;
    }

    if ("COMPANY_PERMISSION".equals(target.mode()) && target.companyId() != null) {
      jdbcTemplate.update(
          """
          insert into notification_fanout_jobs(
            id,event_id,company_id,state,cursor_user_id,batch_size,processed_count,
            suppressed_count,attempts,next_attempt_at,created_at,updated_at)
          values (?,?,?,?,null,500,0,0,0,now(),now(),now())
          on conflict (event_id) do nothing
          """,
          com.messaging.common.id.IdGenerator.nextId(),
          eventId,
          target.companyId(),
          "PENDING");
      return;
    }

    throw new NonRetryableKafkaException("Unsupported notification recipient mode");
  }

  private record EventTarget(String mode, Long userId, Long companyId) {}
}
