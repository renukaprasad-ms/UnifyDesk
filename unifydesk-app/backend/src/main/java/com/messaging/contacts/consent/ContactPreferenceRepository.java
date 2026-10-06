package com.messaging.contacts.consent;

import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.consent.ConsentTypes.*;
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
public class ContactPreferenceRepository {

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;

  @Transactional
  public PreferenceSnapshot apply(PreferenceCommand command) {
    advisoryLock(
        command.companyId()
            + ":"
            + command.contactId()
            + ":"
            + command.identityId()
            + ":"
            + command.channel()
            + ":"
            + command.purpose());

    Optional<PreferenceSnapshot> current =
        findCurrent(
            command.companyId(),
            command.contactId(),
            command.identityId(),
            command.channel(),
            command.purpose(),
            true);

    long eventId = IdGenerator.nextId();
    int inserted =
        jdbcTemplate.update(
            """
            insert into contact_preference_events (
              id, company_id, contact_id, identity_id, channel, purpose, previous_state,
              new_state, source, source_ref, actor_type, actor_id, reason, occurred_at,
              created_at, idempotency_key
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (company_id, idempotency_key) do nothing
            """,
            eventId,
            command.companyId(),
            command.contactId(),
            command.identityId(),
            command.channel().name(),
            command.purpose().name(),
            current.map(value -> value.state().name()).orElse(null),
            command.state().name(),
            command.source().name(),
            command.sourceRef(),
            command.actorType().name(),
            command.actorId(),
            command.reason(),
            Timestamp.from(command.occurredAt()),
            Timestamp.from(Instant.now()),
            command.idempotencyKey());

    if (inserted == 0) {
      return findCurrent(
              command.companyId(),
              command.contactId(),
              command.identityId(),
              command.channel(),
              command.purpose(),
              false)
          .orElseThrow(() -> new IllegalStateException("Preference replay without projection"));
    }

    Instant now = Instant.now();
    if (current.isPresent()
        && ContactPreferenceProjectionPolicy.shouldReplace(current.get(), command)) {
      jdbcTemplate.update(
          """
          update contact_channel_preferences
          set state = ?, source = ?, source_ref = ?, occurred_at = ?, updated_at = ?,
              version = version + 1
          where id = ? and company_id = ?
          """,
          command.state().name(),
          command.source().name(),
          command.sourceRef(),
          Timestamp.from(command.occurredAt()),
          Timestamp.from(now),
          current.get().id(),
          command.companyId());
    } else if (current.isEmpty()) {
      jdbcTemplate.update(
          """
          insert into contact_channel_preferences (
            id, company_id, contact_id, identity_id, channel, purpose, state, source,
            source_ref, occurred_at, updated_at, version
          ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
          """,
          IdGenerator.nextId(),
          command.companyId(),
          command.contactId(),
          command.identityId(),
          command.channel().name(),
          command.purpose().name(),
          command.state().name(),
          command.source().name(),
          command.sourceRef(),
          Timestamp.from(command.occurredAt()),
          Timestamp.from(now));
    }
    return findCurrent(
            command.companyId(),
            command.contactId(),
            command.identityId(),
            command.channel(),
            command.purpose(),
            false)
        .orElseThrow();
  }

  public List<PreferenceSnapshot> list(long companyId, long contactId) {
    return jdbcTemplate.query(
        """
        select * from contact_channel_preferences
        where company_id = ? and contact_id = ?
        order by channel, purpose, identity_id nulls first
        """,
        this::map,
        companyId,
        contactId);
  }

  public PreferenceState effectiveState(
      long companyId, long contactId, Long identityId, Channel channel, Purpose purpose) {
    List<PreferenceSnapshot> rows =
        jdbcTemplate.query(
            """
            select * from contact_channel_preferences
            where company_id = ? and contact_id = ? and channel = ? and purpose = ?
              and (identity_id is null or identity_id = ?)
            order by (identity_id is not null) desc
            limit 1
            """,
            this::map,
            companyId,
            contactId,
            channel.name(),
            purpose.name(),
            identityId);
    return rows.isEmpty() ? PreferenceState.UNKNOWN : rows.getFirst().state();
  }

  public Map<Long, PreferenceState> effectiveStates(
      long companyId,
      Map<Long, Long> contactIdentityIds,
      Channel channel,
      Purpose purpose) {
    if (contactIdentityIds.isEmpty()) return Map.of();
    List<Long> contactIds = List.copyOf(contactIdentityIds.keySet());
    List<PreferenceSnapshot> rows =
        namedJdbcTemplate.query(
            """
            select * from contact_channel_preferences
            where company_id = :companyId
              and contact_id in (:contactIds)
              and channel = :channel
              and purpose = :purpose
            """,
            Map.of(
                "companyId", companyId,
                "contactIds", contactIds,
                "channel", channel.name(),
                "purpose", purpose.name()),
            this::map);
    Map<Long, PreferenceState> result = new HashMap<>();
    Map<Long, PreferenceState> global = new HashMap<>();
    for (PreferenceSnapshot row : rows) {
      if (row.identityId() == null) {
        global.put(row.contactId(), row.state());
      } else if (Objects.equals(row.identityId(), contactIdentityIds.get(row.contactId()))) {
        result.put(row.contactId(), row.state());
      }
    }
    for (Long contactId : contactIds) {
      result.putIfAbsent(contactId, global.getOrDefault(contactId, PreferenceState.UNKNOWN));
    }
    return result;
  }

  private Optional<PreferenceSnapshot> findCurrent(
      long companyId,
      long contactId,
      Long identityId,
      Channel channel,
      Purpose purpose,
      boolean forUpdate) {
    String sql =
        """
        select * from contact_channel_preferences
        where company_id = ? and contact_id = ?
          and identity_id is not distinct from ?
          and channel = ? and purpose = ?
        """
            + (forUpdate ? " for update" : "");
    return jdbcTemplate
        .query(sql, this::map, companyId, contactId, identityId, channel.name(), purpose.name())
        .stream()
        .findFirst();
  }

  private PreferenceSnapshot map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    return new PreferenceSnapshot(
        rs.getLong("id"),
        rs.getLong("contact_id"),
        (Long) rs.getObject("identity_id"),
        Channel.valueOf(rs.getString("channel")),
        Purpose.valueOf(rs.getString("purpose")),
        PreferenceState.valueOf(rs.getString("state")),
        PreferenceSource.valueOf(rs.getString("source")),
        rs.getString("source_ref"),
        rs.getTimestamp("occurred_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        rs.getLong("version"));
  }

  private void advisoryLock(String key) {
    jdbcTemplate.query(
        "select pg_advisory_xact_lock(hashtextextended(?, 0))",
        rs -> null,
        key);
  }
}
