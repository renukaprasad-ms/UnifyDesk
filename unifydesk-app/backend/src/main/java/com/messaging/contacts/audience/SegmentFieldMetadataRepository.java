package com.messaging.contacts.audience;

import com.messaging.common.exception.BadRequestException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SegmentFieldMetadataRepository {

  private final JdbcTemplate jdbcTemplate;

  public CustomFieldMetadata requireActiveCustomField(long companyId, long fieldId) {
    return jdbcTemplate
        .query(
            """
            select id, field_type
            from contact_custom_field_definitions
            where company_id = ? and id = ? and status = 'ACTIVE'
            """,
            (rs, rowNum) ->
                new CustomFieldMetadata(
                    rs.getLong("id"),
                    CustomFieldType.valueOf(
                        rs.getString("field_type").toUpperCase(Locale.ROOT))),
            companyId,
            fieldId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new BadRequestException("CONTACT_SEGMENT_INVALID"));
  }

  public record CustomFieldMetadata(long id, CustomFieldType type) {}

  public enum CustomFieldType {
    TEXT,
    NUMBER,
    BOOLEAN,
    DATE,
    DATETIME,
    SINGLE_SELECT,
    MULTI_SELECT
  }
}
