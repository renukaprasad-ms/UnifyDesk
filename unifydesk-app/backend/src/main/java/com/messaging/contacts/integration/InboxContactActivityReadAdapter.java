package com.messaging.contacts.integration;

import com.messaging.contacts.processing.ContactActivityService.ActivityItem;
import com.messaging.contacts.processing.ContactActivityService.ContactActivityReadAdapter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InboxContactActivityReadAdapter implements ContactActivityReadAdapter {

  private static final String TYPE = "INBOX_CONVERSATION";

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public List<ActivityItem> page(
      long companyId,
      long contactId,
      Instant beforeOccurredAt,
      long beforeId,
      int limit,
      Set<String> types) {
    if (types != null && !types.isEmpty() && !types.contains(TYPE)) return List.of();

    int bounded = Math.max(1, Math.min(limit, 201));
    Map<String, Object> params = new HashMap<>();
    params.put("companyId", companyId);
    params.put("contactId", contactId);
    params.put("beforeOccurredAt", Timestamp.from(beforeOccurredAt));
    params.put("beforeId", beforeId);
    params.put("limit", bounded);
    String occurred = "coalesce(c.last_message_at,c.updated_at,c.created_at)";

    return jdbcTemplate.query(
        """
        select c.id,c.provider_type,%s as occurred_at
        from inbox_conversations c
        where c.company_id=:companyId
          and exists (
            select 1
            from inbox_participant_contact_links l
            where l.company_id=c.company_id
              and l.conversation_id=c.id
              and l.contact_id=:contactId
              and l.resolution='MATCHED'
          )
          and (
            %s < :beforeOccurredAt
            or (%s = :beforeOccurredAt and c.id < :beforeId)
          )
        order by occurred_at desc,c.id desc
        limit :limit
        """
            .formatted(occurred, occurred, occurred),
        params,
        (rs, rowNum) -> {
          boolean whatsapp = "META_WHATSAPP".equals(rs.getString("provider_type"));
          return new ActivityItem(
              rs.getLong("id"),
              "INBOX",
              TYPE,
              whatsapp ? "WhatsApp conversation" : "Email conversation",
              "Related inbox conversation",
              whatsapp ? "/inbox/social-media" : "/inbox/email",
              rs.getTimestamp("occurred_at").toInstant());
        });
  }
}
