package com.messaging.contacts.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.id.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactActivityService {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final List<ContactActivityReadAdapter> readAdapters;

  public void append(
      long companyId,
      long contactId,
      String eventType,
      String actorType,
      String actorId,
      String sourceRef,
      String summaryJson,
      Instant occurredAt) {
    if (summaryJson != null && summaryJson.length() > 4096) {
      throw new BadRequestException("CONTACT_FIELD_INVALID");
    }
    jdbcTemplate.getJdbcTemplate().update(
        """
        insert into contact_activity_events (
          id, company_id, contact_id, event_type, actor_type, actor_id, source_ref,
          summary_json, occurred_at, created_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
        """,
        IdGenerator.nextId(),
        companyId,
        contactId,
        eventType,
        actorType,
        actorId,
        sourceRef,
        summaryJson,
        Timestamp.from(occurredAt == null ? Instant.now() : occurredAt),
        Timestamp.from(Instant.now()));
  }

  public ActivityPage page(
      long companyId,
      long contactId,
      String cursor,
      int limit,
      List<String> requestedTypes) {
    int bounded = Math.max(1, Math.min(limit, 200));
    Cursor decoded = decode(cursor);
    Set<String> types = normalizeTypes(requestedTypes);
    int fetchSize = bounded + 1;

    List<ActivityItem> merged =
        new ArrayList<>(localPage(companyId, contactId, decoded, fetchSize, types));
    for (ContactActivityReadAdapter adapter : readAdapters) {
      merged.addAll(
          adapter.page(
              companyId,
              contactId,
              decoded.beforeOccurredAt(),
              decoded.beforeId(),
              fetchSize,
              types));
    }

    merged.sort(
        Comparator.comparing(ActivityItem::occurredAt)
            .reversed()
            .thenComparing(Comparator.comparingLong(ActivityItem::id).reversed()));

    boolean hasMore = merged.size() > bounded;
    List<ActivityItem> items =
        List.copyOf(merged.subList(0, Math.min(bounded, merged.size())));
    String nextCursor =
        hasMore && !items.isEmpty() ? encode(items.getLast()) : null;
    return new ActivityPage(items, nextCursor, hasMore);
  }

  private List<ActivityItem> localPage(
      long companyId,
      long contactId,
      Cursor cursor,
      int limit,
      Set<String> types) {
    Map<String, Object> params = new HashMap<>();
    params.put("companyId", companyId);
    params.put("contactId", contactId);
    params.put("beforeOccurredAt", Timestamp.from(cursor.beforeOccurredAt()));
    params.put("beforeId", cursor.beforeId());
    params.put("limit", limit);

    String typePredicate = "";
    if (!types.isEmpty()) {
      params.put("types", types);
      typePredicate = " and event_type in (:types)";
    }

    return jdbcTemplate.query(
        """
        select id, event_type, summary_json, occurred_at
        from contact_activity_events
        where company_id = :companyId
          and contact_id = :contactId
          and (
            occurred_at < :beforeOccurredAt
            or (occurred_at = :beforeOccurredAt and id < :beforeId)
          )
        """
            + typePredicate
            + " order by occurred_at desc, id desc limit :limit",
        params,
        (rs, rowNum) ->
            normalized(
                rs.getLong("id"),
                rs.getString("event_type"),
                rs.getString("summary_json"),
                rs.getTimestamp("occurred_at").toInstant()));
  }

  private ActivityItem normalized(
      long id, String eventType, String summaryJson, Instant occurredAt) {
    String title = humanize(eventType);
    String summary = null;
    String href = null;
    if (summaryJson != null && !summaryJson.isBlank()) {
      try {
        JsonNode node = objectMapper.readTree(summaryJson);
        if (node.isTextual()) {
          summary = bounded(node.textValue(), 500);
        } else if (node.isObject()) {
          if (node.path("title").isTextual()) {
            title = bounded(node.path("title").textValue(), 160);
          }
          if (node.path("summary").isTextual()) {
            summary = bounded(node.path("summary").textValue(), 500);
          }
          if (node.path("href").isTextual() && node.path("href").textValue().startsWith("/")) {
            href = bounded(node.path("href").textValue(), 500);
          }
        }
      } catch (Exception ignored) {
        summary = null;
      }
    }
    return new ActivityItem(id, "CONTACTS", eventType, title, summary, href, occurredAt);
  }

  private Set<String> normalizeTypes(List<String> requestedTypes) {
    if (requestedTypes == null || requestedTypes.isEmpty()) return Set.of();
    if (requestedTypes.size() > 20) {
      throw new BadRequestException("CONTACT_FIELD_INVALID");
    }
    Set<String> result = new LinkedHashSet<>();
    for (String type : requestedTypes) {
      if (type == null || type.isBlank() || type.length() > 64) {
        throw new BadRequestException("CONTACT_FIELD_INVALID");
      }
      result.add(type.trim().toUpperCase(Locale.ROOT));
    }
    return Set.copyOf(result);
  }

  private Cursor decode(String value) {
    if (value == null || value.isBlank()) {
      return new Cursor(Instant.now().plusSeconds(1), Long.MAX_VALUE);
    }
    try {
      String decoded =
          new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
      String[] parts = decoded.split(":", -1);
      if (parts.length != 2) throw new IllegalArgumentException();
      long epochMillis = Long.parseLong(parts[0]);
      long id = Long.parseLong(parts[1]);
      if (id <= 0) throw new IllegalArgumentException();
      Instant instant = Instant.ofEpochMilli(epochMillis);
      if (instant.isAfter(Instant.now().plusSeconds(60))) {
        throw new IllegalArgumentException();
      }
      return new Cursor(instant, id);
    } catch (RuntimeException exception) {
      throw new BadRequestException("CONTACT_FIELD_INVALID");
    }
  }

  private String encode(ActivityItem item) {
    String raw = item.occurredAt().toEpochMilli() + ":" + item.id();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private String humanize(String value) {
    if (value == null || value.isBlank()) return "Contact activity";
    String normalized = value.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
    return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
  }

  private String bounded(String value, int max) {
    if (value == null || value.length() <= max) return value;
    return value.substring(0, max);
  }

  public interface ContactActivityReadAdapter {
    List<ActivityItem> page(
        long companyId,
        long contactId,
        Instant beforeOccurredAt,
        long beforeId,
        int limit,
        Set<String> types);
  }

  public record ActivityPage(
      List<ActivityItem> items, String nextCursor, boolean hasMore) {}

  public record ActivityItem(
      long id,
      String source,
      String type,
      String title,
      String summary,
      String href,
      Instant occurredAt) {}

  private record Cursor(Instant beforeOccurredAt, long beforeId) {}
}
