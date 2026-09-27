package com.messaging.notification.platform.core.api;

import static com.messaging.notification.platform.core.api.NotificationApiDtos.*;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.id.IdGenerator;
import com.messaging.company.service.CompanyIamPermissionService;
import com.messaging.notification.platform.core.config.NotificationPlatformProperties;
import com.messaging.notification.platform.core.realtime.NotificationRealtimePublisher;
import com.messaging.notification.platform.core.registry.NotificationTypeRegistry;
import com.messaging.notification.platform.core.registry.NotificationTypeRegistry.EmailMode;
import com.messaging.notification.platform.core.state.NotificationUnreadCounterService;
import com.messaging.notification.platform.push.BrowserPushReadiness;
import com.messaging.security.service.CurrentUserService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationApiService {

  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

  private final JdbcTemplate jdbcTemplate;
  private final CurrentUserService currentUserService;
  private final CompanyIamPermissionService permissionService;
  private final NotificationUnreadCounterService counterService;
  private final NotificationRealtimePublisher realtimePublisher;
  private final NotificationTypeRegistry registry;
  private final NotificationCursorCodec cursorCodec;
  private final NotificationPlatformProperties properties;
  private final BrowserPushReadiness pushReadiness;

  @Transactional(readOnly = true)
  public NotificationPage list(
      String companyIdValue, Integer requestedLimit, String cursorValue, boolean unreadOnly) {
    long userId = currentUserService.currentUserId();
    Long companyId = parseOptionalId(companyIdValue, "companyId");
    if (companyId != null) {
      requireMember(userId, companyId);
    }
    int limit = requestedLimit == null ? 20 : requestedLimit;
    if (limit < 1 || limit > 50) {
      throw new BadRequestException("Invalid notification limit");
    }

    Set<String> effectivePermissions = effectivePermissions(userId, companyId);
    NotificationCursorCodec.Decoded scanCursor =
        cursorCodec.decode(cursorValue, companyId, unreadOnly);
    int windowSize = Math.min(51, Math.max(21, limit + 1));
    List<Row> visibleRows = new ArrayList<>(limit + 1);
    boolean exhausted = false;

    while (visibleRows.size() <= limit && !exhausted) {
      List<Object> args = new ArrayList<>();
      args.add(userId);
      StringBuilder sql =
          new StringBuilder(
              """
              select id,company_id,notification_type,category,severity,required_permission_key,
                     title,body,aggregate_count,action_kind,action_path,first_event_at,last_event_at,
                     seen_at,read_at
              from user_notifications
              where user_id=?
                and (expires_at is null or expires_at > now())
                and
              """);
      sql.append(visibilityClause(companyId, effectivePermissions, args));
      if (unreadOnly) {
        sql.append(" and read_at is null");
      }
      if (scanCursor != null) {
        sql.append(" and (last_event_at < ? or (last_event_at = ? and id < ?))");
        args.add(Timestamp.from(scanCursor.lastEventAt()));
        args.add(Timestamp.from(scanCursor.lastEventAt()));
        args.add(scanCursor.id());
      }
      sql.append(" order by last_event_at desc,id desc limit ?");
      args.add(windowSize);

      List<Row> candidates = jdbcTemplate.query(sql.toString(), this::mapRow, args.toArray());
      for (Row candidate : candidates) {
        if (visible(companyId, effectivePermissions, candidate)) {
          visibleRows.add(candidate);
          if (visibleRows.size() > limit) {
            break;
          }
        }
      }

      if (visibleRows.size() > limit) {
        break;
      }
      if (candidates.size() < windowSize) {
        exhausted = true;
        break;
      }
      Row lastCandidate = candidates.getLast();
      scanCursor =
          new NotificationCursorCodec.Decoded(
              lastCandidate.lastEventAt(), lastCandidate.id());
    }

    boolean hasMore = visibleRows.size() > limit;
    List<Row> pageRows = visibleRows.stream().limit(limit).toList();
    String nextCursor =
        hasMore
            ? cursorCodec.encode(
                companyId,
                unreadOnly,
                pageRows.getLast().lastEventAt(),
                pageRows.getLast().id())
            : null;
    return new NotificationPage(pageRows.stream().map(this::toItem).toList(), nextCursor, hasMore);
  }

  @Transactional(readOnly = true)
  public NotificationItem get(String notificationIdValue, String companyIdValue) {
    long userId = currentUserService.currentUserId();
    long notificationId = parseId(notificationIdValue, "notificationId");
    Long companyId = parseOptionalId(companyIdValue, "companyId");
    if (companyId != null && !hasMember(userId, companyId)) {
      throw new NotFoundException("Notification not found");
    }
    Set<String> effectivePermissions = effectivePermissions(userId, companyId);
    Row row = findAddressed(userId, notificationId, companyId, false);
    if (row == null || !visible(companyId, effectivePermissions, row)) {
      throw new NotFoundException("Notification not found");
    }
    return toItem(row);
  }

  @Transactional(readOnly = true)
  public UnreadCount unreadCount(String companyIdValue) {
    long userId = currentUserService.currentUserId();
    Long companyId = parseOptionalId(companyIdValue, "companyId");
    if (companyId != null) {
      requireMember(userId, companyId);
    }
    Set<String> effectivePermissions = effectivePermissions(userId, companyId);
    NotificationUnreadCounterService.Counts counts =
        counterService.counts(userId, companyId, effectivePermissions);
    return new UnreadCount(counts.account(), counts.company(), counts.total());
  }

  @Transactional
  public SeenResponse markSeen(SeenRequest request) {
    if (request == null || request.notificationIds() == null) {
      throw new BadRequestException("notificationIds are required");
    }
    long userId = currentUserService.currentUserId();
    Long companyId = parseOptionalId(request.companyId(), "companyId");
    if (companyId != null) {
      requireMember(userId, companyId);
    }
    Set<String> effectivePermissions = effectivePermissions(userId, companyId);
    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    for (String value : request.notificationIds()) {
      ids.add(parseId(value, "notificationId"));
    }
    if (ids.isEmpty() || ids.size() > 100) {
      throw new BadRequestException("notificationIds must contain 1 to 100 distinct ids");
    }

    int updated = 0;
    for (Long id : ids) {
      Row row = findAddressed(userId, id, companyId, true);
      if (row == null || !visible(companyId, effectivePermissions, row)) {
        continue;
      }
      int changed =
          jdbcTemplate.update(
              "update user_notifications set seen_at=coalesce(seen_at,now()),updated_at=case when seen_at is null then now() else updated_at end where id=? and user_id=? and seen_at is null",
              id,
              userId);
      if (changed > 0) {
        updated++;
        realtimePublisher.afterCommit(userId, row.companyId(), row.id(), "SEEN_STATE");
      }
    }
    return new SeenResponse(updated);
  }

  @Transactional
  public ReadStateResponse setReadState(String notificationIdValue, ReadStateRequest request) {
    if (request == null) {
      throw new BadRequestException("Invalid read-state request");
    }
    long userId = currentUserService.currentUserId();
    long notificationId = parseId(notificationIdValue, "notificationId");
    Long companyId = parseOptionalId(request.companyId(), "companyId");
    if (companyId != null && !hasMember(userId, companyId)) {
      throw new NotFoundException("Notification not found");
    }
    Set<String> effectivePermissions = effectivePermissions(userId, companyId);
    Row row = findAddressed(userId, notificationId, companyId, true);
    if (row == null || !visible(companyId, effectivePermissions, row)) {
      throw new NotFoundException("Notification not found");
    }

    boolean changed = false;
    if (request.read() && row.readAt() == null) {
      changed =
          jdbcTemplate.update(
                  "update user_notifications set read_at=now(),updated_at=now() where id=? and user_id=? and read_at is null",
                  notificationId,
                  userId)
              == 1;
      if (changed) {
        counterService.decrement(
            userId, row.companyId(), row.requiredPermissionKey());
      }
    } else if (!request.read() && row.readAt() != null) {
      changed =
          jdbcTemplate.update(
                  "update user_notifications set read_at=null,updated_at=now() where id=? and user_id=? and read_at is not null",
                  notificationId,
                  userId)
              == 1;
      if (changed) {
        counterService.increment(
            userId, row.companyId(), row.requiredPermissionKey());
      }
    }
    if (changed) {
      realtimePublisher.afterCommit(userId, row.companyId(), row.id(), "READ_STATE");
    }

    Row refreshed = findAddressed(userId, notificationId, companyId, false);
    NotificationUnreadCounterService.Counts counts =
        counterService.counts(userId, companyId, effectivePermissions);
    return new ReadStateResponse(
        toItem(refreshed),
        new UnreadCount(counts.account(), counts.company(), counts.total()));
  }

  @Transactional
  public ReadAllResponse readAll(ReadAllRequest request) {
    if (request == null) {
      throw new BadRequestException("Invalid read-all request");
    }
    long userId = currentUserService.currentUserId();
    Long companyId = parseOptionalId(request.companyId(), "companyId");
    if (companyId != null) {
      requireMember(userId, companyId);
    }
    Set<String> effectivePermissions = effectivePermissions(userId, companyId);
    Instant requestStart = Instant.now();
    Instant cutoff =
        request.before() == null || request.before().isAfter(requestStart)
            ? requestStart
            : request.before();

    long afterId = 0L;
    int updated = 0;
    while (true) {
      List<Object> args = new ArrayList<>();
      args.add(userId);
      String scope = visibilityClause(companyId, effectivePermissions, args);
      args.add(Timestamp.from(cutoff));
      args.add(afterId);
      String sql =
          """
          select id,company_id,notification_type,category,severity,required_permission_key,
                 title,body,aggregate_count,action_kind,action_path,first_event_at,last_event_at,
                 seen_at,read_at
          from user_notifications
          where user_id=? and %s and read_at is null
            and (expires_at is null or expires_at > now())
            and last_event_at <= ? and id > ?
          order by id
          limit 500
          for update
          """
              .formatted(scope);
      List<Row> batch = jdbcTemplate.query(sql, this::mapRow, args.toArray());
      if (batch.isEmpty()) {
        break;
      }
      for (Row row : batch) {
        afterId = Math.max(afterId, row.id());
        if (!visible(companyId, effectivePermissions, row)) {
          continue;
        }
        if (jdbcTemplate.update(
                "update user_notifications set read_at=now(),updated_at=now() where id=? and user_id=? and read_at is null",
                row.id(),
                userId)
            == 1) {
          updated++;
          counterService.decrement(
              userId, row.companyId(), row.requiredPermissionKey());
        }
      }
      if (batch.size() < 500) {
        break;
      }
    }

    if (updated > 0) {
      realtimePublisher.afterCommit(userId, companyId, null, "READ_STATE");
    }
    NotificationUnreadCounterService.Counts counts =
        counterService.counts(userId, companyId, effectivePermissions);
    return new ReadAllResponse(
        updated,
        cutoff,
        new UnreadCount(counts.account(), counts.company(), counts.total()));
  }

  @Transactional(readOnly = true)
  public PreferencesResponse preferences(String companyIdValue) {
    long userId = currentUserService.currentUserId();
    Long companyId = parseOptionalId(companyIdValue, "companyId");
    if (companyId != null) {
      requireMember(userId, companyId);
    }
    return preferencesFor(userId, companyId);
  }

  @Transactional
  public PreferencesResponse updatePreferences(String companyIdValue, PreferencesRequest request) {
    if (request == null || request.quietHours() == null || request.types() == null) {
      throw new BadRequestException("Invalid notification preferences");
    }
    long userId = currentUserService.currentUserId();
    Long companyId = parseOptionalId(companyIdValue, "companyId");
    if (companyId != null) {
      requireMember(userId, companyId);
    }

    String timezone = validateTimezone(request.timezone());
    Quiet normalized = normalizeQuiet(request.quietHours());

    deleteSettings(userId, companyId);
    jdbcTemplate.update(
        """
        insert into notification_user_settings(
          id,user_id,company_id,timezone,quiet_hours_enabled,quiet_start,quiet_end,created_at,updated_at)
        values (?,?,?,?,?,?,?,?,?)
        """,
        IdGenerator.nextId(),
        userId,
        companyId,
        timezone,
        normalized.enabled(),
        normalized.start(),
        normalized.end(),
        Instant.now(),
        Instant.now());

    deleteOverrides(userId, companyId);
    LinkedHashSet<String> seenTypes = new LinkedHashSet<>();
    for (TypePreferenceRequest item : request.types()) {
      if (item == null || !seenTypes.add(item.type())) {
        throw new BadRequestException("Duplicate notification type");
      }
      NotificationTypeRegistry.Definition definition = registry.requireNotificationType(item.type());
      EmailMode emailMode =
          item.emailMode() == null
              ? null
              : parseEmailMode(item.emailMode(), definition);
      if (definition.mandatoryEmail() && emailMode == EmailMode.OFF) {
        throw new BadRequestException("Mandatory notification email cannot be disabled");
      }
      jdbcTemplate.update(
          """
          insert into notification_preference_overrides(
            id,user_id,company_id,notification_type,email_mode,browser_enabled,created_at,updated_at)
          values (?,?,?,?,?,?,?,?)
          """,
          IdGenerator.nextId(),
          userId,
          companyId,
          definition.notificationType(),
          emailMode == null ? null : emailMode.name(),
          item.browserEnabled(),
          Instant.now(),
          Instant.now());
    }
    realtimePublisher.afterCommit(userId, companyId, null, "PREFERENCE_RELEVANCE");
    return preferencesFor(userId, companyId);
  }

  private PreferencesResponse preferencesFor(long userId, Long companyId) {
    Settings settings = effectiveSettings(userId, companyId);
    List<TypePreferenceResponse> types =
        registry.definitions().stream()
            .map(
                definition -> {
                  OverrideValue scoped =
                      companyId == null
                          ? findOverride(userId, null, definition.notificationType())
                          : findOverride(userId, companyId, definition.notificationType());
                  OverrideValue account =
                      companyId == null
                          ? null
                          : findOverride(userId, null, definition.notificationType());
                  OverrideValue effective = scoped != null ? scoped : account;
                  String overrideMode = effective == null ? null : effective.emailMode();
                  String effectiveMode =
                      definition.mandatoryEmail()
                          ? EmailMode.IMMEDIATE.name()
                          : overrideMode == null
                              ? definition.defaultEmailMode().name()
                              : overrideMode;
                  Boolean browserOverride = effective == null ? null : effective.browserEnabled();
                  boolean browserEffective =
                      browserOverride == null
                          ? definition.defaultBrowserEnabled()
                          : browserOverride;
                  return new TypePreferenceResponse(
                      definition.notificationType(),
                      definition.label(),
                      definition.category(),
                      definition.mandatoryEmail(),
                      new EmailPreference(
                          definition.allowedEmailModes().stream().map(Enum::name).toList(),
                          definition.defaultEmailMode().name(),
                          effectiveMode,
                          overrideMode,
                          definition.mandatoryEmail()),
                      new BrowserPreference(
                          definition.defaultBrowserEnabled(),
                          browserEffective,
                          browserOverride,
                          false));
                })
            .toList();

    return new PreferencesResponse(
        new PreferenceScope(companyId == null ? null : Long.toString(companyId)),
        new TimezonePreference(settings.timezone(), settings.timezoneSource()),
        new QuietHoursResponse(
            settings.quietEnabled(),
            settings.quietStart().format(TIME_FORMAT),
            settings.quietEnd().format(TIME_FORMAT)),
        new ChannelAvailability(
            properties.getEmail().isEnabled(), pushReadiness.ready()),
        types);
  }

  private Settings effectiveSettings(long userId, Long companyId) {
    SettingsRow company = companyId == null ? null : findSettings(userId, companyId);
    SettingsRow account = findSettings(userId, null);
    SettingsRow chosen = company != null ? company : account;

    String companyTimezone = null;
    if (companyId != null) {
      companyTimezone =
          jdbcTemplate.query(
              "select timezone from companies where id=?",
              rs -> rs.next() ? rs.getString(1) : null,
              companyId);
    }

    String timezone;
    String source;
    if (chosen != null && chosen.timezone() != null && !chosen.timezone().isBlank()) {
      timezone = chosen.timezone();
      source = chosen.companyId() == null ? "ACCOUNT_OVERRIDE" : "USER_OVERRIDE";
    } else if (companyTimezone != null && !companyTimezone.isBlank()) {
      timezone = companyTimezone;
      source = "COMPANY_DEFAULT";
    } else {
      timezone = "UTC";
      source = "UTC_DEFAULT";
    }

    boolean quietEnabled = chosen == null || chosen.quietEnabled();
    LocalTime start =
        chosen == null || chosen.quietStart() == null
            ? LocalTime.of(22, 0)
            : chosen.quietStart();
    LocalTime end =
        chosen == null || chosen.quietEnd() == null
            ? LocalTime.of(8, 0)
            : chosen.quietEnd();
    return new Settings(timezone, source, quietEnabled, start, end);
  }

  private Row findAddressed(long userId, long notificationId, Long companyId, boolean lock) {
    String scope =
        companyId == null ? "company_id is null" : "(company_id is null or company_id=?)";
    List<Object> args = new ArrayList<>();
    args.add(notificationId);
    args.add(userId);
    if (companyId != null) {
      args.add(companyId);
    }
    String sql =
        """
        select id,company_id,notification_type,category,severity,required_permission_key,
               title,body,aggregate_count,action_kind,action_path,first_event_at,last_event_at,
               seen_at,read_at
        from user_notifications
        where id=? and user_id=? and %s
          and (expires_at is null or expires_at > now())
        %s
        """
            .formatted(scope, lock ? "for update" : "");
    return jdbcTemplate.query(sql, rs -> rs.next() ? mapRow(rs, 0) : null, args.toArray());
  }

  private Row mapRow(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
    return new Row(
        rs.getLong("id"),
        (Long) rs.getObject("company_id"),
        rs.getString("notification_type"),
        rs.getString("category"),
        rs.getString("severity"),
        rs.getString("required_permission_key"),
        rs.getString("title"),
        rs.getString("body"),
        rs.getInt("aggregate_count"),
        rs.getString("action_kind"),
        rs.getString("action_path"),
        rs.getTimestamp("first_event_at").toInstant(),
        rs.getTimestamp("last_event_at").toInstant(),
        rs.getTimestamp("seen_at") == null ? null : rs.getTimestamp("seen_at").toInstant(),
        rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant());
  }

  private boolean visible(
      Long selectedCompanyId, Set<String> effectivePermissions, Row row) {
    if (row.companyId() == null) {
      return true;
    }
    if (selectedCompanyId == null || !Objects.equals(selectedCompanyId, row.companyId())) {
      return false;
    }
    return row.requiredPermissionKey() == null
        || effectivePermissions.contains(row.requiredPermissionKey());
  }

  private Set<String> effectivePermissions(long userId, Long companyId) {
    return companyId == null
        ? Set.of()
        : permissionService.effectivePermissions(userId, companyId);
  }

  private String visibilityClause(
      Long companyId, Set<String> effectivePermissions, List<Object> args) {
    if (companyId == null) {
      return "company_id is null";
    }

    args.add(companyId);
    if (effectivePermissions == null || effectivePermissions.isEmpty()) {
      return "(company_id is null or (company_id=? and required_permission_key is null))";
    }

    List<String> permissions = effectivePermissions.stream().sorted().toList();
    args.addAll(permissions);
    String placeholders = String.join(",", Collections.nCopies(permissions.size(), "?"));
    return "(company_id is null or (company_id=? and (required_permission_key is null"
        + " or required_permission_key in ("
        + placeholders
        + "))))";
  }

  private NotificationItem toItem(Row row) {
    return new NotificationItem(
        Long.toString(row.id()),
        row.type(),
        row.category(),
        row.severity(),
        new Scope(
            row.companyId() == null ? "ACCOUNT" : "COMPANY",
            row.companyId() == null ? null : Long.toString(row.companyId())),
        row.title(),
        row.body(),
        row.aggregateCount(),
        row.actionKind() == null ? null : new Action(row.actionKind(), row.actionPath()),
        row.firstEventAt(),
        row.lastEventAt(),
        row.seenAt(),
        row.readAt(),
        row.readAt() == null);
  }

  private void requireMember(long userId, long companyId) {
    if (!hasMember(userId, companyId)) {
      throw new ForbiddenException("Company access denied");
    }
  }

  private boolean hasMember(long userId, long companyId) {
    Boolean allowed =
        jdbcTemplate.queryForObject(
            """
            select exists(
              select 1 from company_members m
              join companies c on c.id=m.company_id
              where m.company_id=? and m.user_id=? and m.status='ACTIVE'
                and c.status in ('ACTIVE','SUSPENDED')
            )
            """,
            Boolean.class,
            companyId,
            userId);
    return Boolean.TRUE.equals(allowed);
  }

  private long parseId(String value, String field) {
    try {
      if (value == null || value.isBlank()) {
        throw new NumberFormatException();
      }
      long id = Long.parseLong(value);
      if (id <= 0) {
        throw new NumberFormatException();
      }
      return id;
    } catch (NumberFormatException exception) {
      throw new BadRequestException("Invalid " + field);
    }
  }

  private Long parseOptionalId(String value, String field) {
    return value == null || value.isBlank() ? null : parseId(value, field);
  }

  private EmailMode parseEmailMode(
      String value, NotificationTypeRegistry.Definition definition) {
    try {
      EmailMode mode = EmailMode.valueOf(value);
      if (!definition.allowedEmailModes().contains(mode)) {
        throw new BadRequestException("Email mode is not allowed for " + definition.notificationType());
      }
      return mode;
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Invalid notification email mode");
    }
  }

  private String validateTimezone(String value) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException("Invalid notification timezone");
    }
    try {
      return ZoneId.of(value).getId();
    } catch (Exception exception) {
      throw new BadRequestException("Invalid notification timezone");
    }
  }

  private Quiet normalizeQuiet(QuietHoursRequest quietHours) {
    if (!quietHours.enabled()) {
      return new Quiet(false, LocalTime.of(22, 0), LocalTime.of(8, 0));
    }
    try {
      LocalTime start = LocalTime.parse(quietHours.start(), TIME_FORMAT);
      LocalTime end = LocalTime.parse(quietHours.end(), TIME_FORMAT);
      if (start.equals(end)) {
        return new Quiet(false, start, end);
      }
      return new Quiet(true, start, end);
    } catch (DateTimeParseException | NullPointerException exception) {
      throw new BadRequestException("Invalid notification quiet hours");
    }
  }

  private void deleteSettings(long userId, Long companyId) {
    if (companyId == null) {
      jdbcTemplate.update(
          "delete from notification_user_settings where user_id=? and company_id is null", userId);
    } else {
      jdbcTemplate.update(
          "delete from notification_user_settings where user_id=? and company_id=?", userId, companyId);
    }
  }

  private void deleteOverrides(long userId, Long companyId) {
    if (companyId == null) {
      jdbcTemplate.update(
          "delete from notification_preference_overrides where user_id=? and company_id is null",
          userId);
    } else {
      jdbcTemplate.update(
          "delete from notification_preference_overrides where user_id=? and company_id=?",
          userId,
          companyId);
    }
  }

  private SettingsRow findSettings(long userId, Long companyId) {
    String sql =
        companyId == null
            ? "select company_id,timezone,quiet_hours_enabled,quiet_start,quiet_end from notification_user_settings where user_id=? and company_id is null"
            : "select company_id,timezone,quiet_hours_enabled,quiet_start,quiet_end from notification_user_settings where user_id=? and company_id=?";
    Object[] args =
        companyId == null ? new Object[] {userId} : new Object[] {userId, companyId};
    return jdbcTemplate.query(
        sql,
        rs ->
            rs.next()
                ? new SettingsRow(
                    (Long) rs.getObject("company_id"),
                    rs.getString("timezone"),
                    rs.getBoolean("quiet_hours_enabled"),
                    rs.getTime("quiet_start") == null
                        ? null
                        : rs.getTime("quiet_start").toLocalTime(),
                    rs.getTime("quiet_end") == null
                        ? null
                        : rs.getTime("quiet_end").toLocalTime())
                : null,
        args);
  }

  private OverrideValue findOverride(long userId, Long companyId, String type) {
    String sql =
        companyId == null
            ? "select email_mode,browser_enabled from notification_preference_overrides where user_id=? and company_id is null and notification_type=?"
            : "select email_mode,browser_enabled from notification_preference_overrides where user_id=? and company_id=? and notification_type=?";
    Object[] args =
        companyId == null
            ? new Object[] {userId, type}
            : new Object[] {userId, companyId, type};
    return jdbcTemplate.query(
        sql,
        rs ->
            rs.next()
                ? new OverrideValue(
                    rs.getString("email_mode"), (Boolean) rs.getObject("browser_enabled"))
                : null,
        args);
  }

  private record Row(
      long id,
      Long companyId,
      String type,
      String category,
      String severity,
      String requiredPermissionKey,
      String title,
      String body,
      int aggregateCount,
      String actionKind,
      String actionPath,
      Instant firstEventAt,
      Instant lastEventAt,
      Instant seenAt,
      Instant readAt) {}

  private record SettingsRow(
      Long companyId,
      String timezone,
      boolean quietEnabled,
      LocalTime quietStart,
      LocalTime quietEnd) {}

  private record Settings(
      String timezone,
      String timezoneSource,
      boolean quietEnabled,
      LocalTime quietStart,
      LocalTime quietEnd) {}

  private record OverrideValue(String emailMode, Boolean browserEnabled) {}

  private record Quiet(boolean enabled, LocalTime start, LocalTime end) {}
}
