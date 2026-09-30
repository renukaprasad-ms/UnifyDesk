package com.messaging.contacts.core.repository;

import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.core.domain.CoreRows.TagRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TagRepository {

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;

  public TagRepository(
      JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    this.namedJdbcTemplate = namedJdbcTemplate;
  }

  public TagRow insert(
      long companyId, String name, String normalizedName, String color, String metadataJson) {
    long id = IdGenerator.nextId();
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        insert into contact_tags
          (id, company_id, name, normalized_name, color, metadata_json, created_at, updated_at)
        values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)
        """,
        id,
        companyId,
        name,
        normalizedName,
        color,
        metadataJson,
        Timestamp.from(now),
        Timestamp.from(now));
    return require(companyId, id);
  }

  public Optional<TagRow> find(long companyId, long tagId) {
    return jdbcTemplate
        .query(
            "select * from contact_tags where company_id = ? and id = ?",
            (rs, rowNum) ->
                new TagRow(
                    rs.getLong("id"),
                    rs.getLong("company_id"),
                    rs.getString("name"),
                    rs.getString("normalized_name"),
                    rs.getString("color"),
                    rs.getString("metadata_json"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    rs.getTimestamp("archived_at") == null
                        ? null
                        : rs.getTimestamp("archived_at").toInstant()),
            companyId,
            tagId)
        .stream()
        .findFirst();
  }

  public TagRow require(long companyId, long tagId) {
    return find(companyId, tagId).orElseThrow();
  }

  public List<TagRow> list(long companyId) {
    return jdbcTemplate.query(
        """
        select *
        from contact_tags
        where company_id = ? and archived_at is null
        order by normalized_name, id
        """,
        (rs, rowNum) ->
            new TagRow(
                rs.getLong("id"),
                rs.getLong("company_id"),
                rs.getString("name"),
                rs.getString("normalized_name"),
                rs.getString("color"),
                rs.getString("metadata_json"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                null),
        companyId);
  }

  public void update(
      long companyId,
      long tagId,
      String name,
      String normalizedName,
      String color,
      String metadataJson) {
    int changed =
        jdbcTemplate.update(
            """
            update contact_tags
            set name = ?, normalized_name = ?, color = ?, metadata_json = cast(? as jsonb), updated_at = now()
            where company_id = ? and id = ? and archived_at is null
            """,
            name,
            normalizedName,
            color,
            metadataJson,
            companyId,
            tagId);
    if (changed == 0) {
      throw new java.util.NoSuchElementException();
    }
  }

  public void archive(long companyId, long tagId) {
    int changed =
        jdbcTemplate.update(
            """
            update contact_tags
            set archived_at = now(), updated_at = now()
            where company_id = ? and id = ? and archived_at is null
            """,
            companyId,
            tagId);
    if (changed == 0 && find(companyId, tagId).isEmpty()) {
      throw new java.util.NoSuchElementException();
    }
  }

  public boolean allActiveOwned(long companyId, Collection<Long> tagIds) {
    if (tagIds.isEmpty()) {
      return true;
    }
    MapSqlParameterSource params =
        new MapSqlParameterSource("companyId", companyId).addValue("tagIds", tagIds);
    Long count =
        namedJdbcTemplate.queryForObject(
            """
            select count(*)
            from contact_tags
            where company_id = :companyId
              and archived_at is null
              and id in (:tagIds)
            """,
            params,
            Long.class);
    return count != null && count == tagIds.size();
  }

  public void addMemberships(
      long companyId, long contactId, Collection<Long> tagIds, long userId) {
    if (tagIds.isEmpty()) {
      return;
    }
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("companyId", companyId)
            .addValue("contactId", contactId)
            .addValue("tagIds", tagIds)
            .addValue("userId", userId);
    namedJdbcTemplate.update(
        """
        insert into contact_tag_memberships
          (company_id, tag_id, contact_id, assigned_by_user_id, assigned_at)
        select :companyId, t.id, :contactId, :userId, now()
        from contact_tags t
        where t.company_id = :companyId
          and t.archived_at is null
          and t.id in (:tagIds)
        on conflict (tag_id, contact_id) do nothing
        """,
        params);
  }

  public void addMembership(long companyId, long contactId, long tagId, long userId) {
    jdbcTemplate.update(
        """
        insert into contact_tag_memberships
          (company_id, tag_id, contact_id, assigned_by_user_id, assigned_at)
        values (?, ?, ?, ?, now())
        on conflict (tag_id, contact_id) do nothing
        """,
        companyId,
        tagId,
        contactId,
        userId);
  }

  public void removeMembership(long companyId, long contactId, long tagId) {
    jdbcTemplate.update(
        "delete from contact_tag_memberships where company_id = ? and contact_id = ? and tag_id = ?",
        companyId,
        contactId,
        tagId);
  }

  public List<TagRow> tagsForContact(long companyId, long contactId) {
    return jdbcTemplate.query(
        """
        select t.*
        from contact_tags t
        join contact_tag_memberships m
          on m.company_id = t.company_id
         and m.tag_id = t.id
        where m.company_id = ?
          and m.contact_id = ?
          and t.archived_at is null
        order by t.normalized_name, t.id
        """,
        (rs, rowNum) ->
            new TagRow(
                rs.getLong("id"),
                rs.getLong("company_id"),
                rs.getString("name"),
                rs.getString("normalized_name"),
                rs.getString("color"),
                rs.getString("metadata_json"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                null),
        companyId,
        contactId);
  }

  public void mergeMemberships(long companyId, long sourceId, long targetId) {
    jdbcTemplate.update(
        """
        insert into contact_tag_memberships
          (company_id, tag_id, contact_id, assigned_by_user_id, assigned_at)
        select company_id, tag_id, ?, assigned_by_user_id, assigned_at
        from contact_tag_memberships
        where company_id = ? and contact_id = ?
        on conflict (tag_id, contact_id) do nothing
        """,
        targetId,
        companyId,
        sourceId);
    jdbcTemplate.update(
        "delete from contact_tag_memberships where company_id = ? and contact_id = ?",
        companyId,
        sourceId);
  }
}
