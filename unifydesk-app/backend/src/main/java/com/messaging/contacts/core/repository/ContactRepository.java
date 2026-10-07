package com.messaging.contacts.core.repository;

import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.core.domain.ContactTypes.ContactSort;
import com.messaging.contacts.core.domain.ContactTypes.ContactStatus;
import com.messaging.contacts.core.domain.ContactTypes.IdentityStatus;
import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import com.messaging.contacts.core.domain.CoreRows.ContactRow;
import com.messaging.contacts.core.domain.CoreRows.ContactSummaryRow;
import com.messaging.contacts.core.domain.CoreRows.IdentityRow;
import com.messaging.contacts.core.service.IdentityNormalizer.NormalizedIdentity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContactRepository {

  private static final RowMapper<ContactRow> CONTACT_MAPPER =
      (rs, rowNum) -> mapContact(rs, "");
  private static final RowMapper<IdentityRow> IDENTITY_MAPPER =
      (rs, rowNum) -> mapIdentity(rs);

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;

  public ContactRepository(
      JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    this.namedJdbcTemplate = namedJdbcTemplate;
  }

  public long countActive(long companyId) {
    Long count =
        jdbcTemplate.queryForObject(
            "select count(*) from contacts where company_id = ? and status = 'ACTIVE'",
            Long.class,
            companyId);
    return count == null ? 0 : count;
  }

  public DashboardCounts dashboardCounts(long companyId) {
    return jdbcTemplate.queryForObject(
        """
        select
          count(*) as total_contacts,
          count(*) filter (where c.created_at >= now() - interval '30 days') as new_contacts_30d,
          count(*) filter (
            where coalesce((
              select p.state
              from contact_channel_preferences p
              where p.company_id = c.company_id
                and p.contact_id = c.id
                and p.channel = 'EMAIL'
                and p.purpose = 'MARKETING'
                and (
                  p.identity_id is null
                  or p.identity_id = (
                    select i.id
                    from contact_identities i
                    where i.company_id = c.company_id
                      and i.contact_id = c.id
                      and i.identity_type = 'EMAIL'
                      and i.status = 'ACTIVE'
                    order by i.is_primary desc, i.created_at, i.id
                    limit 1
                  )
                )
              order by (p.identity_id is not null) desc, p.updated_at desc, p.id desc
              limit 1
            ), 'UNKNOWN') = 'OPTED_IN'
          ) as email_subscribers,
          count(*) filter (
            where coalesce((
              select p.state
              from contact_channel_preferences p
              where p.company_id = c.company_id
                and p.contact_id = c.id
                and p.channel = 'SMS'
                and p.purpose = 'MARKETING'
                and (
                  p.identity_id is null
                  or p.identity_id = (
                    select i.id
                    from contact_identities i
                    where i.company_id = c.company_id
                      and i.contact_id = c.id
                      and i.identity_type = 'PHONE'
                      and i.status = 'ACTIVE'
                    order by i.is_primary desc, i.created_at, i.id
                    limit 1
                  )
                )
              order by (p.identity_id is not null) desc, p.updated_at desc, p.id desc
              limit 1
            ), 'UNKNOWN') = 'OPTED_IN'
          ) as sms_subscribers,
          count(*) filter (
            where coalesce((
              select p.state
              from contact_channel_preferences p
              where p.company_id = c.company_id
                and p.contact_id = c.id
                and p.channel = 'WHATSAPP'
                and p.purpose = 'MARKETING'
                and (
                  p.identity_id is null
                  or p.identity_id = (
                    select i.id
                    from contact_identities i
                    where i.company_id = c.company_id
                      and i.contact_id = c.id
                      and i.identity_type = 'PHONE'
                      and i.status = 'ACTIVE'
                    order by i.is_primary desc, i.created_at, i.id
                    limit 1
                  )
                )
              order by (p.identity_id is not null) desc, p.updated_at desc, p.id desc
              limit 1
            ), 'UNKNOWN') = 'OPTED_IN'
          ) as whatsapp_subscribers
        from contacts c
        where c.company_id = ?
          and c.status = 'ACTIVE'
        """,
        (rs, rowNum) ->
            new DashboardCounts(
                rs.getLong("total_contacts"),
                rs.getLong("new_contacts_30d"),
                rs.getLong("email_subscribers"),
                rs.getLong("sms_subscribers"),
                rs.getLong("whatsapp_subscribers")),
        companyId);
  }

  public List<DailyContactCount> dashboardGrowth(long companyId) {
    return jdbcTemplate.query(
        """
        select
          (c.created_at at time zone 'UTC')::date as day,
          count(*) as added
        from contacts c
        where c.company_id = ?
          and c.status = 'ACTIVE'
          and (c.created_at at time zone 'UTC') >=
              date_trunc('day', now() at time zone 'UTC') - interval '29 days'
        group by (c.created_at at time zone 'UTC')::date
        order by day
        """,
        (rs, rowNum) -> new DailyContactCount(rs.getObject("day", LocalDate.class), rs.getLong("added")),
        companyId);
  }

  public ContactRow insertContact(
      long companyId,
      long userId,
      String firstName,
      String lastName,
      String displayName,
      String organization,
      String jobTitle,
      String countryCode,
      String timezone,
      String language,
      String source,
      String notes,
      Long ownerUserId) {
    long id = IdGenerator.nextId();
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        insert into contacts
          (id, company_id, first_name, last_name, display_name, organization, job_title,
           country_code, timezone, language, source, notes, owner_user_id, status,
           created_by_user_id, created_at, updated_at, version)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, 0)
        """,
        id,
        companyId,
        firstName,
        lastName,
        displayName,
        organization,
        jobTitle,
        countryCode,
        timezone,
        language,
        source,
        notes,
        ownerUserId,
        userId,
        Timestamp.from(now),
        Timestamp.from(now));
    return require(companyId, id);
  }

  public Optional<ContactRow> find(long companyId, long contactId) {
    return jdbcTemplate
        .query(
            "select * from contacts where company_id = ? and id = ?",
            CONTACT_MAPPER,
            companyId,
            contactId)
        .stream()
        .findFirst();
  }

  public ContactRow require(long companyId, long contactId) {
    return find(companyId, contactId).orElseThrow();
  }

  public ContactRow lock(long companyId, long contactId) {
    return jdbcTemplate
        .query(
            "select * from contacts where company_id = ? and id = ? for update",
            CONTACT_MAPPER,
            companyId,
            contactId)
        .stream()
        .findFirst()
        .orElseThrow();
  }

  public List<ContactRow> lockInIdOrder(long companyId, long firstId, long secondId) {
    long lower = Math.min(firstId, secondId);
    long upper = Math.max(firstId, secondId);
    List<ContactRow> rows = new ArrayList<>(2);
    rows.add(lock(companyId, lower));
    rows.add(lock(companyId, upper));
    return rows;
  }

  public boolean allActiveOwned(long companyId, Collection<Long> contactIds) {
    if (contactIds.isEmpty()) {
      return true;
    }
    MapSqlParameterSource params =
        new MapSqlParameterSource("companyId", companyId).addValue("ids", contactIds);
    Long count =
        namedJdbcTemplate.queryForObject(
            """
            select count(*)
            from contacts
            where company_id = :companyId
              and status = 'ACTIVE'
              and id in (:ids)
            """,
            params,
            Long.class);
    return count != null && count == contactIds.size();
  }

  public IdentityRow insertIdentity(
      long companyId, long contactId, NormalizedIdentity identity, boolean primary) {
    long id = IdGenerator.nextId();
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        insert into contact_identities
          (id, company_id, contact_id, identity_type, namespace, value, normalized_value,
           is_primary, status, created_at, updated_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
        """,
        id,
        companyId,
        contactId,
        identity.type().name(),
        identity.namespace(),
        identity.displayValue(),
        identity.normalizedValue(),
        primary,
        Timestamp.from(now),
        Timestamp.from(now));
    return requireIdentity(companyId, contactId, id);
  }

  public void disableIdentity(long companyId, long contactId, long identityId) {
    int updated =
        jdbcTemplate.update(
            """
            update contact_identities
            set status = 'DISABLED',
                is_primary = false,
                deleted_at = now(),
                updated_at = now()
            where company_id = ?
              and contact_id = ?
              and id = ?
              and status = 'ACTIVE'
            """,
            companyId,
            contactId,
            identityId);
    if (updated == 0) {
      throw new java.util.NoSuchElementException();
    }
  }

  public IdentityRow setPrimary(long companyId, long contactId, long identityId) {
    lock(companyId, contactId);
    IdentityRow selected = requireIdentity(companyId, contactId, identityId);
    if (selected.status() != IdentityStatus.ACTIVE
        || selected.type() == IdentityType.EXTERNAL) {
      throw new IllegalArgumentException("Identity cannot be primary");
    }
    jdbcTemplate.update(
        """
        update contact_identities
        set is_primary = false, updated_at = now()
        where company_id = ?
          and contact_id = ?
          and identity_type = ?
          and status = 'ACTIVE'
          and is_primary = true
          and id <> ?
        """,
        companyId,
        contactId,
        selected.type().name(),
        identityId);
    jdbcTemplate.update(
        """
        update contact_identities
        set is_primary = true, updated_at = now()
        where company_id = ? and contact_id = ? and id = ? and status = 'ACTIVE'
        """,
        companyId,
        contactId,
        identityId);
    return requireIdentity(companyId, contactId, identityId);
  }

  public Optional<IdentityRow> findIdentity(long companyId, long contactId, long identityId) {
    return jdbcTemplate
        .query(
            "select * from contact_identities where company_id = ? and contact_id = ? and id = ?",
            IDENTITY_MAPPER,
            companyId,
            contactId,
            identityId)
        .stream()
        .findFirst();
  }

  public IdentityRow requireIdentity(long companyId, long contactId, long identityId) {
    return findIdentity(companyId, contactId, identityId).orElseThrow();
  }

  public List<IdentityRow> identities(long companyId, long contactId) {
    return jdbcTemplate.query(
        """
        select *
        from contact_identities
        where company_id = ? and contact_id = ?
        order by identity_type, is_primary desc, created_at, id
        """,
        IDENTITY_MAPPER,
        companyId,
        contactId);
  }

  public Map<IdentityKey, Long> findActiveIdentityOwners(
      long companyId, Collection<NormalizedIdentity> identities) {
    if (identities.isEmpty()) {
      return Map.of();
    }
    Set<String> values =
        identities.stream()
            .map(NormalizedIdentity::normalizedValue)
            .collect(java.util.stream.Collectors.toSet());
    MapSqlParameterSource params =
        new MapSqlParameterSource("companyId", companyId).addValue("values", values);
    Map<IdentityKey, Long> owners = new HashMap<>();
    namedJdbcTemplate.query(
        """
        select identity_type, namespace, normalized_value, contact_id
        from contact_identities
        where company_id = :companyId
          and status = 'ACTIVE'
          and normalized_value in (:values)
        """,
        params,
        rs -> {
          IdentityKey key =
              new IdentityKey(
                  IdentityType.valueOf(rs.getString("identity_type")),
                  rs.getString("namespace"),
                  rs.getString("normalized_value"));
          owners.put(key, rs.getLong("contact_id"));
        });
    return Map.copyOf(owners);
  }

  public List<ContactSummaryRow> search(SearchSpec spec) {
    StringBuilder sql =
        new StringBuilder(
            """
            select
              c.*,
              email.normalized_value as primary_email,
              phone.normalized_value as primary_phone,
              email_pref.state as email_marketing_preference,
              sms_pref.state as sms_marketing_preference,
              whatsapp_pref.state as whatsapp_marketing_preference
            from contacts c
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
            where c.company_id = :companyId
              and c.status = :status
            """);

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("companyId", spec.companyId())
            .addValue("status", spec.status().name())
            .addValue("limit", spec.limit());

    if (spec.queryPrefix() != null) {
      sql.append(
          """
            and (
              lower(coalesce(c.display_name, '')) like :prefix
              or lower(coalesce(c.first_name, '')) like :prefix
              or lower(coalesce(c.last_name, '')) like :prefix
              or lower(coalesce(c.organization, '')) like :prefix
              or exists (
                select 1
                from contact_identities si
                where si.company_id = c.company_id
                  and si.contact_id = c.id
                  and si.status = 'ACTIVE'
                  and si.normalized_value like :identityPrefix
              )
            )
          """);
      params.addValue("prefix", spec.queryPrefix() + "%");
      params.addValue("identityPrefix", spec.identityPrefix() + "%");
    }
    if (spec.tagId() != null) {
      sql.append(
          """
            and exists (
              select 1 from contact_tag_memberships tm
              join contact_tags t
                on t.id = tm.tag_id
               and t.company_id = tm.company_id
               and t.archived_at is null
              where tm.company_id = c.company_id
                and tm.contact_id = c.id
                and tm.tag_id = :tagId
            )
          """);
      params.addValue("tagId", spec.tagId());
    }
    if (spec.listId() != null) {
      sql.append(
          """
            and exists (
              select 1 from contact_list_memberships lm
              join contact_lists l
                on l.id = lm.list_id
               and l.company_id = lm.company_id
               and l.status = 'ACTIVE'
              where lm.company_id = c.company_id
                and lm.contact_id = c.id
                and lm.list_id = :listId
            )
          """);
      params.addValue("listId", spec.listId());
    }
    if (spec.createdAfter() != null) {
      sql.append(" and c.created_at >= :createdAfter");
      params.addValue("createdAfter", Timestamp.from(spec.createdAfter()));
    }
    if (spec.createdBefore() != null) {
      sql.append(" and c.created_at < :createdBefore");
      params.addValue("createdBefore", Timestamp.from(spec.createdBefore()));
    }

    appendKeyset(spec, sql, params);
    sql.append(orderBy(spec.sort())).append(" limit :limit");

    return namedJdbcTemplate.query(
        sql.toString(),
        params,
        (rs, rowNum) ->
            new ContactSummaryRow(
                mapContact(rs, ""),
                rs.getString("primary_email"),
                rs.getString("primary_phone"),
                preferenceState(rs.getString("email_marketing_preference")),
                preferenceState(rs.getString("sms_marketing_preference")),
                preferenceState(rs.getString("whatsapp_marketing_preference"))));
  }

  private com.messaging.contacts.consent.ConsentTypes.PreferenceState preferenceState(
      String value) {
    return value == null
        ? com.messaging.contacts.consent.ConsentTypes.PreferenceState.UNKNOWN
        : com.messaging.contacts.consent.ConsentTypes.PreferenceState.valueOf(value);
  }

  private void appendKeyset(
      SearchSpec spec, StringBuilder sql, MapSqlParameterSource params) {
    if (spec.afterId() == null) {
      return;
    }
    params.addValue("afterId", spec.afterId());
    switch (spec.sort()) {
      case UPDATED_DESC -> {
        sql.append(
            " and (c.updated_at < :afterInstant or (c.updated_at = :afterInstant and c.id < :afterId))");
        params.addValue("afterInstant", Timestamp.from(Instant.parse(spec.afterSortValue())));
      }
      case CREATED_DESC -> {
        sql.append(
            " and (c.created_at < :afterInstant or (c.created_at = :afterInstant and c.id < :afterId))");
        params.addValue("afterInstant", Timestamp.from(Instant.parse(spec.afterSortValue())));
      }
      case NAME_ASC -> {
        sql.append(
            """
             and (
               lower(coalesce(c.display_name, '')) > :afterName
               or (
                 lower(coalesce(c.display_name, '')) = :afterName
                 and c.id > :afterId
               )
             )
            """);
        params.addValue("afterName", spec.afterSortValue());
      }
    }
  }

  private String orderBy(ContactSort sort) {
    return switch (sort) {
      case UPDATED_DESC -> " order by c.updated_at desc, c.id desc";
      case CREATED_DESC -> " order by c.created_at desc, c.id desc";
      case NAME_ASC -> " order by lower(coalesce(c.display_name, '')) asc, c.id asc";
    };
  }

  public int patchContact(
      long companyId,
      long contactId,
      Map<String, Object> columns,
      long expectedVersion) {
    StringJoiner assignments = new StringJoiner(", ");
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("companyId", companyId)
            .addValue("contactId", contactId)
            .addValue("expectedVersion", expectedVersion);
    for (Map.Entry<String, Object> entry : columns.entrySet()) {
      assignments.add(entry.getKey() + " = :" + entry.getKey());
      params.addValue(entry.getKey(), entry.getValue());
    }
    assignments.add("updated_at = now()");
    assignments.add("version = version + 1");
    String sql =
        """
        update contacts
        set %s
        where company_id = :companyId
          and id = :contactId
          and status = 'ACTIVE'
          and version = :expectedVersion
        """
            .formatted(assignments);
    return namedJdbcTemplate.update(sql, params);
  }

  public int markDeleted(long companyId, long contactId, long expectedVersion) {
    return jdbcTemplate.update(
        """
        update contacts
        set status = 'DELETED',
            deleted_at = now(),
            updated_at = now(),
            version = version + 1
        where company_id = ?
          and id = ?
          and status = 'ACTIVE'
          and version = ?
        """,
        companyId,
        contactId,
        expectedVersion);
  }

  public void disableAllIdentities(long companyId, long contactId) {
    jdbcTemplate.update(
        """
        update contact_identities
        set status = 'DISABLED',
            is_primary = false,
            deleted_at = coalesce(deleted_at, now()),
            updated_at = now()
        where company_id = ? and contact_id = ? and status = 'ACTIVE'
        """,
        companyId,
        contactId);
  }

  public void deleteTagMemberships(long companyId, long contactId) {
    jdbcTemplate.update(
        "delete from contact_tag_memberships where company_id = ? and contact_id = ?",
        companyId,
        contactId);
  }

  public Map<Long, Integer> deleteListMemberships(long companyId, long contactId) {
    List<Long> listIds =
        jdbcTemplate.queryForList(
            """
            delete from contact_list_memberships
            where company_id = ? and contact_id = ?
            returning list_id
            """,
            Long.class,
            companyId,
            contactId);
    Map<Long, Integer> counts = new LinkedHashMap<>();
    listIds.forEach(id -> counts.merge(id, 1, Integer::sum));
    return counts;
  }

  public void purgeProfile(long companyId, long contactId) {
    jdbcTemplate.update(
        "delete from contact_custom_field_values where company_id = ? and contact_id = ?",
        companyId,
        contactId);
    jdbcTemplate.update(
        "delete from contact_identities where company_id = ? and contact_id = ?",
        companyId,
        contactId);
    jdbcTemplate.update(
        """
        update contacts
        set first_name = null,
            last_name = null,
            display_name = null,
            organization = null,
            job_title = null,
            country_code = null,
            timezone = null,
            language = null,
            source = null,
            notes = null,
            owner_user_id = null,
            last_activity_at = null,
            updated_at = now(),
            version = version + 1
        where company_id = ? and id = ? and status = 'DELETED'
        """,
        companyId,
        contactId);
  }

  public void moveIdentitiesForMerge(long companyId, long sourceId, long targetId) {
    Set<String> targetPrimaryTypes =
        Set.copyOf(
            jdbcTemplate.queryForList(
                """
                select identity_type
                from contact_identities
                where company_id = ?
                  and contact_id = ?
                  and status = 'ACTIVE'
                  and is_primary = true
                  and identity_type in ('EMAIL','PHONE')
                """,
                String.class,
                companyId,
                targetId));
    if (!targetPrimaryTypes.isEmpty()) {
      MapSqlParameterSource params =
          new MapSqlParameterSource()
              .addValue("companyId", companyId)
              .addValue("sourceId", sourceId)
              .addValue("types", targetPrimaryTypes);
      namedJdbcTemplate.update(
          """
          update contact_identities
          set is_primary = false, updated_at = now()
          where company_id = :companyId
            and contact_id = :sourceId
            and status = 'ACTIVE'
            and is_primary = true
            and identity_type in (:types)
          """,
          params);
    }
    jdbcTemplate.update(
        """
        update contact_identities
        set contact_id = ?, updated_at = now()
        where company_id = ? and contact_id = ?
        """,
        targetId,
        companyId,
        sourceId);
  }

  public void finalizeMerge(long companyId, long sourceId, long targetId) {
    jdbcTemplate.update(
        """
        update contacts
        set status = 'MERGED',
            merged_into_contact_id = ?,
            first_name = null,
            last_name = null,
            display_name = null,
            organization = null,
            job_title = null,
            country_code = null,
            timezone = null,
            language = null,
            source = null,
            notes = null,
            owner_user_id = null,
            last_activity_at = null,
            updated_at = now(),
            version = version + 1
        where company_id = ? and id = ? and status = 'ACTIVE'
        """,
        targetId,
        companyId,
        sourceId);
    jdbcTemplate.update(
        """
        update contacts
        set updated_at = now(), version = version + 1
        where company_id = ? and id = ? and status = 'ACTIVE'
        """,
        companyId,
        targetId);
  }

  public void appendMergeAudit(
      long companyId, long sourceId, long targetId, String strategyJson, long actorUserId) {
    jdbcTemplate.update(
        """
        insert into contact_merge_audit
          (id, company_id, source_contact_id, target_contact_id, strategy_json, actor_user_id, merged_at)
        values (?, ?, ?, ?, cast(? as jsonb), ?, now())
        """,
        IdGenerator.nextId(),
        companyId,
        sourceId,
        targetId,
        strategyJson,
        actorUserId);
  }

  public static IdentityKey key(NormalizedIdentity identity) {
    return new IdentityKey(identity.type(), identity.namespace(), identity.normalizedValue());
  }

  private static ContactRow mapContact(ResultSet rs, String prefix) throws SQLException {
    Timestamp lastActivity = rs.getTimestamp(prefix + "last_activity_at");
    Timestamp deleted = rs.getTimestamp(prefix + "deleted_at");
    Long owner = (Long) rs.getObject(prefix + "owner_user_id");
    Long mergedInto = (Long) rs.getObject(prefix + "merged_into_contact_id");
    return new ContactRow(
        rs.getLong(prefix + "id"),
        rs.getLong(prefix + "company_id"),
        rs.getString(prefix + "first_name"),
        rs.getString(prefix + "last_name"),
        rs.getString(prefix + "display_name"),
        rs.getString(prefix + "organization"),
        rs.getString(prefix + "job_title"),
        rs.getString(prefix + "country_code"),
        rs.getString(prefix + "timezone"),
        rs.getString(prefix + "language"),
        rs.getString(prefix + "source"),
        rs.getString(prefix + "notes"),
        owner,
        ContactStatus.valueOf(rs.getString(prefix + "status")),
        lastActivity == null ? null : lastActivity.toInstant(),
        mergedInto,
        rs.getLong(prefix + "created_by_user_id"),
        rs.getTimestamp(prefix + "created_at").toInstant(),
        rs.getTimestamp(prefix + "updated_at").toInstant(),
        deleted == null ? null : deleted.toInstant(),
        rs.getLong(prefix + "version"));
  }

  private static IdentityRow mapIdentity(ResultSet rs) throws SQLException {
    Timestamp deleted = rs.getTimestamp("deleted_at");
    return new IdentityRow(
        rs.getLong("id"),
        rs.getLong("company_id"),
        rs.getLong("contact_id"),
        IdentityType.valueOf(rs.getString("identity_type")),
        rs.getString("namespace"),
        rs.getString("value"),
        rs.getString("normalized_value"),
        rs.getBoolean("is_primary"),
        IdentityStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        deleted == null ? null : deleted.toInstant());
  }

  public record DashboardCounts(
      long totalContacts,
      long newContacts30d,
      long emailSubscribers,
      long smsSubscribers,
      long whatsappSubscribers) {}

  public record DailyContactCount(LocalDate day, long added) {}

  public record IdentityKey(IdentityType type, String namespace, String normalizedValue) {}

  public record SearchSpec(
      long companyId,
      ContactStatus status,
      ContactSort sort,
      String queryPrefix,
      String identityPrefix,
      Long tagId,
      Long listId,
      Instant createdAfter,
      Instant createdBefore,
      String afterSortValue,
      Long afterId,
      int limit) {}
}
