package com.messaging.contacts.processing;

import com.messaging.contacts.audience.*;
import com.messaging.contacts.processing.ContactBulkService.Selection;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactBulkSelectionResolver {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final AudienceSourceResolver audienceSourceResolver;
  private final SegmentCompiler segmentCompiler;
  private final SegmentRepository segmentRepository;

  public List<Long> page(long companyId, Selection selection, long afterId, int limit) {
    return switch (selection.type()) {
      case IDS -> explicitIds(companyId, selection.ids(), afterId, limit);
      case LIST ->
          audienceSourceResolver.page(
              companyId,
              audienceSourceResolver.describe(
                  companyId, AudienceSourceResolver.SourceType.LIST, requireSourceId(selection)),
              afterId,
              limit);
      case SEGMENT ->
          audienceSourceResolver.page(
              companyId,
              audienceSourceResolver.describe(
                  companyId, AudienceSourceResolver.SourceType.SEGMENT, requireSourceId(selection)),
              afterId,
              limit);
      case FILTER -> segmentRepository.page(segmentCompiler.compile(companyId, selection.filter()), afterId, limit);
    };
  }

  private List<Long> explicitIds(
      long companyId, List<Long> ids, long afterId, int limit) {
    if (ids == null || ids.isEmpty() || ids.size() > 500) {
      throw new com.messaging.common.exception.BadRequestException("CONTACT_BULK_INVALID");
    }
    List<Long> candidates =
        ids.stream()
            .filter(Objects::nonNull)
            .filter(id -> id > afterId)
            .distinct()
            .sorted()
            .limit(limit)
            .toList();
    if (candidates.isEmpty()) return List.of();
    return jdbcTemplate.queryForList(
        """
        select id from contacts
        where company_id = :companyId and status = 'ACTIVE' and id in (:ids)
        order by id asc
        """,
        Map.of("companyId", companyId, "ids", candidates),
        Long.class);
  }

  private long requireSourceId(Selection selection) {
    if (selection.sourceId() == null)
      throw new com.messaging.common.exception.BadRequestException("CONTACT_BULK_INVALID");
    return selection.sourceId();
  }
}
