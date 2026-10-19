package com.messaging.crm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.ApiException;
import com.messaging.common.id.IdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages workspace-scoped custom field definitions for CRM entities (LEAD, DEAL, ACCOUNT). Values
 * are stored as JSONB on the parent entity row — no separate value table. Validation against
 * definitions happens at write time in this service.
 */
@Service
@RequiredArgsConstructor
public class CrmCustomFieldService {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  /** The three entity types that support custom fields. */
  public enum EntityType {
    LEAD,
    DEAL,
    ACCOUNT
  }

  /** All supported field types. */
  public enum FieldType {
    TEXT,
    NUMBER,
    DATE,
    BOOLEAN,
    DROPDOWN,
    MULTI_SELECT,
    URL
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> STRING_LIST_REF = new TypeReference<>() {};
  private static final int MAX_FIELDS_PER_ENTITY = 50;
  private static final Set<FieldType> OPTIONS_REQUIRED =
      Set.of(FieldType.DROPDOWN, FieldType.MULTI_SELECT);

  public record FieldDef(
      String id,
      String entityType,
      String fieldKey,
      String label,
      String fieldType,
      List<String> options,
      boolean required,
      int displayOrder,
      Instant createdAt) {}

  public record CreateInput(
      EntityType entityType,
      String fieldKey,
      String label,
      FieldType fieldType,
      List<String> options,
      boolean required,
      Integer displayOrder) {}

  public record UpdateInput(
      String label, List<String> options, boolean required, Integer displayOrder) {}

  // ── Queries ──────────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<FieldDef> list(long companyId, EntityType entityType) {
    return jdbc.query(
        """
        select * from crm_custom_field_defs
        where company_id = ? and entity_type = ?
        order by display_order asc, id asc
        """,
        CrmCustomFieldService::mapDef,
        companyId,
        entityType.name());
  }

  @Transactional(readOnly = true)
  public FieldDef get(long companyId, long fieldId) {
    List<FieldDef> rows =
        jdbc.query(
            "select * from crm_custom_field_defs where company_id = ? and id = ?",
            CrmCustomFieldService::mapDef,
            companyId,
            fieldId);
    if (rows.isEmpty()) throw notFound();
    return rows.getFirst();
  }

  // ── Mutations ─────────────────────────────────────────────────────────────────

  @Transactional
  public FieldDef create(long companyId, long actor, CreateInput input) {
    validate(input);
    Integer count =
        jdbc.queryForObject(
            "select count(*) from crm_custom_field_defs where company_id = ? and entity_type = ?",
            Integer.class,
            companyId,
            input.entityType().name());
    if (count != null && count >= MAX_FIELDS_PER_ENTITY) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_CUSTOM_FIELD_LIMIT_REACHED");
    }
    int order = input.displayOrder() != null ? input.displayOrder() : (count == null ? 0 : count);
    long key = IdGenerator.nextId();
    try {
      jdbc.update(
          """
          insert into crm_custom_field_defs
            (id,company_id,entity_type,field_key,label,field_type,options,required,display_order,created_by_user_id)
          values(?,?,?,?,?,?,cast(? as jsonb),?,?,?)
          """,
          key,
          companyId,
          input.entityType().name(),
          input.fieldKey(),
          input.label().trim(),
          input.fieldType().name(),
          serializeOptions(input.options()),
          input.required(),
          order,
          actor);
    } catch (DuplicateKeyException e) {
      throw new ApiException(HttpStatus.CONFLICT, "CRM_CUSTOM_FIELD_KEY_EXISTS");
    }
    return get(companyId, key);
  }

  @Transactional
  public FieldDef update(long companyId, long fieldId, UpdateInput input) {
    FieldDef existing = get(companyId, fieldId);
    FieldType fieldType = FieldType.valueOf(existing.fieldType());
    List<String> opts =
        OPTIONS_REQUIRED.contains(fieldType) ? validateOptions(input.options()) : null;
    String safeLabel = input.label() == null ? null : input.label().trim();
    if (safeLabel == null || safeLabel.isBlank() || safeLabel.length() > 120) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_CUSTOM_FIELD_INVALID_LABEL");
    }
    int order = input.displayOrder() != null ? input.displayOrder() : existing.displayOrder();
    jdbc.update(
        """
        update crm_custom_field_defs
           set label = ?, options = cast(? as jsonb), required = ?, display_order = ?, updated_at = now()
         where company_id = ? and id = ?
        """,
        safeLabel,
        serializeOptions(opts),
        input.required(),
        order,
        companyId,
        fieldId);
    return get(companyId, fieldId);
  }

  @Transactional
  public void delete(long companyId, long fieldId) {
    FieldDef def = get(companyId, fieldId);
    // Remove the key from all entity rows in one update per table
    String table =
        switch (EntityType.valueOf(def.entityType())) {
          case LEAD -> "crm_leads";
          case DEAL -> "crm_deals";
          case ACCOUNT -> "crm_accounts";
        };
    String column =
        switch (EntityType.valueOf(def.entityType())) {
          case LEAD, DEAL, ACCOUNT -> "custom_fields";
        };
    // Strip key from existing JSONB values so no orphan data remains
    jdbc.update(
        "update "
            + table
            + " set "
            + column
            + " = "
            + column
            + " - ? where company_id = ? and "
            + column
            + " ? ?",
        def.fieldKey(),
        companyId,
        def.fieldKey());
    jdbc.update(
        "delete from crm_custom_field_defs where company_id = ? and id = ?", companyId, fieldId);
  }

  // ── Value validation (called by other CRM services at write time) ─────────────

  /**
   * Validates and sanitizes a client-supplied custom field map against the current definitions.
   * Returns a clean map safe to serialize as JSONB. Throws 400 on any violation.
   */
  public Map<String, Object> validateValues(
      long companyId, EntityType entityType, Map<String, Object> incoming) {
    return validateValues(companyId, entityType, incoming, true);
  }

  /**
   * Validates and sanitizes a client-supplied custom field map. When enforceRequired is false, only
   * fields actually provided in incoming are validated/coerced, without demanding that every
   * globally required custom field be present.
   */
  public Map<String, Object> validateValues(
      long companyId,
      EntityType entityType,
      Map<String, Object> incoming,
      boolean enforceRequired) {
    if (incoming == null || incoming.isEmpty()) return Map.of();
    List<FieldDef> defs = list(companyId, entityType);
    Map<String, FieldDef> byKey =
        defs.stream().collect(java.util.stream.Collectors.toMap(FieldDef::fieldKey, d -> d));
    // Check all required fields are present if enforceRequired is requested
    if (enforceRequired) {
      for (FieldDef def : defs) {
        if (def.required() && !incoming.containsKey(def.fieldKey())) {
          throw new ApiException(
              HttpStatus.BAD_REQUEST, "CRM_CUSTOM_FIELD_REQUIRED:" + def.fieldKey());
        }
      }
    }
    java.util.Map<String, Object> clean = new java.util.LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : incoming.entrySet()) {
      String key = entry.getKey();
      FieldDef def = byKey.get(key);
      if (def == null) continue; // silently drop unknown keys
      Object value = entry.getValue();
      if (value == null) continue; // null = not set
      if (value instanceof String s && s.trim().isEmpty()) continue; // skip blank optional value
      clean.put(key, coerce(def, value));
    }
    return clean;
  }

  // ── Private helpers ───────────────────────────────────────────────────────────

  private void validate(CreateInput input) {
    if (input.entityType() == null || input.fieldType() == null) {
      throw bad();
    }
    String key = input.fieldKey();
    if (key == null || !key.matches("^[a-z][a-z0-9_]{0,62}$")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_CUSTOM_FIELD_INVALID_KEY");
    }
    if (input.label() == null || input.label().isBlank() || input.label().trim().length() > 120) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_CUSTOM_FIELD_INVALID_LABEL");
    }
    if (OPTIONS_REQUIRED.contains(input.fieldType())) {
      validateOptions(input.options());
    } else if (input.options() != null && !input.options().isEmpty()) {
      throw bad();
    }
  }

  private List<String> validateOptions(List<String> options) {
    if (options == null || options.isEmpty() || options.size() > 50) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_CUSTOM_FIELD_INVALID_OPTIONS");
    }
    List<String> clean = options.stream().map(String::trim).filter(s -> !s.isBlank()).toList();
    if (clean.size() != options.size() || clean.size() > 50) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_CUSTOM_FIELD_INVALID_OPTIONS");
    }
    return clean;
  }

  private Object coerce(FieldDef def, Object value) {
    return switch (FieldType.valueOf(def.fieldType())) {
      case TEXT, URL -> {
        String s = value.toString().trim();
        if (s.length() > 1000)
          throw new ApiException(
              HttpStatus.BAD_REQUEST, "CRM_CUSTOM_FIELD_VALUE_TOO_LONG:" + def.fieldKey());
        yield s;
      }
      case NUMBER -> {
        try {
          yield new java.math.BigDecimal(value.toString());
        } catch (NumberFormatException e) {
          throw new ApiException(
              HttpStatus.BAD_REQUEST, "CRM_CUSTOM_FIELD_INVALID_NUMBER:" + def.fieldKey());
        }
      }
      case DATE -> {
        try {
          yield java.time.LocalDate.parse(value.toString());
        } catch (Exception e) {
          throw new ApiException(
              HttpStatus.BAD_REQUEST, "CRM_CUSTOM_FIELD_INVALID_DATE:" + def.fieldKey());
        }
      }
      case BOOLEAN -> {
        if (value instanceof Boolean b) yield b;
        if ("true".equalsIgnoreCase(value.toString())) yield true;
        if ("false".equalsIgnoreCase(value.toString())) yield false;
        throw new ApiException(
            HttpStatus.BAD_REQUEST, "CRM_CUSTOM_FIELD_INVALID_BOOLEAN:" + def.fieldKey());
      }
      case DROPDOWN -> {
        String s = value.toString();
        if (!def.options().contains(s))
          throw new ApiException(
              HttpStatus.BAD_REQUEST, "CRM_CUSTOM_FIELD_INVALID_OPTION:" + def.fieldKey());
        yield s;
      }
      case MULTI_SELECT -> {
        @SuppressWarnings("unchecked")
        List<String> selected =
            value instanceof List<?> l
                ? l.stream().map(Object::toString).toList()
                : Arrays.asList(value.toString().split(","));
        List<String> invalid = selected.stream().filter(s -> !def.options().contains(s)).toList();
        if (!invalid.isEmpty())
          throw new ApiException(
              HttpStatus.BAD_REQUEST, "CRM_CUSTOM_FIELD_INVALID_OPTION:" + def.fieldKey());
        yield selected;
      }
    };
  }

  private String serializeOptions(List<String> options) {
    if (options == null) return null;
    try {
      return objectMapper.writeValueAsString(options);
    } catch (Exception e) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CRM_CUSTOM_FIELD_SERIALIZE_ERROR");
    }
  }

  private static FieldDef mapDef(ResultSet rs, int row) throws SQLException {
    List<String> options = null;
    String raw = rs.getString("options");
    if (raw != null) {
      try {
        options = MAPPER.readValue(raw, STRING_LIST_REF);
      } catch (Exception ignored) {
      }
    }
    return new FieldDef(
        Long.toString(rs.getLong("id")),
        rs.getString("entity_type"),
        rs.getString("field_key"),
        rs.getString("label"),
        rs.getString("field_type"),
        options,
        rs.getBoolean("required"),
        rs.getInt("display_order"),
        rs.getTimestamp("created_at").toInstant());
  }

  private static ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "CRM_CUSTOM_FIELD_NOT_FOUND");
  }

  private static ApiException bad() {
    return new ApiException(HttpStatus.BAD_REQUEST, "CRM_CUSTOM_FIELD_INVALID_INPUT");
  }
}
