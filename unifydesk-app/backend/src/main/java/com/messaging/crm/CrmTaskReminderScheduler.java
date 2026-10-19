package com.messaging.crm;

import com.messaging.notification.platform.core.event.NotificationEventRecorder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bounded, cluster-safe CRM reminder scan. It only records durable in-app notification events. The
 * existing notification outbox handles materialization; no synchronous email/provider IO.
 *
 * <p>Disabled by default until Flyway V110 and notification delivery are verified.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.crm.task-reminders", name = "enabled", havingValue = "true")
public class CrmTaskReminderScheduler {
  private static final int MAX_BATCH = 50;

  private final JdbcTemplate jdbc;
  private final NotificationEventRecorder events;

  private record Due(long id, long companyId, long assigneeId, long generation, String title) {}

  private static Due mapDue(ResultSet rs, int row) throws SQLException {
    return new Due(
        rs.getLong("id"),
        rs.getLong("company_id"),
        rs.getLong("assignee_user_id"),
        rs.getLong("reminder_generation"),
        rs.getString("title"));
  }

  @Scheduled(
      fixedDelayString = "${app.crm.task-reminders.poll-interval-ms:60000}",
      initialDelayString = "${app.crm.task-reminders.initial-delay-ms:30000}")
  @Transactional
  public void scan() {
    List<Due> due =
        jdbc.query(
            """
        select t.id, t.company_id, t.assignee_user_id, t.reminder_generation, t.title
        from crm_tasks t
        join company_members m on m.company_id = t.company_id
          and m.user_id = t.assignee_user_id and m.status = 'ACTIVE'
        join users u on u.id = t.assignee_user_id and u.status = 'ACTIVE'
        join companies c on c.id = t.company_id and c.status = 'ACTIVE'
        where t.status in ('OPEN','IN_PROGRESS')
          and t.due_at is not null
          and t.due_at >= now() - interval '1 day'
          and t.due_at <= now() + interval '10 minutes'
          and t.reminded_generation is distinct from t.reminder_generation
        order by t.due_at, t.id
        limit ?
        for update of t skip locked
        """,
            CrmTaskReminderScheduler::mapDue,
            MAX_BATCH);

    for (Due task : due) {
      events.recordCrmTaskDue(
          task.id(), task.companyId(), task.assigneeId(), task.generation(), task.title());
      // The event insert and this update commit atomically; retries are idempotent.
      jdbc.update(
          """
          update crm_tasks set reminded_generation = reminder_generation
          where company_id = ? and id = ? and reminder_generation = ?
          """,
          task.companyId(),
          task.id(),
          task.generation());
    }
    if (!due.isEmpty()) {
      log.debug("CRM reminder events recorded: {}", due.size());
    }
  }
}
