package com.messaging.contacts.processing;

import com.messaging.common.id.IdGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactActivityService {

  private final JdbcTemplate jdbcTemplate;
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
      throw new com.messaging.common.exception.BadRequestException("CONTACT_FIELD_INVALID");
    }
    jdbcTemplate.update(
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

  public List<ActivityItem> page(long companyId, long contactId, long beforeId, int limit) {
    int bounded = Math.max(1, Math.min(limit, 200));
    List<ActivityItem> local =
        jdbcTemplate.query(
            """
            select id, event_type, source_ref, summary_json, occurred_at
            from contact_activity_events
            where company_id = ? and contact_id = ? and (? = 0 or id < ?)
            order by id desc
            limit ?
            """,
            (rs, rowNum) ->
                new ActivityItem(
                    rs.getLong("id"),
                    "CONTACTS",
                    rs.getString("event_type"),
                    rs.getString("source_ref"),
                    rs.getString("summary_json"),
                    rs.getTimestamp("occurred_at").toInstant()),
            companyId,
            contactId,
            beforeId,
            beforeId,
            bounded);
    List<ActivityItem> merged = new ArrayList<>(local);
    for (ContactActivityReadAdapter adapter : readAdapters) {
      merged.addAll(adapter.page(companyId, contactId, beforeId, bounded));
    }
    merged.sort(Comparator.comparing(ActivityItem::occurredAt).reversed().thenComparing(ActivityItem::id).reversed());
    if (merged.size() > bounded) return List.copyOf(merged.subList(0, bounded));
    return List.copyOf(merged);
  }

  public interface ContactActivityReadAdapter {
    List<ActivityItem> page(long companyId, long contactId, long beforeId, int limit);
  }

  public record ActivityItem(
      long id, String source, String type, String sourceRef, String summaryJson, Instant occurredAt) {}
}
