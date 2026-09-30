package com.messaging.contacts.audience;

import com.messaging.contacts.audience.SegmentCompiler.CompiledSegment;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SegmentSampleRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public List<SampleRow> page(CompiledSegment compiled, int limit) {
    Map<String, Object> params = new LinkedHashMap<>(compiled.parameters());
    params.put("limit", limit);
    return jdbcTemplate.query(
        """
        select c.id,
               c.display_name,
               c.first_name,
               c.last_name,
               c.organization,
               c.status,
               c.created_at,
               c.updated_at,
               c.last_activity_at,
               email.normalized_value as primary_email,
               phone.normalized_value as primary_phone
        from contacts c
        left join lateral (
          select ci.normalized_value
          from contact_identities ci
          where ci.company_id = c.company_id
            and ci.contact_id = c.id
            and ci.status = 'ACTIVE'
            and ci.identity_type = 'EMAIL'
          order by ci.is_primary desc, ci.created_at asc, ci.id asc
          limit 1
        ) email on true
        left join lateral (
          select ci.normalized_value
          from contact_identities ci
          where ci.company_id = c.company_id
            and ci.contact_id = c.id
            and ci.status = 'ACTIVE'
            and ci.identity_type = 'PHONE'
          order by ci.is_primary desc, ci.created_at asc, ci.id asc
          limit 1
        ) phone on true
        where
        """
            + compiled.whereSql()
            + " order by c.id asc limit :limit",
        params,
        (rs, rowNum) ->
            new SampleRow(
                rs.getLong("id"),
                rs.getString("display_name"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("organization"),
                rs.getString("primary_email"),
                rs.getString("primary_phone"),
                rs.getString("status"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")),
                instant(rs.getTimestamp("last_activity_at"))));
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  public record SampleRow(
      long id,
      String displayName,
      String firstName,
      String lastName,
      String organization,
      String primaryEmail,
      String primaryPhone,
      String status,
      Instant createdAt,
      Instant updatedAt,
      Instant lastActivityAt) {}
}
