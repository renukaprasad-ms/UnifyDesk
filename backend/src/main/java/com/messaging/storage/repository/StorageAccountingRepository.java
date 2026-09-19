package com.messaging.storage.repository;

import com.messaging.storage.dto.StorageUsageBreakdownResponse;
import com.messaging.storage.enums.StorageEventType;
import com.messaging.storage.enums.StorageUsageType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StorageAccountingRepository {

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;

  public void lockCompany(long companyId) {
    jdbcTemplate.queryForObject(
        "select id from companies where id = ? for update", Long.class, companyId);
  }

  public boolean hasEvent(String idempotencyKey) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from storage_usage_events where idempotency_key = ?",
            Integer.class,
            idempotencyKey);
    return count != null && count > 0;
  }

  public Set<String> existingEventKeys(List<String> idempotencyKeys) {
    if (idempotencyKeys == null || idempotencyKeys.isEmpty()) {
      return Set.of();
    }

    List<String> keys =
        namedJdbcTemplate.queryForList(
            "select idempotency_key from storage_usage_events where idempotency_key in (:keys)",
            Map.of("keys", idempotencyKeys),
            String.class);
    return new HashSet<>(keys);
  }

  public int[] appendEvents(List<StorageEventInsert> events) {
    if (events == null || events.isEmpty()) {
      return new int[0];
    }

    SqlParameterSource[] batch =
        events.stream()
            .map(
                event ->
                    new MapSqlParameterSource()
                        .addValue("id", event.id())
                        .addValue("companyId", event.companyId())
                        .addValue("usageType", event.usageType().name())
                        .addValue("resourceType", event.resourceType())
                        .addValue("resourceId", event.resourceId())
                        .addValue("eventType", event.eventType().name())
                        .addValue("usedDelta", event.usedBytesDelta())
                        .addValue("reservedDelta", event.reservedBytesDelta())
                        .addValue("idempotencyKey", event.idempotencyKey())
                        .addValue("createdAt", Timestamp.from(event.createdAt())))
            .toArray(SqlParameterSource[]::new);

    return namedJdbcTemplate.batchUpdate(
        """
        insert into storage_usage_events (
          id, company_id, usage_type, resource_type, resource_id, event_type,
          used_bytes_delta, reserved_bytes_delta, idempotency_key, created_at
        ) values (
          :id, :companyId, :usageType, :resourceType, :resourceId, :eventType,
          :usedDelta, :reservedDelta, :idempotencyKey, :createdAt
        )
        on conflict (idempotency_key) do nothing
        """,
        batch);
  }


  public boolean appendEvent(
      long id,
      long companyId,
      StorageUsageType usageType,
      String resourceType,
      String resourceId,
      StorageEventType eventType,
      long usedBytesDelta,
      long reservedBytesDelta,
      String idempotencyKey,
      Instant createdAt) {
    int updated =
        namedJdbcTemplate.update(
            """
            insert into storage_usage_events (
              id, company_id, usage_type, resource_type, resource_id, event_type,
              used_bytes_delta, reserved_bytes_delta, idempotency_key, created_at
            ) values (
              :id, :companyId, :usageType, :resourceType, :resourceId, :eventType,
              :usedDelta, :reservedDelta, :idempotencyKey, :createdAt
            )
            on conflict (idempotency_key) do nothing
            """,
            Map.of(
                "id", id,
                "companyId", companyId,
                "usageType", usageType.name(),
                "resourceType", resourceType,
                "resourceId", resourceId,
                "eventType", eventType.name(),
                "usedDelta", usedBytesDelta,
                "reservedDelta", reservedBytesDelta,
                "idempotencyKey", idempotencyKey,
                "createdAt", Timestamp.from(createdAt)));
    return updated == 1;
  }

  public void applyDelta(
      long companyId, StorageUsageType usageType, long usedBytesDelta, long reservedBytesDelta) {
    namedJdbcTemplate.update(
        """
        insert into company_storage_usage (
          company_id, usage_type, used_bytes, reserved_bytes, updated_at
        ) values (
          :companyId, :usageType, greatest(0, :usedDelta), greatest(0, :reservedDelta), now()
        )
        on conflict (company_id, usage_type) do update set
          used_bytes = greatest(0, company_storage_usage.used_bytes + :usedDelta),
          reserved_bytes = greatest(0, company_storage_usage.reserved_bytes + :reservedDelta),
          updated_at = now()
        """,
        Map.of(
            "companyId", companyId,
            "usageType", usageType.name(),
            "usedDelta", usedBytesDelta,
            "reservedDelta", reservedBytesDelta));
  }

  public UsageTotals totals(long companyId) {
    return jdbcTemplate.queryForObject(
        """
        select
          coalesce(sum(used_bytes), 0) as used_bytes,
          coalesce(sum(reserved_bytes), 0) as reserved_bytes
        from company_storage_usage
        where company_id = ?
        """,
        (rs, rowNum) -> new UsageTotals(rs.getLong("used_bytes"), rs.getLong("reserved_bytes")),
        companyId);
  }

  public BucketUsage bucket(long companyId, StorageUsageType usageType) {
    List<BucketUsage> rows =
        jdbcTemplate.query(
            """
            select used_bytes, reserved_bytes
            from company_storage_usage
            where company_id = ? and usage_type = ?
            """,
            (rs, rowNum) -> new BucketUsage(rs.getLong("used_bytes"), rs.getLong("reserved_bytes")),
            companyId,
            usageType.name());
    return rows.isEmpty() ? new BucketUsage(0, 0) : rows.getFirst();
  }

  public List<StorageUsageBreakdownResponse> breakdown(long companyId) {
    return jdbcTemplate.query(
        """
        select usage_type, used_bytes, reserved_bytes
        from company_storage_usage
        where company_id = ?
          and (used_bytes > 0 or reserved_bytes > 0)
        order by used_bytes + reserved_bytes desc, usage_type asc
        """,
        this::mapBreakdown,
        companyId);
  }

  public void replaceBucket(
      long companyId, StorageUsageType usageType, long usedBytes, long reservedBytes) {
    namedJdbcTemplate.update(
        """
        insert into company_storage_usage (
          company_id, usage_type, used_bytes, reserved_bytes, updated_at
        ) values (
          :companyId, :usageType, :usedBytes, :reservedBytes, now()
        )
        on conflict (company_id, usage_type) do update set
          used_bytes = excluded.used_bytes,
          reserved_bytes = excluded.reserved_bytes,
          updated_at = now()
        """,
        Map.of(
            "companyId", companyId,
            "usageType", usageType.name(),
            "usedBytes", Math.max(0, usedBytes),
            "reservedBytes", Math.max(0, reservedBytes)));
  }

  private StorageUsageBreakdownResponse mapBreakdown(ResultSet rs, int rowNum) throws SQLException {
    return new StorageUsageBreakdownResponse(
        StorageUsageType.valueOf(rs.getString("usage_type")),
        rs.getLong("used_bytes"),
        rs.getLong("reserved_bytes"));
  }

  public record StorageEventInsert(
      long id,
      long companyId,
      StorageUsageType usageType,
      String resourceType,
      String resourceId,
      StorageEventType eventType,
      long usedBytesDelta,
      long reservedBytesDelta,
      String idempotencyKey,
      Instant createdAt) {}

  public record UsageTotals(long usedBytes, long reservedBytes) {}

  public record BucketUsage(long usedBytes, long reservedBytes) {}
}
