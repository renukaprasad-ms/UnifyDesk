package com.messaging.notification.platform.core.materialization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.id.IdGenerator;
import com.messaging.company.service.CompanyIamPermissionService;
import com.messaging.notification.platform.core.realtime.NotificationRealtimePublisher;
import com.messaging.notification.platform.core.registry.NotificationTypeRegistry;
import com.messaging.notification.platform.core.registry.NotificationTypeRegistry.EmailMode;
import com.messaging.notification.platform.core.state.NotificationUnreadCounterService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationMaterializer {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final NotificationTypeRegistry registry;
  private final CompanyIamPermissionService permissionService;
  private final NotificationUnreadCounterService counterService;
  private final NotificationRealtimePublisher realtimePublisher;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public MaterializationResult materialize(long eventId, long userId) {
    EventRow event = loadEvent(eventId);
    if (event == null) {
      return MaterializationResult.MISSING_EVENT;
    }

    int receiptInserted =
        jdbcTemplate.update(
            """
            insert into notification_event_recipients(
              id,notification_event_id,company_id,user_id,notification_id,status,
              suppression_reason,created_at,updated_at)
            values (?,?,?,?,null,'SUPPRESSED_POLICY',null,now(),now())
            on conflict (notification_event_id,user_id) do nothing
            """,
            IdGenerator.nextId(),
            event.id(),
            event.companyId(),
            userId);
    if (receiptInserted == 0) {
      return MaterializationResult.DUPLICATE;
    }

    if (!eligible(event, userId)) {
      jdbcTemplate.update(
          """
          update notification_event_recipients
          set status='SUPPRESSED_INELIGIBLE',suppression_reason='RECIPIENT_NOT_ELIGIBLE',updated_at=now()
          where notification_event_id=? and user_id=?
          """,
          event.id(),
          userId);
      return MaterializationResult.SUPPRESSED;
    }

    NotificationTypeRegistry.Definition definition =
        registry.requireSource(event.eventType(), event.schemaVersion());
    Map<String, String> variables = variables(event.variablesJson());
    registry.validateVariables(definition, variables);
    NotificationTypeRegistry.Rendered rendered = registry.render(definition, variables);

    Materialized visible =
        definition.coalesced()
            ? coalesce(event, userId, definition, rendered)
            : create(event, userId, definition, rendered);

    createDeliveryIntents(event, userId, definition, visible.notificationId());

    jdbcTemplate.update(
        """
        update notification_event_recipients
        set notification_id=?,status='MATERIALIZED',suppression_reason=null,updated_at=now()
        where notification_event_id=? and user_id=?
        """,
        visible.notificationId(),
        event.id(),
        userId);

    realtimePublisher.afterCommit(
        userId,
        event.companyId(),
        visible.notificationId(),
        visible.coalesced() ? "COALESCED" : "CREATED");
    return visible.coalesced()
        ? MaterializationResult.COALESCED
        : MaterializationResult.CREATED;
  }

  private Materialized create(
      EventRow event,
      long userId,
      NotificationTypeRegistry.Definition definition,
      NotificationTypeRegistry.Rendered rendered) {
    long notificationId = IdGenerator.nextId();
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        insert into user_notifications(
          id,company_id,user_id,notification_type,category,severity,required_permission_key,
          title,body,aggregate_count,group_key,bucket_start,action_kind,action_path,
          first_event_at,last_event_at,seen_at,read_at,expires_at,created_at,updated_at)
        values (?,?,?,?,?,?,?,?,?,1,?,null,'ROUTE',?,?,?,null,null,?,?,?)
        """,
        notificationId,
        event.companyId(),
        userId,
        definition.notificationType(),
        definition.category(),
        definition.severity(),
        event.requiredPermissionKey(),
        rendered.title(),
        rendered.body(),
        event.groupKey(),
        rendered.actionPath(),
        utc(event.occurredAt()),
        utc(event.occurredAt()),
        utc(now.plus(180, ChronoUnit.DAYS)),
        utc(now),
        utc(now));
    counterService.increment(userId, event.companyId(), event.requiredPermissionKey());
    return new Materialized(notificationId, false);
  }

  private Materialized coalesce(
      EventRow event,
      long userId,
      NotificationTypeRegistry.Definition definition,
      NotificationTypeRegistry.Rendered rendered) {
    Instant bucket = fiveMinuteBucket(event.occurredAt());
    long lockKey =
        Integer.toUnsignedLong(
            Objects.hash(
                event.companyId(),
                userId,
                definition.notificationType(),
                event.groupKey(),
                bucket));
    jdbcTemplate.queryForObject("select pg_advisory_xact_lock(?)", Object.class, lockKey);

    ExistingNotification existing =
        jdbcTemplate.query(
            """
            select id,aggregate_count,read_at
            from user_notifications
            where company_id=? and user_id=? and notification_type=? and group_key=? and bucket_start=?
            for update
            """,
            rs ->
                rs.next()
                    ? new ExistingNotification(
                        rs.getLong("id"),
                        rs.getInt("aggregate_count"),
                        rs.getTimestamp("read_at") == null
                            ? null
                            : rs.getTimestamp("read_at").toInstant())
                    : null,
            event.companyId(),
            userId,
            definition.notificationType(),
            event.groupKey(),
            utc(bucket));

    if (existing == null) {
      long notificationId = IdGenerator.nextId();
      Instant now = Instant.now();
      jdbcTemplate.update(
          """
          insert into user_notifications(
            id,company_id,user_id,notification_type,category,severity,required_permission_key,
            title,body,aggregate_count,group_key,bucket_start,action_kind,action_path,
            first_event_at,last_event_at,seen_at,read_at,expires_at,created_at,updated_at)
          values (?,?,?,?,?,?,?,?,?,1,?,?,'ROUTE',?,?,?,null,null,?,?,?)
          """,
          notificationId,
          event.companyId(),
          userId,
          definition.notificationType(),
          definition.category(),
          definition.severity(),
          event.requiredPermissionKey(),
          rendered.title(),
          rendered.body(),
          event.groupKey(),
          utc(bucket),
          rendered.actionPath(),
          utc(event.occurredAt()),
          utc(event.occurredAt()),
          utc(now.plus(180, ChronoUnit.DAYS)),
          utc(now),
          utc(now));
      counterService.increment(userId, event.companyId(), event.requiredPermissionKey());
      return new Materialized(notificationId, false);
    }

    int nextCount = Math.addExact(existing.aggregateCount(), 1);
    jdbcTemplate.update(
        """
        update user_notifications
        set aggregate_count=?,title=?,body=?,last_event_at=greatest(last_event_at,?),
            read_at=null,updated_at=now()
        where id=?
        """,
        nextCount,
        coalescedTitle(definition, rendered, nextCount),
        rendered.body(),
        utc(event.occurredAt()),
        existing.id());
    if (existing.readAt() != null) {
      counterService.increment(userId, event.companyId(), event.requiredPermissionKey());
    }
    return new Materialized(existing.id(), true);
  }

  private String coalescedTitle(
      NotificationTypeRegistry.Definition definition,
      NotificationTypeRegistry.Rendered rendered,
      int aggregateCount) {
    if ("INBOX_ACTIVITY".equals(definition.notificationType())) {
      return aggregateCount + " new emails";
    }
    return rendered.title();
  }

  private void createDeliveryIntents(
      EventRow event,
      long userId,
      NotificationTypeRegistry.Definition definition,
      long notificationId) {
    if ("COMPANY_CREATED".equals(definition.notificationType())) {
      insertIntent(
          event,
          userId,
          notificationId,
          "EMAIL",
          "IMMEDIATE",
          "email:welcome:" + event.companyId() + ":" + userId);
      return;
    }

    EffectivePreference preference = preference(userId, event.companyId(), definition);

    if (isBroadcastTerminal(definition.notificationType())
        && preference.emailMode() != EmailMode.OFF) {
      insertIntent(
          event,
          userId,
          notificationId,
          "EMAIL",
          preference.emailMode().name(),
          "email:broadcast:" + notificationId + ":" + userId);
    }

    if ("INBOX_ACTIVITY".equals(definition.notificationType())
        && preference.emailMode() == EmailMode.DIGEST) {
      insertIntent(
          event,
          userId,
          notificationId,
          "EMAIL",
          "DIGEST",
          "digest:inbox:" + notificationId + ":" + userId);
    }

    if (("INBOX_ACTIVITY".equals(definition.notificationType())
            || "COMPANY_INVITATION".equals(definition.notificationType())
            || "COMPANY_INVITATION_ACCEPTED".equals(definition.notificationType())
            || isBroadcastTerminal(definition.notificationType()))
        && preference.browserEnabled()) {
      insertIntent(
          event,
          userId,
          notificationId,
          "WEB_PUSH",
          "IMMEDIATE",
          "push:" + notificationId + ":" + userId);
    }
  }

  private boolean isBroadcastTerminal(String notificationType) {
    return "BROADCAST_COMPLETED".equals(notificationType)
        || "BROADCAST_FAILED".equals(notificationType);
  }

  private void insertIntent(
      EventRow event,
      long userId,
      long notificationId,
      String channel,
      String mode,
      String idempotencyKey) {
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        insert into notification_delivery_intents(
          id,notification_event_id,notification_id,company_id,user_id,notification_type,
          required_permission_key,channel,delivery_mode,idempotency_key,state,not_before,
          next_attempt_at,attempt_count,created_at,updated_at)
        values (?,?,?,?,?,?,?,?,?,?,'PENDING',?,?,0,?,?)
        on conflict (idempotency_key) do nothing
        """,
        IdGenerator.nextId(),
        event.id(),
        notificationId,
        event.companyId(),
        userId,
        registry.requireSource(event.eventType(), event.schemaVersion()).notificationType(),
        event.requiredPermissionKey(),
        channel,
        mode,
        idempotencyKey,
        utc(now),
        utc(now),
        utc(now),
        utc(now));
  }

  private EffectivePreference preference(
      long userId, Long companyId, NotificationTypeRegistry.Definition definition) {
    PreferenceRow company =
        companyId == null ? null : findPreference(userId, companyId, definition.notificationType());
    PreferenceRow account = findPreference(userId, null, definition.notificationType());
    PreferenceRow selected = company != null ? company : account;
    EmailMode emailMode =
        selected == null || selected.emailMode() == null
            ? definition.defaultEmailMode()
            : EmailMode.valueOf(selected.emailMode());
    boolean browser =
        selected == null || selected.browserEnabled() == null
            ? definition.defaultBrowserEnabled()
            : selected.browserEnabled();
    return new EffectivePreference(emailMode, browser);
  }

  private PreferenceRow findPreference(long userId, Long companyId, String type) {
    String sql =
        companyId == null
            ? """
              select email_mode,browser_enabled
              from notification_preference_overrides
              where user_id=? and company_id is null and notification_type=?
              """
            : """
              select email_mode,browser_enabled
              from notification_preference_overrides
              where user_id=? and company_id=? and notification_type=?
              """;
    Object[] args =
        companyId == null
            ? new Object[] {userId, type}
            : new Object[] {userId, companyId, type};
    return jdbcTemplate.query(
        sql,
        rs ->
            rs.next()
                ? new PreferenceRow(
                    rs.getString("email_mode"),
                    (Boolean) rs.getObject("browser_enabled"))
                : null,
        args);
  }

  private boolean eligible(EventRow event, long userId) {
    Boolean activeUser =
        jdbcTemplate.queryForObject(
            "select exists(select 1 from users where id=? and status='ACTIVE')",
            Boolean.class,
            userId);
    if (!Boolean.TRUE.equals(activeUser)) {
      return false;
    }
    if (event.companyId() == null) {
      return true;
    }
    Boolean member =
        jdbcTemplate.queryForObject(
            """
            select exists(
              select 1
              from company_members m
              join companies c on c.id=m.company_id
              where m.company_id=? and m.user_id=? and m.status='ACTIVE'
                and c.status in ('ACTIVE','SUSPENDED')
            )
            """,
            Boolean.class,
            event.companyId(),
            userId);
    if (!Boolean.TRUE.equals(member)) {
      return false;
    }
    if ("crm.task.due".equals(event.eventType())) {
      // A reminder may wait in the outbox while a task is completed, moved or reassigned.
      // Suppress stale generations rather than delivering them to the old assignee.
      Map<String, String> payload = variables(event.variablesJson());
      long taskId;
      long generation;
      try {
        taskId = Long.parseLong(payload.get("taskId"));
        generation = Long.parseLong(payload.get("generation"));
      } catch (NumberFormatException | NullPointerException exception) {
        return false;
      }
      Boolean currentTask = jdbcTemplate.queryForObject(
          """
          select exists(
            select 1 from crm_tasks t
            join companies c on c.id = t.company_id and c.status = 'ACTIVE'
            where t.company_id = ? and t.id = ? and t.assignee_user_id = ?
              and t.reminder_generation = ?
              and t.status in ('OPEN', 'IN_PROGRESS')
              and t.due_at is not null
          )
          """,
          Boolean.class, event.companyId(), taskId, userId, generation);
      if (!Boolean.TRUE.equals(currentTask)
          || !permissionService.hasPermission(userId, event.companyId(), "contact.read")) {
        // CRM task pages also require contact.read; do not emit unusable notifications.
        return false;
      }
    }
    return event.requiredPermissionKey() == null
        || permissionService.hasPermission(userId, event.companyId(), event.requiredPermissionKey());
  }

  private EventRow loadEvent(long eventId) {
    return jdbcTemplate.query(
        """
        select id,event_type,schema_version,company_id,required_permission_key,group_key,
               variables_json::text,occurred_at
        from notification_events where id=?
        """,
        rs ->
            rs.next()
                ? new EventRow(
                    rs.getLong("id"),
                    rs.getString("event_type"),
                    rs.getInt("schema_version"),
                    (Long) rs.getObject("company_id"),
                    rs.getString("required_permission_key"),
                    rs.getString("group_key"),
                    rs.getString(7),
                    rs.getTimestamp("occurred_at").toInstant())
                : null,
        eventId);
  }

  private Map<String, String> variables(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid persisted notification variables", exception);
    }
  }

  private OffsetDateTime utc(Instant value) {
    return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
  }

  private Instant fiveMinuteBucket(Instant value) {
    long seconds = value.getEpochSecond();
    return Instant.ofEpochSecond((seconds / 300L) * 300L);
  }

  public enum MaterializationResult {
    CREATED,
    COALESCED,
    SUPPRESSED,
    DUPLICATE,
    MISSING_EVENT
  }

  private record EventRow(
      long id,
      String eventType,
      int schemaVersion,
      Long companyId,
      String requiredPermissionKey,
      String groupKey,
      String variablesJson,
      Instant occurredAt) {}

  private record ExistingNotification(long id, int aggregateCount, Instant readAt) {}

  private record Materialized(long notificationId, boolean coalesced) {}

  private record PreferenceRow(String emailMode, Boolean browserEnabled) {}

  private record EffectivePreference(EmailMode emailMode, boolean browserEnabled) {}
}
