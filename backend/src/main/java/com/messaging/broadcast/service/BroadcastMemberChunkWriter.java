package com.messaging.broadcast.service;

import com.messaging.common.id.IdGenerator;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastMemberChunkWriter {

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public int write(Long companyId, Long groupId, List<MemberRow> rows) {
    if (rows.isEmpty()) return 0;
    Instant now = Instant.now();
    int[] counts =
        jdbcTemplate.batchUpdate(
            """
            insert into broadcast_group_members (
              id, group_id, company_id, destination, normalized_destination,
              display_name, metadata_json, status, active_since, disabled_at,
              created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, null, ?, ?)
            on conflict (group_id, normalized_destination)
            do update set
              destination = excluded.destination,
              display_name = excluded.display_name,
              metadata_json = excluded.metadata_json,
              status = 'ACTIVE',
              active_since = excluded.active_since,
              disabled_at = null,
              updated_at = excluded.updated_at
            where broadcast_group_members.status = 'DISABLED'
            """,
            new BatchPreparedStatementSetter() {
              @Override
              public void setValues(PreparedStatement ps, int index) throws java.sql.SQLException {
                MemberRow row = rows.get(index);
                ps.setLong(1, IdGenerator.nextId());
                ps.setLong(2, groupId);
                ps.setLong(3, companyId);
                ps.setString(4, row.destination());
                ps.setString(5, row.normalizedDestination());
                ps.setString(6, row.displayName());
                ps.setString(7, row.metadataJson());
                Timestamp timestamp = Timestamp.from(now);
                ps.setTimestamp(8, timestamp);
                ps.setTimestamp(9, timestamp);
                ps.setTimestamp(10, timestamp);
              }

              @Override
              public int getBatchSize() {
                return rows.size();
              }
            });

    int changed = 0;
    for (int count : counts) {
      if (count > 0 || count == java.sql.Statement.SUCCESS_NO_INFO) changed++;
    }
    if (changed > 0) {
      jdbcTemplate.update(
          """
          update broadcast_groups
          set member_count = member_count + ?, updated_at = ?
          where id = ? and company_id = ?
          """,
          changed,
          Timestamp.from(now),
          groupId,
          companyId);
    }
    return changed;
  }

  public record MemberRow(
      String destination, String normalizedDestination, String displayName, String metadataJson) {}
}
