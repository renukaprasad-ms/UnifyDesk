package com.messaging.contacts.core.repository;

import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.core.domain.ContactTypes.CustomFieldType;
import com.messaging.contacts.core.domain.ContactTypes.DefinitionStatus;
import com.messaging.contacts.core.domain.CoreRows.CustomFieldDefinitionRow;
import com.messaging.contacts.core.domain.CoreRows.CustomFieldValueRow;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomFieldRepository {

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;

  public CustomFieldRepository(
      JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    this.namedJdbcTemplate = namedJdbcTemplate;
  }

  public long countActiveDefinitions(long companyId) {
    Long count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from contact_custom_field_definitions
            where company_id = ? and status = 'ACTIVE'
            """,
            Long.class,
            companyId);
    return count == null ? 0 : count;
  }

  public CustomFieldDefinitionRow insertDefinition(
      long companyId,
      String fieldKey,
      String label,
      String normalizedLabel,
      CustomFieldType type,
      String optionsJson,
      int position) {
    long id = IdGenerator.nextId();
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        insert into contact_custom_field_definitions
          (id, company_id, field_key, label, normalized_label, field_type, options_json,
           required, position, status, created_at, updated_at)
        values (?, ?, ?, ?, ?, ?, cast(? as jsonb), false, ?, 'ACTIVE', ?, ?)
        """,
        id,
        companyId,
        fieldKey,
        label,
        normalizedLabel,
        type.name(),
        optionsJson,
        position,
        Timestamp.from(now),
        Timestamp.from(now));
    return requireDefinition(companyId, id);
  }

  public Optional<CustomFieldDefinitionRow> findDefinition(long companyId, long fieldId) {
    return jdbcTemplate
        .query(
            "select * from contact_custom_field_definitions where company_id = ? and id = ?",
            (rs, rowNum) -> mapDefinition(rs),
            companyId,
            fieldId)
        .stream()
        .findFirst();
  }

  public CustomFieldDefinitionRow requireDefinition(long companyId, long fieldId) {
    return findDefinition(companyId, fieldId).orElseThrow();
  }

  public List<CustomFieldDefinitionRow> listDefinitions(long companyId) {
    return jdbcTemplate.query(
        """
        select *
        from contact_custom_field_definitions
        where company_id = ?
        order by status, position, id
        """,
        (rs, rowNum) -> mapDefinition(rs),
        companyId);
  }

  public Map<Long, CustomFieldDefinitionRow> findActiveDefinitions(
      long companyId, Collection<Long> fieldIds) {
    if (fieldIds.isEmpty()) {
      return Map.of();
    }
    MapSqlParameterSource params =
        new MapSqlParameterSource("companyId", companyId).addValue("fieldIds", fieldIds);
    return namedJdbcTemplate
        .query(
            """
            select *
            from contact_custom_field_definitions
            where company_id = :companyId
              and id in (:fieldIds)
              and status = 'ACTIVE'
            """,
            params,
            (rs, rowNum) -> mapDefinition(rs))
        .stream()
        .collect(Collectors.toUnmodifiableMap(CustomFieldDefinitionRow::id, row -> row));
  }

  public void updateDefinition(
      long companyId,
      long fieldId,
      String label,
      String normalizedLabel,
      String optionsJson,
      int position) {
    int changed =
        jdbcTemplate.update(
            """
            update contact_custom_field_definitions
            set label = ?,
                normalized_label = ?,
                options_json = cast(? as jsonb),
                position = ?,
                updated_at = now()
            where company_id = ? and id = ? and status = 'ACTIVE'
            """,
            label,
            normalizedLabel,
            optionsJson,
            position,
            companyId,
            fieldId);
    if (changed == 0) {
      throw new java.util.NoSuchElementException();
    }
  }

  public void archiveDefinition(long companyId, long fieldId) {
    int changed =
        jdbcTemplate.update(
            """
            update contact_custom_field_definitions
            set status = 'ARCHIVED', updated_at = now()
            where company_id = ? and id = ? and status = 'ACTIVE'
            """,
            companyId,
            fieldId);
    if (changed == 0 && findDefinition(companyId, fieldId).isEmpty()) {
      throw new java.util.NoSuchElementException();
    }
  }

  public List<CustomFieldValueRow> values(long companyId, long contactId) {
    return jdbcTemplate.query(
        """
        select *
        from contact_custom_field_values
        where company_id = ? and contact_id = ?
        order by field_id
        """,
        (rs, rowNum) -> mapValue(rs),
        companyId,
        contactId);
  }

  public void deleteValues(long companyId, long contactId, Collection<Long> fieldIds) {
    if (fieldIds.isEmpty()) {
      return;
    }
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("companyId", companyId)
            .addValue("contactId", contactId)
            .addValue("fieldIds", fieldIds);
    namedJdbcTemplate.update(
        """
        delete from contact_custom_field_values
        where company_id = :companyId
          and contact_id = :contactId
          and field_id in (:fieldIds)
        """,
        params);
  }

  public void upsertValues(long companyId, long contactId, List<TypedValue> values) {
    if (values.isEmpty()) {
      return;
    }
    jdbcTemplate.execute(
        (ConnectionCallback<Void>)
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      """
                      insert into contact_custom_field_values
                        (company_id, contact_id, field_id, text_value, number_value,
                         boolean_value, date_value, datetime_value, text_array_value, updated_at)
                      values (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                      on conflict (contact_id, field_id) do update
                      set company_id = excluded.company_id,
                          text_value = excluded.text_value,
                          number_value = excluded.number_value,
                          boolean_value = excluded.boolean_value,
                          date_value = excluded.date_value,
                          datetime_value = excluded.datetime_value,
                          text_array_value = excluded.text_array_value,
                          updated_at = now()
                      """)) {
                for (TypedValue value : values) {
                  statement.setLong(1, companyId);
                  statement.setLong(2, contactId);
                  statement.setLong(3, value.fieldId());
                  statement.setObject(4, value.textValue());
                  statement.setObject(5, value.numberValue());
                  statement.setObject(6, value.booleanValue());
                  statement.setObject(7, value.dateValue());
                  statement.setObject(
                      8,
                      value.datetimeValue() == null
                          ? null
                          : Timestamp.from(value.datetimeValue()));
                  Array sqlArray =
                      value.textArrayValue() == null
                          ? null
                          : connection.createArrayOf(
                              "text", value.textArrayValue().toArray(String[]::new));
                  statement.setArray(9, sqlArray);
                  statement.addBatch();
                }
                statement.executeBatch();
              }
              return null;
            });
  }

  public void mergeValues(
      long companyId, long sourceId, long targetId, Collection<Long> sourceWinsFieldIds) {
    if (!sourceWinsFieldIds.isEmpty()) {
      MapSqlParameterSource params =
          new MapSqlParameterSource()
              .addValue("companyId", companyId)
              .addValue("sourceId", sourceId)
              .addValue("targetId", targetId)
              .addValue("fieldIds", sourceWinsFieldIds);
      namedJdbcTemplate.update(
          """
          insert into contact_custom_field_values
            (company_id, contact_id, field_id, text_value, number_value, boolean_value,
             date_value, datetime_value, text_array_value, updated_at)
          select company_id, :targetId, field_id, text_value, number_value, boolean_value,
                 date_value, datetime_value, text_array_value, now()
          from contact_custom_field_values
          where company_id = :companyId
            and contact_id = :sourceId
            and field_id in (:fieldIds)
          on conflict (contact_id, field_id) do update
          set text_value = excluded.text_value,
              number_value = excluded.number_value,
              boolean_value = excluded.boolean_value,
              date_value = excluded.date_value,
              datetime_value = excluded.datetime_value,
              text_array_value = excluded.text_array_value,
              updated_at = now()
          """,
          params);
    }

    jdbcTemplate.update(
        """
        insert into contact_custom_field_values
          (company_id, contact_id, field_id, text_value, number_value, boolean_value,
           date_value, datetime_value, text_array_value, updated_at)
        select company_id, ?, field_id, text_value, number_value, boolean_value,
               date_value, datetime_value, text_array_value, now()
        from contact_custom_field_values
        where company_id = ? and contact_id = ?
        on conflict (contact_id, field_id) do nothing
        """,
        targetId,
        companyId,
        sourceId);
    jdbcTemplate.update(
        "delete from contact_custom_field_values where company_id = ? and contact_id = ?",
        companyId,
        sourceId);
  }

  private static CustomFieldDefinitionRow mapDefinition(ResultSet rs) throws SQLException {
    return new CustomFieldDefinitionRow(
        rs.getLong("id"),
        rs.getLong("company_id"),
        rs.getString("field_key"),
        rs.getString("label"),
        rs.getString("normalized_label"),
        CustomFieldType.valueOf(rs.getString("field_type")),
        rs.getString("options_json"),
        rs.getInt("position"),
        DefinitionStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private static CustomFieldValueRow mapValue(ResultSet rs) throws SQLException {
    Array array = rs.getArray("text_array_value");
    List<String> values =
        array == null
            ? null
            : List.of((String[]) array.getArray());
    Timestamp datetime = rs.getTimestamp("datetime_value");
    return new CustomFieldValueRow(
        rs.getLong("company_id"),
        rs.getLong("contact_id"),
        rs.getLong("field_id"),
        rs.getString("text_value"),
        rs.getBigDecimal("number_value"),
        (Boolean) rs.getObject("boolean_value"),
        rs.getObject("date_value", LocalDate.class),
        datetime == null ? null : datetime.toInstant(),
        values,
        rs.getTimestamp("updated_at").toInstant());
  }

  public record TypedValue(
      long fieldId,
      String textValue,
      BigDecimal numberValue,
      Boolean booleanValue,
      LocalDate dateValue,
      Instant datetimeValue,
      List<String> textArrayValue) {}
}
