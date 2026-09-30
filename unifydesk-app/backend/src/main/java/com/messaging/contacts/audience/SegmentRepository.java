package com.messaging.contacts.audience;

import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.audience.SegmentCompiler.CompiledSegment;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class SegmentRepository {

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;

  @Transactional
  public SegmentRow create(
      long companyId,
      long userId,
      String name,
      String description,
      int filterVersion,
      String filterJson,
      String filterHash) {
    long id = IdGenerator.nextId();
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        insert into contact_segments (
          id, company_id, name, normalized_name, description, filter_version, filter_json,
          filter_hash, status, created_by_user_id, created_at, updated_at, version
        ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'ACTIVE', ?, ?, ?, 0)
        """,
        id,
        companyId,
        name.trim(),
        normalizeName(name),
        description,
        filterVersion,
        filterJson,
        filterHash,
        userId,
        Timestamp.from(now),
        Timestamp.from(now));
    return require(companyId, id);
  }

  public List<SegmentRow> list(long companyId) {
    return jdbcTemplate.query(
        """
        select * from contact_segments
        where company_id = ? and status = 'ACTIVE'
        order by updated_at desc, id desc
        limit 200
        """,
        this::map,
        companyId);
  }

  public Optional<SegmentRow> find(long companyId, long segmentId) {
    return jdbcTemplate
        .query(
            "select * from contact_segments where company_id = ? and id = ?",
            this::map,
            companyId,
            segmentId)
        .stream()
        .findFirst();
  }

  public SegmentRow require(long companyId, long segmentId) {
    return find(companyId, segmentId)
        .orElseThrow(() -> new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND"));
  }

  public long countActive(long companyId) {
    Long value =
        jdbcTemplate.queryForObject(
            "select count(*) from contact_segments where company_id = ? and status = 'ACTIVE'",
            Long.class,
            companyId);
    return value == null ? 0 : value;
  }

  @Transactional
  public boolean update(
      long companyId,
      long id,
      long expectedVersion,
      String name,
      String description,
      int filterVersion,
      String filterJson,
      String filterHash) {
    int updated =
        jdbcTemplate.update(
            """
            update contact_segments
            set name = ?, normalized_name = ?, description = ?, filter_version = ?,
                filter_json = ?::jsonb, filter_hash = ?, cached_count = null,
                count_computed_at = null, updated_at = ?, version = version + 1
            where company_id = ? and id = ? and status = 'ACTIVE' and version = ?
            """,
            name.trim(),
            normalizeName(name),
            description,
            filterVersion,
            filterJson,
            filterHash,
            Timestamp.from(Instant.now()),
            companyId,
            id,
            expectedVersion);
    return updated == 1;
  }

  @Transactional
  public boolean archive(long companyId, long id) {
    return jdbcTemplate.update(
            """
            update contact_segments
            set status = 'ARCHIVED', updated_at = ?, version = version + 1
            where company_id = ? and id = ? and status = 'ACTIVE'
            """,
            Timestamp.from(Instant.now()),
            companyId,
            id)
        == 1;
  }

  public List<Long> page(CompiledSegment compiled, long afterId, int limit) {
    Map<String, Object> params = new LinkedHashMap<>(compiled.parameters());
    params.put("afterId", afterId);
    params.put("limit", limit);
    return namedJdbcTemplate.queryForList(
        "select c.id from contacts c where "
            + compiled.whereSql()
            + " and c.id > :afterId order by c.id asc limit :limit",
        params,
        Long.class);
  }

  public long count(CompiledSegment compiled) {
    Long value =
        namedJdbcTemplate.queryForObject(
            "select count(*) from contacts c where " + compiled.whereSql(),
            compiled.parameters(),
            Long.class);
    return value == null ? 0 : value;
  }

  @Transactional
  public void updateCount(long companyId, long segmentId, String filterHash, long count) {
    jdbcTemplate.update(
        """
        update contact_segments
        set cached_count = ?, count_computed_at = ?, updated_at = ?
        where company_id = ? and id = ? and status = 'ACTIVE' and filter_hash = ?
        """,
        count,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        companyId,
        segmentId,
        filterHash);
  }

  private SegmentRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    return new SegmentRow(
        rs.getLong("id"),
        rs.getLong("company_id"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getInt("filter_version"),
        rs.getString("filter_json"),
        rs.getString("filter_hash"),
        rs.getString("status"),
        (Long) rs.getObject("cached_count"),
        timestamp(rs, "count_computed_at"),
        rs.getLong("created_by_user_id"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        rs.getLong("version"));
  }

  private Instant timestamp(java.sql.ResultSet rs, String name) throws java.sql.SQLException {
    Timestamp value = rs.getTimestamp(name);
    return value == null ? null : value.toInstant();
  }

  private String normalizeName(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  public record SegmentRow(
      long id,
      long companyId,
      String name,
      String description,
      int filterVersion,
      String filterJson,
      String filterHash,
      String status,
      Long cachedCount,
      Instant countComputedAt,
      long createdByUserId,
      Instant createdAt,
      Instant updatedAt,
      long version) {}
}
