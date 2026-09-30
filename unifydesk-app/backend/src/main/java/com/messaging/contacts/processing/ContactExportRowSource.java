package com.messaging.contacts.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.contacts.audience.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactExportRowSource {

  private static final Set<String> ALLOWED_FIELDS =
      Set.of("id", "displayName", "firstName", "lastName", "organization", "jobTitle", "countryCode", "language");

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;
  private final SegmentRepository segmentRepository;
  private final SegmentCompiler segmentCompiler;
  private final ObjectMapper objectMapper;

  public List<ExportRow> page(
      long companyId,
      Source source,
      List<String> fields,
      long afterContactId,
      int limit) {
    validateFields(fields);
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("companyId", companyId);
    params.put("afterId", afterContactId);
    params.put("limit", limit);
    String sourcePredicate =
        switch (source.type()) {
          case CONTACTS -> "";
          case LIST -> {
            params.put("sourceId", requireId(source));
            yield " and exists (select 1 from contact_list_memberships clm where clm.company_id = c.company_id and clm.contact_id = c.id and clm.list_id = :sourceId)";
          }
          case SEGMENT -> {
            long segmentId = requireId(source);
            SegmentRepository.SegmentRow segment = segmentRepository.require(companyId, segmentId);
            SegmentFilter filter;
            try {
              filter = objectMapper.readValue(segment.filterJson(), SegmentFilter.class);
            } catch (Exception exception) {
              throw new IllegalStateException("Stored segment filter is invalid", exception);
            }
            SegmentCompiler.CompiledSegment compiled = segmentCompiler.compile(companyId, filter);
            params.putAll(compiled.parameters());
            yield " and (" + compiled.whereSql() + ")";
          }
        };
    return namedJdbcTemplate.query(
        """
        select c.id, c.display_name, c.first_name, c.last_name, c.organization,
               c.job_title, c.country_code, c.language
        from contacts c
        where c.company_id = :companyId and c.status = 'ACTIVE'
          and c.id > :afterId
        """
            + sourcePredicate
            + " order by c.id asc limit :limit",
        params,
        (rs, rowNum) ->
            new ExportRow(
                rs.getLong("id"),
                Map.of(
                    "id", Long.toString(rs.getLong("id")),
                    "displayName", nullToEmpty(rs.getString("display_name")),
                    "firstName", nullToEmpty(rs.getString("first_name")),
                    "lastName", nullToEmpty(rs.getString("last_name")),
                    "organization", nullToEmpty(rs.getString("organization")),
                    "jobTitle", nullToEmpty(rs.getString("job_title")),
                    "countryCode", nullToEmpty(rs.getString("country_code")),
                    "language", nullToEmpty(rs.getString("language")))));
  }

  public void validateSource(long companyId, Source source) {
    if (source.type() == SourceType.LIST) {
      Long count =
          jdbcTemplate.queryForObject(
              "select count(*) from contact_lists where company_id = ? and id = ? and status = 'ACTIVE'",
              Long.class,
              companyId,
              requireId(source));
      if (count == null || count == 0) throw notFound();
    } else if (source.type() == SourceType.SEGMENT) {
      segmentRepository.require(companyId, requireId(source));
    }
  }

  public void validateFields(List<String> fields) {
    if (fields == null || fields.isEmpty() || fields.size() > ALLOWED_FIELDS.size()) {
      throw new com.messaging.common.exception.BadRequestException("CONTACT_FIELD_INVALID");
    }
    for (String field : fields) if (!ALLOWED_FIELDS.contains(field)) throw new com.messaging.common.exception.BadRequestException("CONTACT_FIELD_INVALID");
  }

  private long requireId(Source source) {
    if (source.id() == null) throw new com.messaging.common.exception.BadRequestException("CONTACT_FIELD_INVALID");
    return source.id();
  }

  private com.messaging.common.exception.NotFoundException notFound() {
    return new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND");
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  public enum SourceType {
    CONTACTS,
    LIST,
    SEGMENT
  }

  public record Source(SourceType type, Long id) {}

  public record ExportRow(long contactId, Map<String, String> values) {}
}
