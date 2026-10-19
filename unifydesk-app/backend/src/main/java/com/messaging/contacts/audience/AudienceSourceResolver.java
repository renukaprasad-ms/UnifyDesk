package com.messaging.contacts.audience;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AudienceSourceResolver {

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;
  private final SegmentRepository segmentRepository;
  private final SegmentCompiler segmentCompiler;
  private final ObjectMapper objectMapper;

  public SourceDescriptor describe(long companyId, SourceType type, long sourceId) {
    return describeForBroadcast(companyId, type, sourceId).source();
  }

  public BroadcastSourceState describeForBroadcast(long companyId, SourceType type, long sourceId) {
    return switch (type) {
      case LIST ->
          jdbcTemplate
              .query(
                  """
                  select id, version from contact_lists
                  where company_id = ? and id = ? and status = 'ACTIVE'
                  """,
                  (rs, rowNum) ->
                      new BroadcastSourceState(
                          new SourceDescriptor(
                              type, rs.getLong("id"), Long.toString(rs.getLong("version")), null),
                          null,
                          null),
                  companyId,
                  sourceId)
              .stream()
              .findFirst()
              .orElseThrow(this::notFound);
      case SEGMENT -> {
        SegmentRepository.SegmentRow segment = segmentRepository.require(companyId, sourceId);
        yield new BroadcastSourceState(
            new SourceDescriptor(
                type, sourceId, Long.toString(segment.version()), segment.filterHash()),
            segment.filterVersion(),
            null);
      }
      case CONTACTS ->
          jdbcTemplate
              .query(
                  """
                  select id, selection_hash
                  from contact_audience_selections
                  where company_id = ? and id = ?
                  """,
                  (rs, rowNum) -> {
                    String selectionHash = rs.getString("selection_hash");
                    return new BroadcastSourceState(
                        new SourceDescriptor(type, rs.getLong("id"), selectionHash, selectionHash),
                        null,
                        selectionHash);
                  },
                  companyId,
                  sourceId)
              .stream()
              .findFirst()
              .orElseThrow(this::notFound);
    };
  }

  public long count(long companyId, SourceDescriptor source) {
    return switch (source.type()) {
      case LIST -> {
        requireFrozenList(companyId, source);
        Long value =
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from contact_list_memberships clm
                join contacts c
                  on c.company_id = clm.company_id and c.id = clm.contact_id
                where clm.company_id = ? and clm.list_id = ? and c.status = 'ACTIVE'
                """,
                Long.class,
                companyId,
                source.id());
        yield value == null ? 0 : value;
      }
      case SEGMENT -> {
        SegmentRepository.SegmentRow segment = segmentRepository.require(companyId, source.id());
        requireFrozenSegment(source, segment);
        yield segmentRepository.count(compile(companyId, segment));
      }
      case CONTACTS -> {
        requireFrozenContacts(companyId, source);
        Long value =
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from contact_audience_selection_members m
                join contacts c
                  on c.company_id = m.company_id and c.id = m.contact_id
                where m.company_id = ? and m.selection_id = ? and c.status = 'ACTIVE'
                """,
                Long.class,
                companyId,
                source.id());
        yield value == null ? 0 : value;
      }
    };
  }

  public List<Long> page(long companyId, SourceDescriptor source, long afterContactId, int limit) {
    int bounded = Math.max(1, Math.min(limit, 2000));
    return switch (source.type()) {
      case LIST -> {
        requireFrozenList(companyId, source);
        yield jdbcTemplate.queryForList(
            """
            select c.id
            from contact_list_memberships clm
            join contacts c
              on c.company_id = clm.company_id and c.id = clm.contact_id
            where clm.company_id = ? and clm.list_id = ?
              and c.status = 'ACTIVE' and c.id > ?
            order by c.id asc
            limit ?
            """,
            Long.class,
            companyId,
            source.id(),
            afterContactId,
            bounded);
      }
      case SEGMENT -> {
        SegmentRepository.SegmentRow segment = segmentRepository.require(companyId, source.id());
        requireFrozenSegment(source, segment);
        yield segmentRepository.page(compile(companyId, segment), afterContactId, bounded);
      }
      case CONTACTS -> {
        requireFrozenContacts(companyId, source);
        yield jdbcTemplate.queryForList(
            """
            select c.id
            from contact_audience_selection_members m
            join contacts c
              on c.company_id = m.company_id and c.id = m.contact_id
            where m.company_id = ? and m.selection_id = ?
              and c.status = 'ACTIVE' and c.id > ?
            order by c.id asc
            limit ?
            """,
            Long.class,
            companyId,
            source.id(),
            afterContactId,
            bounded);
      }
    };
  }

  /**
   * Captures immutable candidate IDs in one database operation while holding a source-row lock.
   * Eligibility is intentionally deferred to bounded snapshot batches.
   */
  public long captureCandidates(long companyId, SourceDescriptor source, long snapshotId) {
    return captureCandidates(companyId, source, null, snapshotId);
  }

  public long captureCandidates(long companyId, BroadcastSourceState sourceState, long snapshotId) {
    return captureCandidates(
        companyId, sourceState.source(), sourceState.filterVersion(), snapshotId);
  }

  private long captureCandidates(
      long companyId,
      SourceDescriptor source,
      Integer expectedSegmentFilterVersion,
      long snapshotId) {
    switch (source.type()) {
      case LIST -> {
        lockList(companyId, source);
        jdbcTemplate.update(
            """
            insert into contact_audience_snapshot_candidates
              (snapshot_id, company_id, contact_id, created_at)
            select ?, c.company_id, c.id, now()
            from contact_list_memberships clm
            join contacts c
              on c.company_id = clm.company_id and c.id = clm.contact_id
            where clm.company_id = ? and clm.list_id = ? and c.status = 'ACTIVE'
            on conflict do nothing
            """,
            snapshotId,
            companyId,
            source.id());
      }
      case SEGMENT -> {
        SegmentRepository.SegmentRow segment =
            lockSegment(companyId, source, expectedSegmentFilterVersion);
        SegmentCompiler.CompiledSegment compiled = compile(companyId, segment);
        MapSqlParameterSource params = new MapSqlParameterSource();
        new LinkedHashMap<>(compiled.parameters()).forEach(params::addValue);
        params.addValue("snapshotId", snapshotId);
        namedJdbcTemplate.update(
            """
            insert into contact_audience_snapshot_candidates
              (snapshot_id, company_id, contact_id, created_at)
            select :snapshotId, c.company_id, c.id, now()
            from contacts c
            where
            """
                + compiled.whereSql()
                + " on conflict do nothing",
            params);
      }
      case CONTACTS -> {
        lockContacts(companyId, source);
        jdbcTemplate.update(
            """
            insert into contact_audience_snapshot_candidates
              (snapshot_id, company_id, contact_id, created_at)
            select ?, c.company_id, c.id, now()
            from contact_audience_selection_members m
            join contacts c
              on c.company_id = m.company_id and c.id = m.contact_id
            where m.company_id = ? and m.selection_id = ? and c.status = 'ACTIVE'
            on conflict do nothing
            """,
            snapshotId,
            companyId,
            source.id());
      }
    }
    Long count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from contact_audience_snapshot_candidates
            where company_id = ? and snapshot_id = ?
            """,
            Long.class,
            companyId,
            snapshotId);
    return count == null ? 0 : count;
  }

  private SegmentCompiler.CompiledSegment compile(
      long companyId, SegmentRepository.SegmentRow segment) {
    try {
      SegmentFilter filter = objectMapper.readValue(segment.filterJson(), SegmentFilter.class);
      return segmentCompiler.compile(companyId, filter);
    } catch (Exception exception) {
      if (exception instanceof RuntimeException runtime) throw runtime;
      throw new IllegalStateException("Stored segment filter is invalid", exception);
    }
  }

  private void lockList(long companyId, SourceDescriptor source) {
    Long version =
        jdbcTemplate
            .query(
                """
                select version
                from contact_lists
                where company_id = ? and id = ? and status = 'ACTIVE'
                for share
                """,
                (rs, rowNum) -> rs.getLong("version"),
                companyId,
                source.id())
            .stream()
            .findFirst()
            .orElseThrow(this::notFound);
    if (!Long.toString(version).equals(source.sourceVersion())) changed();
  }

  private SegmentRepository.SegmentRow lockSegment(
      long companyId, SourceDescriptor source, Integer expectedFilterVersion) {
    jdbcTemplate
        .query(
            """
            select id
            from contact_segments
            where company_id = ? and id = ? and status = 'ACTIVE'
            for share
            """,
            (rs, rowNum) -> rs.getLong("id"),
            companyId,
            source.id())
        .stream()
        .findFirst()
        .orElseThrow(this::notFound);
    SegmentRepository.SegmentRow segment = segmentRepository.require(companyId, source.id());
    requireFrozenSegment(source, segment);
    if (expectedFilterVersion != null && segment.filterVersion() != expectedFilterVersion) {
      changed();
    }
    return segment;
  }

  private void lockContacts(long companyId, SourceDescriptor source) {
    String hash =
        jdbcTemplate
            .query(
                """
                select selection_hash
                from contact_audience_selections
                where company_id = ? and id = ?
                for share
                """,
                (rs, rowNum) -> rs.getString("selection_hash"),
                companyId,
                source.id())
            .stream()
            .findFirst()
            .orElseThrow(this::notFound);
    if (!hash.equals(source.sourceVersion())) changed();
  }

  private void requireFrozenList(long companyId, SourceDescriptor source) {
    Long version =
        jdbcTemplate
            .query(
                """
                select version
                from contact_lists
                where company_id = ? and id = ? and status = 'ACTIVE'
                """,
                (rs, rowNum) -> rs.getLong("version"),
                companyId,
                source.id())
            .stream()
            .findFirst()
            .orElseThrow(this::notFound);
    if (!Long.toString(version).equals(source.sourceVersion())) changed();
  }

  private void requireFrozenSegment(SourceDescriptor source, SegmentRepository.SegmentRow segment) {
    if (!segment.filterHash().equals(source.filterHash())
        || !Long.toString(segment.version()).equals(source.sourceVersion())) {
      changed();
    }
  }

  private void requireFrozenContacts(long companyId, SourceDescriptor source) {
    String hash =
        jdbcTemplate
            .query(
                """
                select selection_hash
                from contact_audience_selections
                where company_id = ? and id = ?
                """,
                (rs, rowNum) -> rs.getString("selection_hash"),
                companyId,
                source.id())
            .stream()
            .findFirst()
            .orElseThrow(this::notFound);
    if (!hash.equals(source.sourceVersion())) changed();
  }

  private void changed() {
    throw new SourceChangedException();
  }

  private com.messaging.common.exception.NotFoundException notFound() {
    return new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND");
  }

  public enum SourceType {
    LIST,
    SEGMENT,
    CONTACTS
  }

  public record SourceDescriptor(
      SourceType type, long id, String sourceVersion, String filterHash) {}

  public record BroadcastSourceState(
      SourceDescriptor source, Integer filterVersion, String contactSelectionHash) {}

  public static final class SourceChangedException extends IllegalStateException {
    public SourceChangedException() {
      super("Audience source changed after snapshot freeze");
    }
  }
}
