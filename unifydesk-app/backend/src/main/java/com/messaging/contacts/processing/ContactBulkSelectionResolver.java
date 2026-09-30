package com.messaging.contacts.processing;

import com.messaging.common.exception.BadRequestException;
import com.messaging.contacts.audience.AudienceSourceResolver;
import com.messaging.contacts.processing.ContactBulkService.ContactFilter;
import com.messaging.contacts.processing.ContactBulkService.Selection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactBulkSelectionResolver {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final AudienceSourceResolver audienceSourceResolver;

  public List<Long> page(long companyId, Selection selection, long afterId, int limit) {
    return switch (selection.type()) {
      case IDS -> explicitIds(companyId, selection.ids(), afterId, limit);
      case LIST ->
          audienceSourceResolver.page(
              companyId,
              audienceSourceResolver.describe(
                  companyId,
                  AudienceSourceResolver.SourceType.LIST,
                  requireSourceId(selection)),
              afterId,
              limit);
      case SEGMENT ->
          audienceSourceResolver.page(
              companyId,
              audienceSourceResolver.describe(
                  companyId,
                  AudienceSourceResolver.SourceType.SEGMENT,
                  requireSourceId(selection)),
              afterId,
              limit);
      case FILTER -> filterPage(companyId, selection.filter(), afterId, limit);
    };
  }

  private List<Long> explicitIds(
      long companyId, List<Long> ids, long afterId, int limit) {
    if (ids == null || ids.isEmpty() || ids.size() > 500) {
      throw invalid();
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
        select id
        from contacts
        where company_id = :companyId
          and status = 'ACTIVE'
          and id in (:ids)
        order by id asc
        """,
        Map.of("companyId", companyId, "ids", candidates),
        Long.class);
  }

  private List<Long> filterPage(
      long companyId, ContactFilter filter, long afterId, int limit) {
    if (filter == null) throw invalid();
    Map<String, Object> params = new HashMap<>();
    params.put("companyId", companyId);
    params.put("afterId", afterId);
    params.put("limit", limit);
    List<String> predicates = new ArrayList<>();
    predicates.add("c.company_id = :companyId");
    predicates.add("c.status = 'ACTIVE'");
    predicates.add("c.id > :afterId");

    if (filter.status() != null
        && !filter.status().isBlank()
        && !"ACTIVE".equals(filter.status().toUpperCase(Locale.ROOT))) {
      throw invalid();
    }
    if (filter.q() != null && !filter.q().isBlank()) {
      String q = filter.q().trim();
      if (q.length() > 160) throw invalid();
      params.put("q", q.toLowerCase(Locale.ROOT) + "%");
      predicates.add(
          """
          (
            lower(c.display_name) like :q
            or exists (
              select 1
              from contact_identities ci
              where ci.company_id = c.company_id
                and ci.contact_id = c.id
                and ci.status = 'ACTIVE'
                and lower(ci.normalized_value) like :q
            )
          )
          """);
    }
    if (filter.tagId() != null) {
      params.put("tagId", filter.tagId());
      predicates.add(
          """
          exists (
            select 1
            from contact_tag_memberships ctm
            where ctm.company_id = c.company_id
              and ctm.contact_id = c.id
              and ctm.tag_id = :tagId
          )
          """);
    }
    if (filter.listId() != null) {
      params.put("listId", filter.listId());
      predicates.add(
          """
          exists (
            select 1
            from contact_list_memberships clm
            where clm.company_id = c.company_id
              and clm.contact_id = c.id
              and clm.list_id = :listId
          )
          """);
    }
    if (filter.createdAfter() != null) {
      params.put("createdAfter", Timestamp.from(filter.createdAfter()));
      predicates.add("c.created_at >= :createdAfter");
    }
    if (filter.createdBefore() != null) {
      params.put("createdBefore", Timestamp.from(filter.createdBefore()));
      predicates.add("c.created_at < :createdBefore");
    }
    if (filter.sort() != null
        && !filter.sort().isBlank()
        && !List.of("UPDATED_DESC", "CREATED_DESC", "NAME_ASC").contains(filter.sort())) {
      throw invalid();
    }

    return jdbcTemplate.queryForList(
        "select c.id from contacts c where "
            + String.join(" and ", predicates)
            + " order by c.id asc limit :limit",
        params,
        Long.class);
  }

  private long requireSourceId(Selection selection) {
    if (selection.sourceId() == null) throw invalid();
    return selection.sourceId();
  }

  private BadRequestException invalid() {
    return new BadRequestException("CONTACT_BULK_INVALID");
  }
}
