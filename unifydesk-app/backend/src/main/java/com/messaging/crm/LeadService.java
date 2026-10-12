package com.messaging.crm;

import com.messaging.common.exception.ApiException;
import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.core.domain.ContactTypes.ContactStatus;
import com.messaging.contacts.core.repository.ContactRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lead lifecycle projection over canonical Contacts. This service never changes a Contact's
 * identities, communication preferences or marketing eligibility.
 */
@Service
@RequiredArgsConstructor
public class LeadService {
  private final JdbcTemplate jdbc;
  private final ContactRepository contacts;

  public enum Stage {
    NEW, CONTACTED, QUALIFIED, UNQUALIFIED, CONVERTED
  }

  public record Lead(
      String contactId,
      String displayName,
      String organization,
      String ownerUserId,
      Stage stage,
      long version,
      Instant createdAt,
      Instant updatedAt) {}

  public record LeadPage(List<Lead> items, String nextCursor, boolean hasMore) {}

  public record LeadSummary(long total, long newLeads, long qualified, long converted) {}

  public record StageEvent(
      String id, Stage previousStage, Stage newStage, String actorUserId, Instant occurredAt) {}

  @Transactional
  public Lead enroll(long companyId, long contactId, long actorUserId) {
    requireActiveContact(companyId, contactId);
    try {
      jdbc.update(
          """
          insert into crm_leads
            (company_id, contact_id, stage, created_by_user_id, updated_by_user_id)
          values (?, ?, 'NEW', ?, ?)
          """,
          companyId, contactId, actorUserId, actorUserId);
    } catch (DuplicateKeyException e) {
      throw new ApiException(HttpStatus.CONFLICT, "CRM_LEAD_ALREADY_EXISTS");
    }
    appendEvent(companyId, contactId, null, Stage.NEW, actorUserId);
    return get(companyId, contactId);
  }

  @Transactional(readOnly = true)
  public Lead get(long companyId, long contactId) {
    List<Lead> found = jdbc.query(
        """
        select l.*, c.display_name, c.organization, c.owner_user_id
        from crm_leads l
        join contacts c on c.company_id = l.company_id and c.id = l.contact_id
        where l.company_id = ? and l.contact_id = ? and c.status = 'ACTIVE'
        """,
        LeadService::mapLead,
        companyId, contactId);
    if (found.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "CRM_LEAD_NOT_FOUND");
    return found.getFirst();
  }

  @Transactional(readOnly = true)
  public LeadPage list(long companyId, Stage stage, Long beforeContactId, int requestedSize) {
    int size = Math.max(1, Math.min(requestedSize, 100));
    if (beforeContactId != null && beforeContactId <= 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_INVALID_CURSOR");
    }
    List<Lead> rows = jdbc.query(
        """
        select l.*, c.display_name, c.organization, c.owner_user_id
        from crm_leads l
        join contacts c on c.company_id = l.company_id and c.id = l.contact_id
        where l.company_id = ?
          and c.status = 'ACTIVE'
          and (cast(? as varchar) is null or l.stage = ?)
          and (cast(? as bigint) is null or l.contact_id < ?)
        order by l.contact_id desc
        limit ?
        """,
        LeadService::mapLead,
        companyId, stage == null ? null : stage.name(), stage == null ? null : stage.name(),
        beforeContactId, beforeContactId, size + 1);
    boolean more = rows.size() > size;
    List<Lead> items = more ? rows.subList(0, size) : rows;
    return new LeadPage(
        items,
        more && !items.isEmpty() ? items.getLast().contactId() : null,
        more);
  }

  @Transactional
  public Lead move(long companyId, long contactId, Stage stage, long expectedVersion, long actorUserId) {
    if (stage == null || expectedVersion < 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_INVALID_STAGE_CHANGE");
    }
    requireActiveContact(companyId, contactId);
    List<Lead> locked = jdbc.query(
        """
        select l.*, c.display_name, c.organization, c.owner_user_id
        from crm_leads l
        join contacts c on c.company_id = l.company_id and c.id = l.contact_id
        where l.company_id = ? and l.contact_id = ? and c.status = 'ACTIVE'
        for update of l
        """,
        LeadService::mapLead,
        companyId, contactId);
    if (locked.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "CRM_LEAD_NOT_FOUND");
    Lead previous = locked.getFirst();
    if (previous.version() != expectedVersion) {
      throw new ApiException(HttpStatus.CONFLICT, "CRM_LEAD_VERSION_CONFLICT");
    }
    if (previous.stage() == stage) return previous;
    jdbc.update(
        """
        update crm_leads
        set stage = ?, version = version + 1, updated_by_user_id = ?, updated_at = now()
        where company_id = ? and contact_id = ? and version = ?
        """,
        stage.name(), actorUserId, companyId, contactId, expectedVersion);
    appendEvent(companyId, contactId, previous.stage(), stage, actorUserId);
    return get(companyId, contactId);
  }

  @Transactional(readOnly = true)
  public List<StageEvent> history(long companyId, long contactId) {
    get(companyId, contactId);
    return jdbc.query(
        """
        select id, previous_stage, new_stage, actor_user_id, occurred_at
        from crm_lead_stage_events
        where company_id = ? and contact_id = ?
        order by occurred_at desc, id desc
        limit 100
        """,
        (rs, row) -> new StageEvent(
            Long.toString(rs.getLong("id")),
            rs.getString("previous_stage") == null ? null : Stage.valueOf(rs.getString("previous_stage")),
            Stage.valueOf(rs.getString("new_stage")),
            nullableId(rs, "actor_user_id"),
            rs.getTimestamp("occurred_at").toInstant()),
        companyId, contactId);
  }

  @Transactional(readOnly = true)
  public LeadSummary summary(long companyId) {
    return jdbc.queryForObject(
        """
        select count(*) as total,
          count(*) filter (where l.stage = 'NEW') as new_leads,
          count(*) filter (where l.stage = 'QUALIFIED') as qualified,
          count(*) filter (where l.stage = 'CONVERTED') as converted
        from crm_leads l
        join contacts c on c.company_id = l.company_id and c.id = l.contact_id
        where l.company_id = ? and c.status = 'ACTIVE'
        """,
        (rs, row) -> new LeadSummary(
            rs.getLong("total"), rs.getLong("new_leads"),
            rs.getLong("qualified"), rs.getLong("converted")),
        companyId);
  }

  private void appendEvent(long companyId, long contactId, Stage before, Stage after, long actor) {
    jdbc.update(
        """
        insert into crm_lead_stage_events
          (id, company_id, contact_id, previous_stage, new_stage, actor_user_id)
        values (?, ?, ?, ?, ?, ?)
        """,
        IdGenerator.nextId(), companyId, contactId,
        before == null ? null : before.name(), after.name(), actor);
  }

  private void requireActiveContact(long companyId, long contactId) {
    if (contacts.find(companyId, contactId)
        .filter(contact -> contact.status() == ContactStatus.ACTIVE).isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "CRM_CONTACT_NOT_FOUND");
    }
  }

  private static Lead mapLead(ResultSet rs, int row) throws SQLException {
    return new Lead(
        Long.toString(rs.getLong("contact_id")),
        rs.getString("display_name"),
        rs.getString("organization"),
        nullableId(rs, "owner_user_id"),
        Stage.valueOf(rs.getString("stage")),
        rs.getLong("version"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private static String nullableId(ResultSet rs, String key) throws SQLException {
    Object value = rs.getObject(key);
    return value == null ? null : value.toString();
  }
}
