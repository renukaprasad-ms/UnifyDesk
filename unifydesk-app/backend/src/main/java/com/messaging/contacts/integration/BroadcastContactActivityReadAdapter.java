package com.messaging.contacts.integration;

import com.messaging.contacts.processing.ContactActivityService.ActivityItem;
import com.messaging.contacts.processing.ContactActivityService.ContactActivityReadAdapter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BroadcastContactActivityReadAdapter implements ContactActivityReadAdapter {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public List<ActivityItem> page(
      long companyId,
      long contactId,
      Instant beforeOccurredAt,
      long beforeId,
      int limit,
      Set<String> types) {
    int bounded = Math.max(1, Math.min(limit, 201));
    Map<String, Object> params = new HashMap<>();
    params.put("companyId", companyId);
    params.put("contactId", contactId);
    params.put("beforeOccurredAt", Timestamp.from(beforeOccurredAt));
    params.put("beforeId", beforeId);
    params.put("limit", bounded);

    String typePredicate = "";
    if (types != null && !types.isEmpty()) {
      params.put("types", types);
      typePredicate = " and ('BROADCAST_' || recipient.status) in (:types)";
    }

    String occurred =
        "coalesce(recipient.accepted_at, recipient.bounced_at, recipient.failed_at, "
            + "recipient.updated_at, recipient.created_at)";

    return jdbcTemplate.query(
        """
        select recipient.id,
               recipient.status,
               message.channel_type,
               recipient.failure_code,
               %s as occurred_at
        from broadcast_recipients recipient
        join broadcast_runs run
          on run.id = recipient.run_id
         and run.company_id = recipient.company_id
        join broadcast_messages message
          on message.id = run.broadcast_message_id
         and message.company_id = recipient.company_id
        where recipient.company_id = :companyId
          and recipient.contact_id = :contactId
          and (
            %s < :beforeOccurredAt
            or (%s = :beforeOccurredAt and recipient.id < :beforeId)
          )
        """
            .formatted(occurred, occurred, occurred)
            + typePredicate
            + " order by occurred_at desc, recipient.id desc limit :limit",
        params,
        (rs, rowNum) -> {
          String status = rs.getString("status");
          String channel = rs.getString("channel_type");
          String type = "BROADCAST_" + status;
          String title =
              channel.substring(0, 1)
                  + channel.substring(1).toLowerCase(Locale.ROOT)
                  + " broadcast";
          String summary =
              rs.getString("failure_code") == null
                  ? "Recipient status: " + status.replace('_', ' ').toLowerCase(Locale.ROOT)
                  : "Recipient status: "
                      + status.replace('_', ' ').toLowerCase(Locale.ROOT)
                      + " ("
                      + rs.getString("failure_code")
                      + ")";
          return new ActivityItem(
              rs.getLong("id"),
              "BROADCAST",
              type,
              title,
              summary,
              null,
              rs.getTimestamp("occurred_at").toInstant());
        });
  }
}
