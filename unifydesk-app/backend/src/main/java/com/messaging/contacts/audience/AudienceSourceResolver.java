package com.messaging.contacts.audience;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AudienceSourceResolver {

  private final JdbcTemplate jdbcTemplate;
  private final SegmentRepository segmentRepository;
  private final SegmentCompiler segmentCompiler;
  private final ObjectMapper objectMapper;

  public SourceDescriptor describe(long companyId, SourceType type, long sourceId) {
    if (type == SourceType.LIST) {
      return jdbcTemplate
          .query(
              """
              select id, version from contact_lists
              where company_id = ? and id = ? and status = 'ACTIVE'
              """,
              (rs, rowNum) ->
                  new SourceDescriptor(
                      type, rs.getLong("id"), Long.toString(rs.getLong("version")), null),
              companyId,
              sourceId)
          .stream()
          .findFirst()
          .orElseThrow(() -> notFound());
    }
    SegmentRepository.SegmentRow segment = segmentRepository.require(companyId, sourceId);
    return new SourceDescriptor(
        type, sourceId, Long.toString(segment.version()), segment.filterHash());
  }

  public long count(long companyId, SourceDescriptor source) {
    if (source.type() == SourceType.LIST) {
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
      return value == null ? 0 : value;
    }
    SegmentRepository.SegmentRow segment = segmentRepository.require(companyId, source.id());
    requireFrozenSegment(source, segment);
    try {
      SegmentFilter filter = objectMapper.readValue(segment.filterJson(), SegmentFilter.class);
      return segmentRepository.count(segmentCompiler.compile(companyId, filter));
    } catch (Exception exception) {
      if (exception instanceof RuntimeException runtime) throw runtime;
      throw new IllegalStateException("Stored segment filter is invalid", exception);
    }
  }

  public List<Long> page(
      long companyId, SourceDescriptor source, long afterContactId, int limit) {
    if (source.type() == SourceType.LIST) {
      return jdbcTemplate.queryForList(
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
          limit);
    }
    SegmentRepository.SegmentRow segment = segmentRepository.require(companyId, source.id());
    requireFrozenSegment(source, segment);
    try {
      SegmentFilter filter = objectMapper.readValue(segment.filterJson(), SegmentFilter.class);
      return segmentRepository.page(segmentCompiler.compile(companyId, filter), afterContactId, limit);
    } catch (Exception exception) {
      if (exception instanceof RuntimeException runtime) throw runtime;
      throw new IllegalStateException("Stored segment filter is invalid", exception);
    }
  }

  private void requireFrozenSegment(
      SourceDescriptor source, SegmentRepository.SegmentRow segment) {
    if (!segment.filterHash().equals(source.filterHash())
        || !Long.toString(segment.version()).equals(source.sourceVersion())) {
      throw new IllegalStateException("Audience source changed after snapshot freeze");
    }
  }

  private com.messaging.common.exception.NotFoundException notFound() {
    return new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND");
  }

  public enum SourceType {
    LIST,
    SEGMENT
  }

  public record SourceDescriptor(
      SourceType type, long id, String sourceVersion, String filterHash) {}
}
