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
public class LeadService {
  private final JdbcTemplate jdbc;
  private final ContactRepository contacts;
  private final CrmCustomFieldService crmCustomFields;
  private final CrmLeadScoringService crmLeadScoringService;
  private final CrmAutomationService crmAutomationService;

  @org.springframework.beans.factory.annotation.Autowired
  public LeadService(
      JdbcTemplate jdbc,
      ContactRepository contacts,
      @org.springframework.beans.factory.annotation.Autowired(required = false)
      CrmCustomFieldService crmCustomFields,
      @org.springframework.beans.factory.annotation.Autowired(required = false)
      CrmLeadScoringService crmLeadScoringService,
      @org.springframework.beans.factory.annotation.Autowired(required = false)
      CrmAutomationService crmAutomationService) {
    this.jdbc = jdbc;
    this.contacts = contacts;
    this.crmCustomFields = crmCustomFields;
    this.crmLeadScoringService = crmLeadScoringService;
    this.crmAutomationService = crmAutomationService;
  }

  public LeadService(JdbcTemplate jdbc, ContactRepository contacts) {
    this(jdbc, contacts, null, null, null);
  }

  private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
      new com.fasterxml.jackson.databind.ObjectMapper();
  private static final com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>> MAP_REF =
      new com.fasterxml.jackson.core.type.TypeReference<>() {};

  public enum Stage {
    NEW, CONTACTED, QUALIFIED, UNQUALIFIED, CONVERTED
  }

  public record Lead(
      String contactId,
      String displayName,
      String organization,
      String ownerUserId,
      Stage stage,
      java.util.Map<String, Object> customFields,
      int score,
      String temperature,
      long version,
      Instant createdAt,
      Instant updatedAt) {}

  public record LeadPage(List<Lead> items, String nextCursor, boolean hasMore) {}

  public record LeadSummary(long total, long newLeads, long qualified, long converted) {}

  public record StageEvent(
      String id, Stage previousStage, Stage newStage, String actorUserId, Instant occurredAt,
      String actorName, String actorUsername) {}

  @Transactional
  public Lead enroll(long companyId, long contactId, long actorUserId) {
    return enroll(companyId, contactId, java.util.Map.of(), actorUserId);
  }

  @Transactional
  public Lead enroll(
      long companyId,
      long contactId,
      java.util.Map<String, Object> incomingCustomFields,
      long actorUserId) {
    requireActiveContact(companyId, contactId);
    java.util.Map<String, Object> cleanCustom =
        crmCustomFields != null
            ? crmCustomFields.validateValues(
                companyId, CrmCustomFieldService.EntityType.LEAD, incomingCustomFields)
            : incomingCustomFields != null ? incomingCustomFields : java.util.Map.of();
    String json;
    try {
      json = MAPPER.writeValueAsString(cleanCustom);
    } catch (Exception e) {
      json = "{}";
    }
    try {
      jdbc.update(
          """
          insert into crm_leads
            (company_id, contact_id, stage, custom_fields, created_by_user_id, updated_by_user_id)
          values (?, ?, 'NEW', cast(? as jsonb), ?, ?)
          """,
          companyId, contactId, json, actorUserId, actorUserId);
    } catch (DuplicateKeyException e) {
      throw new ApiException(HttpStatus.CONFLICT, "CRM_LEAD_ALREADY_EXISTS");
    }
    appendEvent(companyId, contactId, null, Stage.NEW, actorUserId);
    if (crmLeadScoringService != null) {
      try {
        crmLeadScoringService.calculateScore(companyId, contactId);
      } catch (Exception ignored) {}
    }
    Lead created = get(companyId, contactId);
    if (crmAutomationService != null) {
      try {
        crmAutomationService.fireEvent(
            companyId,
            "LEAD_ENROLLED",
            "LEAD",
            String.valueOf(contactId),
            java.util.Map.of(
                "contactId", contactId,
                "contactName", created.displayName() != null ? created.displayName() : "Lead",
                "ownerUserId", created.ownerUserId() != null ? created.ownerUserId() : "",
                "stage", "NEW"));
      } catch (Exception ignored) {}
    }
    return created;
  }

  @Transactional
  public Lead updateCustomFields(
      long companyId,
      long contactId,
      java.util.Map<String, Object> incomingCustomFields,
      long actorUserId) {
    requireActiveContact(companyId, contactId);
    get(companyId, contactId);
    java.util.Map<String, Object> cleanCustom =
        crmCustomFields != null
            ? crmCustomFields.validateValues(
                companyId, CrmCustomFieldService.EntityType.LEAD, incomingCustomFields)
            : incomingCustomFields != null ? incomingCustomFields : java.util.Map.of();
    String json;
    try {
      json = MAPPER.writeValueAsString(cleanCustom);
    } catch (Exception e) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CRM_CUSTOM_FIELD_SERIALIZE_ERROR");
    }
    jdbc.update(
        """
        update crm_leads
        set custom_fields = cast(? as jsonb), updated_by_user_id = ?, updated_at = now()
        where company_id = ? and contact_id = ?
        """,
        json, actorUserId, companyId, contactId);
    if (crmLeadScoringService != null) {
      try {
        crmLeadScoringService.calculateScore(companyId, contactId);
      } catch (Exception ignored) {}
    }
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
  public LeadPage list(
      long companyId, Stage stage, String temperature, Long beforeContactId, int requestedSize) {
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
          and (cast(? as varchar) is null or l.temperature = ?)
          and (cast(? as bigint) is null or l.contact_id < ?)
        order by l.score desc, l.contact_id desc
        limit ?
        """,
        LeadService::mapLead,
        companyId,
        stage == null ? null : stage.name(),
        stage == null ? null : stage.name(),
        temperature == null || temperature.isBlank() ? null : temperature.trim().toUpperCase(),
        temperature == null || temperature.isBlank() ? null : temperature.trim().toUpperCase(),
        beforeContactId,
        beforeContactId,
        size + 1);
    boolean more = rows.size() > size;
    List<Lead> items = more ? rows.subList(0, size) : rows;
    return new LeadPage(
        items,
        more && !items.isEmpty() ? items.getLast().contactId() : null,
        more);
  }

  @Transactional(readOnly = true)
  public LeadPage list(long companyId, Stage stage, Long beforeContactId, int requestedSize) {
    return list(companyId, stage, null, beforeContactId, requestedSize);
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
    if (crmLeadScoringService != null) {
      try {
        crmLeadScoringService.calculateScore(companyId, contactId);
      } catch (Exception ignored) {}
    }
    Lead moved = get(companyId, contactId);
    if (crmAutomationService != null) {
      try {
        crmAutomationService.fireEvent(
            companyId,
            "LEAD_STAGE_CHANGED",
            "LEAD",
            String.valueOf(contactId),
            java.util.Map.of(
                "contactId", contactId,
                "contactName", moved.displayName() != null ? moved.displayName() : "Lead",
                "fromStage", previous.stage().name(),
                "toStage", stage.name(),
                "ownerUserId", moved.ownerUserId() != null ? moved.ownerUserId() : ""));
      } catch (Exception ignored) {}
    }
    return moved;
  }

  @Transactional(readOnly = true)
  public List<StageEvent> history(long companyId, long contactId) {
    get(companyId, contactId);
    return jdbc.query(
        """
        select event.id, event.previous_stage, event.new_stage, event.actor_user_id,
          event.occurred_at, actor.name as actor_name, actor.username as actor_username
        from crm_lead_stage_events event
        left join users actor on actor.id = event.actor_user_id
        where event.company_id = ? and event.contact_id = ?
        order by event.occurred_at desc, event.id desc
        limit 100
        """,
        (rs, row) -> new StageEvent(
            Long.toString(rs.getLong("id")),
            rs.getString("previous_stage") == null ? null : Stage.valueOf(rs.getString("previous_stage")),
            Stage.valueOf(rs.getString("new_stage")),
            nullableId(rs, "actor_user_id"),
            rs.getTimestamp("occurred_at").toInstant(),
            rs.getString("actor_name"),
            rs.getString("actor_username")),
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
    java.util.Map<String, Object> customFields = java.util.Map.of();
    String rawCustom = rs.getString("custom_fields");
    if (rawCustom != null && !rawCustom.isBlank()) {
      try {
        customFields = MAPPER.readValue(rawCustom, MAP_REF);
      } catch (Exception ignored) {}
    }
    int score = 0;
    try {
      score = rs.getInt("score");
    } catch (Exception ignored) {}
    String temperature = "COLD";
    try {
      String rawTemp = rs.getString("temperature");
      if (rawTemp != null && !rawTemp.isBlank()) temperature = rawTemp.toUpperCase();
    } catch (Exception ignored) {}

    return new Lead(
        Long.toString(rs.getLong("contact_id")),
        rs.getString("display_name"),
        rs.getString("organization"),
        nullableId(rs, "owner_user_id"),
        Stage.valueOf(rs.getString("stage")),
        customFields,
        score,
        temperature,
        rs.getLong("version"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private static String nullableId(ResultSet rs, String key) throws SQLException {
    Object value = rs.getObject(key);
    return value == null ? null : value.toString();
  }
}
