package com.messaging.contacts.audience;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.id.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactAudienceSelectionService {

  public static final int MAX_CONTACT_SELECTION = 500;

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;

  @Transactional
  public Selection create(long companyId, long userId, List<Long> contactIds) {
    PreviewSelection preview = validate(companyId, contactIds);
    long id = IdGenerator.nextId();
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        insert into contact_audience_selections
          (id,company_id,selection_hash,candidate_count,created_by_user_id,created_at)
        values (?,?,?,?,?,?)
        """,
        id,
        companyId,
        preview.selectionHash(),
        preview.contactIds().size(),
        userId,
        Timestamp.from(now));

    List<Object[]> rows = new ArrayList<>(preview.contactIds().size());
    int sequence = 0;
    for (Long contactId : preview.contactIds()) {
      rows.add(new Object[] {id, companyId, contactId, ++sequence, Timestamp.from(now)});
    }
    jdbcTemplate.batchUpdate(
        """
        insert into contact_audience_selection_members
          (selection_id,company_id,contact_id,sequence_id,created_at)
        values (?,?,?,?,?)
        """,
        rows);
    return new Selection(id, preview.selectionHash(), preview.contactIds().size());
  }

  public PreviewSelection validate(long companyId, List<Long> contactIds) {
    if (contactIds == null
        || contactIds.isEmpty()
        || contactIds.size() > MAX_CONTACT_SELECTION
        || contactIds.stream().anyMatch(id -> id == null || id <= 0)) {
      throw new BadRequestException("CONTACT_SELECTION_INVALID");
    }

    LinkedHashSet<Long> unique = new LinkedHashSet<>(contactIds);
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("companyId", companyId)
            .addValue("contactIds", unique);
    List<Long> owned =
        namedJdbcTemplate.queryForList(
            """
            select id
            from contacts
            where company_id=:companyId
              and status='ACTIVE'
              and id in (:contactIds)
            """,
            params,
            Long.class);
    if (owned.size() != unique.size()) {
      throw new NotFoundException("CONTACT_NOT_FOUND");
    }

    Set<Long> ownedSet = Set.copyOf(owned);
    List<Long> ordered = unique.stream().filter(ownedSet::contains).toList();
    return new PreviewSelection(
        ordered,
        contactIds.size() - ordered.size(),
        hash(ordered));
  }

  private String hash(List<Long> ids) {
    try {
      List<Long> sorted = new ArrayList<>(ids);
      sorted.sort(Long::compareTo);
      String canonical =
          sorted.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  public record Selection(long id, String selectionHash, int candidateCount) {}

  public record PreviewSelection(
      List<Long> contactIds, long duplicateCount, String selectionHash) {}
}
