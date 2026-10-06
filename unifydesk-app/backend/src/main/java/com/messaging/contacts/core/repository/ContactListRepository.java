package com.messaging.contacts.core.repository;

import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.consent.ConsentTypes.PreferenceState;
import com.messaging.contacts.core.domain.ContactTypes.ContactListStatus;
import com.messaging.contacts.core.domain.ContactTypes.ContactStatus;
import com.messaging.contacts.core.domain.ContactTypes.MembershipSource;
import com.messaging.contacts.core.domain.CoreRows.ContactListRow;
import com.messaging.contacts.core.domain.CoreRows.ContactRow;
import com.messaging.contacts.core.domain.CoreRows.ContactSummaryRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContactListRepository {

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;

  public ContactListRepository(
      JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    this.namedJdbcTemplate = namedJdbcTemplate;
  }

  public long countActive(long companyId) {
    Long count =
        jdbcTemplate.queryForObject(
            "select count(*) from contact_lists where company_id = ? and status = 'ACTIVE'",
            Long.class,
            companyId);
    return count == null ? 0 : count;
  }

  public ContactListRow insert(
      long companyId, long userId, String name, String normalizedName, String description) {
    long id = IdGenerator.nextId();
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        insert into contact_lists
          (id, company_id, name, normalized_name, description, status, member_count,
           created_by_user_id, created_at, updated_at, version)
        values (?, ?, ?, ?, ?, 'ACTIVE', 0, ?, ?, ?, 0)
        """,
        id,
        companyId,
        name,
        normalizedName,
        description,
        userId,
        Timestamp.from(now),
        Timestamp.from(now));
    return require(companyId, id);
  }

  public Optional<ContactListRow> find(long companyId, long listId) {
    return jdbcTemplate
        .query(
            "select * from contact_lists where company_id = ? and id = ?",
            (rs, rowNum) -> mapList(rs),
            companyId,
            listId)
        .stream()
        .findFirst();
  }

  public ContactListRow require(long companyId, long listId) {
    return find(companyId, listId).orElseThrow();
  }

  public ContactListRow lockActive(long companyId, long listId) {
    return jdbcTemplate
        .query(
            """
            select *
            from contact_lists
            where company_id = ? and id = ? and status = 'ACTIVE'
            for update
            """,
            (rs, rowNum) -> mapList(rs),
            companyId,
            listId)
        .stream()
        .findFirst()
        .orElseThrow();
  }

  public List<ContactListRow> list(long companyId) {
    return jdbcTemplate.query(
        """
        select *
        from contact_lists
        where company_id = ? and status = 'ACTIVE'
        order by updated_at desc, id desc
        """,
        (rs, rowNum) -> mapList(rs),
        companyId);
  }

  public List<ContactListRow> listsForContact(long companyId, long contactId) {
    return jdbcTemplate.query(
        """
        select l.*
        from contact_list_memberships m
        join contact_lists l
          on l.company_id = m.company_id
         and l.id = m.list_id
        where m.company_id = ?
          and m.contact_id = ?
          and l.status = 'ACTIVE'
        order by l.normalized_name, l.id
        """,
        (rs, rowNum) -> mapList(rs),
        companyId,
        contactId);
  }

  public void update(
      long companyId, long listId, String name, String normalizedName, String description) {
    int changed =
        jdbcTemplate.update(
            """
            update contact_lists
            set name = ?,
                normalized_name = ?,
                description = ?,
                updated_at = now(),
                version = version + 1
            where company_id = ? and id = ? and status = 'ACTIVE'
            """,
            name,
            normalizedName,
            description,
            companyId,
            listId);
    if (changed == 0) {
      throw new java.util.NoSuchElementException();
    }
  }

  public void archive(long companyId, long listId) {
    int changed =
        jdbcTemplate.update(
            """
            update contact_lists
            set status = 'ARCHIVED',
                archived_at = now(),
                updated_at = now(),
                version = version + 1
            where company_id = ? and id = ? and status = 'ACTIVE'
            """,
            companyId,
            listId);
    if (changed == 0 && find(companyId, listId).isEmpty()) {
      throw new java.util.NoSuchElementException();
    }
  }

  public int addMembers(
      long companyId,
      long listId,
      Collection<Long> contactIds,
      MembershipSource source,
      String sourceRef,
      Long userId) {
    if (contactIds.isEmpty()) {
      return 0;
    }
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("companyId", companyId)
            .addValue("listId", listId)
            .addValue("contactIds", contactIds)
            .addValue("source", source.name())
            .addValue("sourceRef", sourceRef)
            .addValue("userId", userId);
    List<Long> inserted =
        namedJdbcTemplate.queryForList(
            """
            insert into contact_list_memberships
              (company_id, list_id, contact_id, source, source_ref, added_by_user_id, added_at)
            select :companyId, :listId, c.id, :source, :sourceRef, :userId, now()
            from contacts c
            where c.company_id = :companyId
              and c.status = 'ACTIVE'
              and c.id in (:contactIds)
            on conflict (list_id, contact_id) do nothing
            returning contact_id
            """,
            params,
            Long.class);
    if (!inserted.isEmpty()) {
      jdbcTemplate.update(
          """
          update contact_lists
          set member_count = member_count + ?,
              updated_at = now(),
              version = version + 1
          where company_id = ? and id = ? and status = 'ACTIVE'
          """,
          inserted.size(),
          companyId,
          listId);
    }
    return inserted.size();
  }

  public boolean removeMember(long companyId, long listId, long contactId) {
    int deleted =
        jdbcTemplate.update(
            """
            delete from contact_list_memberships
            where company_id = ? and list_id = ? and contact_id = ?
            """,
            companyId,
            listId,
            contactId);
    if (deleted > 0) {
      jdbcTemplate.update(
          """
          update contact_lists
          set member_count = greatest(0, member_count - 1),
              updated_at = now(),
              version = version + 1
          where company_id = ? and id = ?
          """,
          companyId,
          listId);
    }
    return deleted > 0;
  }

  public int removeMembers(
      long companyId, long listId, Collection<Long> contactIds) {
    if (contactIds.isEmpty()) return 0;
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("companyId", companyId)
            .addValue("listId", listId)
            .addValue("contactIds", contactIds);
    int deleted =
        namedJdbcTemplate.update(
            """
            delete from contact_list_memberships
            where company_id = :companyId
              and list_id = :listId
              and contact_id in (:contactIds)
            """,
            params);
    if (deleted > 0) {
      jdbcTemplate.update(
          """
          update contact_lists
          set member_count = greatest(0, member_count - ?),
              updated_at = now(),
              version = version + 1
          where company_id = ? and id = ?
          """,
          deleted,
          companyId,
          listId);
    }
    return deleted;
  }


  public List<ContactSummaryRow> members(
      long companyId, long listId, Long afterContactId, int limit) {
    String after = afterContactId == null ? "" : " and c.id > :afterContactId";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("companyId", companyId)
            .addValue("listId", listId)
            .addValue("afterContactId", afterContactId == null ? 0L : afterContactId)
            .addValue("limit", limit);
    return namedJdbcTemplate.query(
        """
        select c.*,
               email.normalized_value as primary_email,
               phone.normalized_value as primary_phone,
               email_pref.state as email_marketing_preference,
               sms_pref.state as sms_marketing_preference,
               whatsapp_pref.state as whatsapp_marketing_preference
        from contact_list_memberships lm
        join contacts c
          on c.company_id = lm.company_id
         and c.id = lm.contact_id
         and c.status = 'ACTIVE'
        left join lateral (
          select i.id, i.normalized_value
          from contact_identities i
          where i.company_id = c.company_id
            and i.contact_id = c.id
            and i.identity_type = 'EMAIL'
            and i.status = 'ACTIVE'
          order by i.is_primary desc, i.created_at, i.id
          limit 1
        ) email on true
        left join lateral (
          select i.id, i.normalized_value
          from contact_identities i
          where i.company_id = c.company_id
            and i.contact_id = c.id
            and i.identity_type = 'PHONE'
            and i.status = 'ACTIVE'
          order by i.is_primary desc, i.created_at, i.id
          limit 1
        ) phone on true
        left join lateral (
          select p.state
          from contact_channel_preferences p
          where p.company_id = c.company_id
            and p.contact_id = c.id
            and p.channel = 'EMAIL'
            and p.purpose = 'MARKETING'
            and (p.identity_id is null or p.identity_id = email.id)
          order by (p.identity_id is not null) desc, p.updated_at desc, p.id desc
          limit 1
        ) email_pref on true
        left join lateral (
          select p.state
          from contact_channel_preferences p
          where p.company_id = c.company_id
            and p.contact_id = c.id
            and p.channel = 'SMS'
            and p.purpose = 'MARKETING'
            and (p.identity_id is null or p.identity_id = phone.id)
          order by (p.identity_id is not null) desc, p.updated_at desc, p.id desc
          limit 1
        ) sms_pref on true
        left join lateral (
          select p.state
          from contact_channel_preferences p
          where p.company_id = c.company_id
            and p.contact_id = c.id
            and p.channel = 'WHATSAPP'
            and p.purpose = 'MARKETING'
            and (p.identity_id is null or p.identity_id = phone.id)
          order by (p.identity_id is not null) desc, p.updated_at desc, p.id desc
          limit 1
        ) whatsapp_pref on true
        where lm.company_id = :companyId
          and lm.list_id = :listId
        """
            + after
            + " order by c.id asc limit :limit",
        params,
        (rs, rowNum) -> {
          ContactRow contact =
              new ContactRow(
                  rs.getLong("id"),
                  rs.getLong("company_id"),
                  rs.getString("first_name"),
                  rs.getString("last_name"),
                  rs.getString("display_name"),
                  rs.getString("organization"),
                  rs.getString("job_title"),
                  rs.getString("country_code"),
                  rs.getString("timezone"),
                  rs.getString("language"),
                  rs.getString("source"),
                  rs.getString("notes"),
                  (Long) rs.getObject("owner_user_id"),
                  ContactStatus.valueOf(rs.getString("status")),
                  rs.getTimestamp("last_activity_at") == null
                      ? null
                      : rs.getTimestamp("last_activity_at").toInstant(),
                  (Long) rs.getObject("merged_into_contact_id"),
                  rs.getLong("created_by_user_id"),
                  rs.getTimestamp("created_at").toInstant(),
                  rs.getTimestamp("updated_at").toInstant(),
                  rs.getTimestamp("deleted_at") == null
                      ? null
                      : rs.getTimestamp("deleted_at").toInstant(),
                  rs.getLong("version"));
          return new ContactSummaryRow(
              contact,
              rs.getString("primary_email"),
              rs.getString("primary_phone"),
              preferenceState(rs.getString("email_marketing_preference")),
              preferenceState(rs.getString("sms_marketing_preference")),
              preferenceState(rs.getString("whatsapp_marketing_preference")));
        });
  }

  private PreferenceState preferenceState(String value) {
    return value == null ? PreferenceState.UNKNOWN : PreferenceState.valueOf(value);
  }

  public long reconcileMemberCount(long companyId, long listId) {
    lockActive(companyId, listId);
    Long actual =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from contact_list_memberships lm
            join contacts c
              on c.company_id = lm.company_id
             and c.id = lm.contact_id
             and c.status = 'ACTIVE'
            where lm.company_id = ? and lm.list_id = ?
            """,
            Long.class,
            companyId,
            listId);
    long count = actual == null ? 0 : actual;
    jdbcTemplate.update(
        """
        update contact_lists
        set member_count = ?, updated_at = now(), version = version + 1
        where company_id = ? and id = ?
        """,
        count,
        companyId,
        listId);
    return count;
  }

  public void decrementCounts(long companyId, Map<Long, Integer> removals) {
    removals.forEach(
        (listId, count) ->
            jdbcTemplate.update(
                """
                update contact_lists
                set member_count = greatest(0, member_count - ?),
                    updated_at = now(),
                    version = version + 1
                where company_id = ? and id = ?
                """,
                count,
                companyId,
                listId));
  }

  public void mergeMemberships(long companyId, long sourceId, long targetId) {
    List<Long> overlaps =
        jdbcTemplate.queryForList(
            """
            select s.list_id
            from contact_list_memberships s
            join contact_list_memberships t
              on t.company_id = s.company_id
             and t.list_id = s.list_id
             and t.contact_id = ?
            where s.company_id = ? and s.contact_id = ?
            """,
            Long.class,
            targetId,
            companyId,
            sourceId);

    jdbcTemplate.update(
        """
        insert into contact_list_memberships
          (company_id, list_id, contact_id, source, source_ref, added_by_user_id, added_at)
        select company_id, list_id, ?, source, source_ref, added_by_user_id, added_at
        from contact_list_memberships
        where company_id = ? and contact_id = ?
        on conflict (list_id, contact_id) do nothing
        """,
        targetId,
        companyId,
        sourceId);
    jdbcTemplate.update(
        "delete from contact_list_memberships where company_id = ? and contact_id = ?",
        companyId,
        sourceId);

    Map<Long, Integer> decrements = new LinkedHashMap<>();
    overlaps.forEach(id -> decrements.merge(id, 1, Integer::sum));
    decrementCounts(companyId, decrements);
  }

  private ContactListRow mapList(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new ContactListRow(
        rs.getLong("id"),
        rs.getLong("company_id"),
        rs.getString("name"),
        rs.getString("normalized_name"),
        rs.getString("description"),
        ContactListStatus.valueOf(rs.getString("status")),
        rs.getLong("member_count"),
        rs.getLong("created_by_user_id"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        rs.getTimestamp("archived_at") == null
            ? null
            : rs.getTimestamp("archived_at").toInstant(),
        rs.getLong("version"));
  }
}
