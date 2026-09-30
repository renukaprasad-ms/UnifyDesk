package com.messaging.contacts.consent;

import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.consent.ConsentTypes.*;
import com.messaging.contacts.consent.SuppressionFingerprintService.Fingerprint;
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
public class ContactSuppressionRepository {

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;

  @Transactional
  public SuppressionSnapshot add(SuppressionCommand command) {
    advisoryLock(
        command.companyId()
            + ":suppression:"
            + command.contactId()
            + ":"
            + command.identityId()
            + ":"
            + command.channel()
            + ":"
            + command.reasonCode()
            + ":"
            + command.source());

    Optional<SuppressionSnapshot> replay = byEventIdempotency(command.companyId(), command.idempotencyKey());
    if (replay.isPresent()) return replay.get();

    Optional<SuppressionSnapshot> current =
        jdbcTemplate
            .query(
                """
                select * from contact_suppressions
                where company_id = ? and contact_id = ?
                  and identity_id is not distinct from ?
                  and channel = ? and reason_code = ? and source = ?
                  and state = 'ACTIVE'
                order by id asc
                limit 1
                for update
                """,
                this::map,
                command.companyId(),
                command.contactId(),
                command.identityId(),
                command.channel().name(),
                command.reasonCode().name(),
                command.source().name())
            .stream()
            .findFirst();

    long suppressionId = current.map(SuppressionSnapshot::id).orElseGet(IdGenerator::nextId);
    Instant now = Instant.now();
    if (current.isEmpty()) {
      jdbcTemplate.update(
          """
          insert into contact_suppressions (
            id, company_id, contact_id, identity_id, identity_fingerprint, fingerprint_key_version,
            channel, reason_code, source, source_ref, state, suppressed_at, lifted_at,
            created_at, updated_at, version
          ) values (?, ?, ?, ?, null, null, ?, ?, ?, ?, 'ACTIVE', ?, null, ?, ?, 0)
          """,
          suppressionId,
          command.companyId(),
          command.contactId(),
          command.identityId(),
          command.channel().name(),
          command.reasonCode().name(),
          command.source().name(),
          command.sourceRef(),
          Timestamp.from(command.occurredAt()),
          Timestamp.from(now),
          Timestamp.from(now));
    }

    appendEvent(
        command.companyId(),
        suppressionId,
        command.contactId(),
        command.identityId(),
        command.channel(),
        command.reasonCode(),
        current.isPresent() ? SuppressionState.ACTIVE : null,
        SuppressionState.ACTIVE,
        command.source(),
        command.sourceRef(),
        command.actorType(),
        command.actorId(),
        command.reason(),
        command.occurredAt(),
        command.idempotencyKey());
    return require(command.companyId(), suppressionId);
  }

  @Transactional
  public SuppressionSnapshot lift(
      long companyId,
      long suppressionId,
      PreferenceSource source,
      String sourceRef,
      ActorType actorType,
      String actorId,
      String reason,
      Instant occurredAt,
      String idempotencyKey) {
    advisoryLock(companyId + ":suppression-id:" + suppressionId);
    Optional<SuppressionSnapshot> replay = byEventIdempotency(companyId, idempotencyKey);
    if (replay.isPresent()) return replay.get();

    SuppressionSnapshot current = requireForUpdate(companyId, suppressionId);
    if (current.state() == SuppressionState.LIFTED) return current;

    jdbcTemplate.update(
        """
        update contact_suppressions
        set state = 'LIFTED', lifted_at = ?, updated_at = ?, version = version + 1
        where company_id = ? and id = ? and state = 'ACTIVE'
        """,
        Timestamp.from(occurredAt),
        Timestamp.from(Instant.now()),
        companyId,
        suppressionId);

    appendEvent(
        companyId,
        suppressionId,
        current.contactId(),
        current.identityId(),
        current.channel(),
        current.reasonCode(),
        SuppressionState.ACTIVE,
        SuppressionState.LIFTED,
        source,
        sourceRef,
        actorType,
        actorId,
        reason,
        occurredAt,
        idempotencyKey);
    return require(companyId, suppressionId);
  }

  public List<SuppressionSnapshot> list(long companyId, long contactId) {
    return jdbcTemplate.query(
        """
        select * from contact_suppressions
        where company_id = ? and contact_id = ?
        order by suppressed_at desc, id desc
        """,
        this::map,
        companyId,
        contactId);
  }

  public boolean isSuppressed(
      long companyId,
      long contactId,
      Long identityId,
      Channel channel,
      Fingerprint fingerprint) {
    Long count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from contact_suppressions
            where company_id = ? and channel = ? and state = 'ACTIVE'
              and (
                (contact_id = ? and (identity_id is null or identity_id = ?))
                or (identity_fingerprint = ? and fingerprint_key_version = ?)
              )
            """,
            Long.class,
            companyId,
            channel.name(),
            contactId,
            identityId,
            fingerprint == null ? null : fingerprint.value(),
            fingerprint == null ? null : fingerprint.keyVersion());
    return count != null && count > 0;
  }

  public Set<Long> suppressedContacts(
      long companyId,
      Map<Long, Long> contactIdentityIds,
      Map<Long, Fingerprint> fingerprints,
      Channel channel) {
    if (contactIdentityIds.isEmpty()) return Set.of();
    List<Long> contacts = List.copyOf(contactIdentityIds.keySet());
    List<String> fingerprintValues =
        fingerprints.values().stream().map(Fingerprint::value).distinct().toList();
    Map<String, Object> params = new HashMap<>();
    params.put("companyId", companyId);
    params.put("channel", channel.name());
    params.put("contactIds", contacts);
    params.put("fingerprints", fingerprintValues.isEmpty() ? List.of("__none__") : fingerprintValues);

    List<SuppressionMatch> rows =
        namedJdbcTemplate.query(
            """
            select contact_id, identity_id, identity_fingerprint, fingerprint_key_version
            from contact_suppressions
            where company_id = :companyId and channel = :channel and state = 'ACTIVE'
              and (
                contact_id in (:contactIds)
                or identity_fingerprint in (:fingerprints)
              )
            """,
            params,
            (rs, rowNum) ->
                new SuppressionMatch(
                    (Long) rs.getObject("contact_id"),
                    (Long) rs.getObject("identity_id"),
                    rs.getString("identity_fingerprint"),
                    (Integer) rs.getObject("fingerprint_key_version")));

    Set<Long> suppressed = new HashSet<>();
    Map<String, Long> contactByFingerprint = new HashMap<>();
    for (var entry : fingerprints.entrySet()) {
      contactByFingerprint.put(entry.getValue().value() + ":" + entry.getValue().keyVersion(), entry.getKey());
    }
    for (SuppressionMatch row : rows) {
      if (row.contactId() != null) {
        Long selectedIdentity = contactIdentityIds.get(row.contactId());
        if (row.identityId() == null || Objects.equals(row.identityId(), selectedIdentity)) {
          suppressed.add(row.contactId());
        }
      }
      if (row.fingerprint() != null && row.keyVersion() != null) {
        Long contactId = contactByFingerprint.get(row.fingerprint() + ":" + row.keyVersion());
        if (contactId != null) suppressed.add(contactId);
      }
    }
    return suppressed;
  }

  @Transactional
  public void tombstoneIdentity(
      long companyId, long suppressionId, Fingerprint fingerprint) {
    jdbcTemplate.update(
        """
        update contact_suppressions
        set contact_id = null, identity_id = null, identity_fingerprint = ?,
            fingerprint_key_version = ?, updated_at = ?, version = version + 1
        where company_id = ? and id = ? and state = 'ACTIVE'
        """,
        fingerprint.value(),
        fingerprint.keyVersion(),
        Timestamp.from(Instant.now()),
        companyId,
        suppressionId);
  }

  @Transactional
  public void copyTombstone(
      long companyId, long sourceSuppressionId, Fingerprint fingerprint) {
    jdbcTemplate.update(
        """
        insert into contact_suppressions (
          id, company_id, contact_id, identity_id, identity_fingerprint,
          fingerprint_key_version, channel, reason_code, source, source_ref, state,
          suppressed_at, lifted_at, created_at, updated_at, version
        )
        select ?, company_id, null, null, ?, ?, channel, reason_code, source, source_ref,
               state, suppressed_at, lifted_at, created_at, ?, 0
        from contact_suppressions
        where company_id = ? and id = ? and state = 'ACTIVE'
        """,
        IdGenerator.nextId(),
        fingerprint.value(),
        fingerprint.keyVersion(),
        Timestamp.from(Instant.now()),
        companyId,
        sourceSuppressionId);
  }

  public SuppressionSnapshot require(long companyId, long id) {
    return jdbcTemplate
        .query(
            "select * from contact_suppressions where company_id = ? and id = ?",
            this::map,
            companyId,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND"));
  }

  private SuppressionSnapshot requireForUpdate(long companyId, long id) {
    return jdbcTemplate
        .query(
            "select * from contact_suppressions where company_id = ? and id = ? for update",
            this::map,
            companyId,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND"));
  }

  private Optional<SuppressionSnapshot> byEventIdempotency(long companyId, String idempotencyKey) {
    return jdbcTemplate
        .query(
            """
            select s.*
            from contact_suppression_events e
            join contact_suppressions s
              on s.company_id = e.company_id and s.id = e.suppression_id
            where e.company_id = ? and e.idempotency_key = ?
            """,
            this::map,
            companyId,
            idempotencyKey)
        .stream()
        .findFirst();
  }

  private void appendEvent(
      long companyId,
      long suppressionId,
      Long contactId,
      Long identityId,
      Channel channel,
      SuppressionReason reasonCode,
      SuppressionState previousState,
      SuppressionState newState,
      PreferenceSource source,
      String sourceRef,
      ActorType actorType,
      String actorId,
      String reason,
      Instant occurredAt,
      String idempotencyKey) {
    int inserted =
        jdbcTemplate.update(
            """
            insert into contact_suppression_events (
              id, company_id, suppression_id, contact_id, identity_id, channel, reason_code,
              previous_state, new_state, source, source_ref, actor_type, actor_id, reason,
              occurred_at, created_at, idempotency_key
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (company_id, idempotency_key) do nothing
            """,
            IdGenerator.nextId(),
            companyId,
            suppressionId,
            contactId,
            identityId,
            channel.name(),
            reasonCode.name(),
            previousState == null ? null : previousState.name(),
            newState.name(),
            source.name(),
            sourceRef,
            actorType.name(),
            actorId,
            reason,
            Timestamp.from(occurredAt),
            Timestamp.from(Instant.now()),
            idempotencyKey);
    if (inserted != 1) {
      throw new IllegalStateException("Suppression event replay race");
    }
  }

  private SuppressionSnapshot map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    Timestamp lifted = rs.getTimestamp("lifted_at");
    return new SuppressionSnapshot(
        rs.getLong("id"),
        (Long) rs.getObject("contact_id"),
        (Long) rs.getObject("identity_id"),
        rs.getString("identity_fingerprint"),
        (Integer) rs.getObject("fingerprint_key_version"),
        Channel.valueOf(rs.getString("channel")),
        SuppressionReason.valueOf(rs.getString("reason_code")),
        PreferenceSource.valueOf(rs.getString("source")),
        rs.getString("source_ref"),
        SuppressionState.valueOf(rs.getString("state")),
        rs.getTimestamp("suppressed_at").toInstant(),
        lifted == null ? null : lifted.toInstant(),
        rs.getLong("version"));
  }

  private void advisoryLock(String key) {
    jdbcTemplate.query(
        "select pg_advisory_xact_lock(hashtextextended(?, 0))",
        rs -> null,
        key);
  }

  private record SuppressionMatch(
      Long contactId, Long identityId, String fingerprint, Integer keyVersion) {}
}
