package com.messaging.contacts.processing;

import com.messaging.common.id.IdGenerator;
import com.messaging.media.enums.StorageProviderType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ContactExportRepository {

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public ExportRow create(
      long companyId,
      long userId,
      String sourceType,
      Long sourceId,
      String fieldsJson,
      String idempotencyKey,
      long jobId,
      Instant expiresAt) {
    Optional<ExportRow> existing = findByIdempotency(companyId, idempotencyKey);
    if (existing.isPresent()) return existing.get();
    long id = IdGenerator.nextId();
    jdbcTemplate.update(
        """
        insert into contact_exports (
          id, company_id, requested_by_user_id, source_type, source_id,
          field_selection_json, idempotency_key, status, async_job_id, created_at, expires_at
        ) values (?, ?, ?, ?, ?, ?::jsonb, ?, 'QUEUED', ?, ?, ?)
        on conflict (company_id, idempotency_key) do nothing
        """,
        id,
        companyId,
        userId,
        sourceType,
        sourceId,
        fieldsJson,
        idempotencyKey,
        jobId,
        Timestamp.from(Instant.now()),
        Timestamp.from(expiresAt));
    return findByIdempotency(companyId, idempotencyKey).orElseThrow();
  }

  public ExportRow require(long companyId, long id) {
    return jdbcTemplate
        .query(
            "select * from contact_exports where company_id = ? and id = ?",
            this::map,
            companyId,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND"));
  }

  @Transactional
  public void markRunning(long companyId, long id) {
    jdbcTemplate.update(
        "update contact_exports set status = 'RUNNING' where company_id = ? and id = ? and status in ('QUEUED','RUNNING')",
        companyId,
        id);
  }

  @Transactional
  public void complete(
      long companyId,
      long id,
      long rows,
      ContactStorageAdapter.PublishedObject object) {
    jdbcTemplate.update(
        """
        update contact_exports
        set status = 'COMPLETED', row_count = ?, output_provider = ?, output_bucket = ?,
            output_object_key = ?, output_size_bytes = ?, completed_at = ?
        where company_id = ? and id = ? and status = 'RUNNING'
        """,
        rows,
        object.provider().name(),
        object.bucket(),
        object.objectKey(),
        object.sizeBytes(),
        Timestamp.from(Instant.now()),
        companyId,
        id);
  }

  @Transactional
  public void fail(long companyId, long id) {
    jdbcTemplate.update(
        "update contact_exports set status = 'FAILED', completed_at = ? where company_id = ? and id = ? and status in ('QUEUED','RUNNING')",
        Timestamp.from(Instant.now()),
        companyId,
        id);
  }

  private Optional<ExportRow> findByIdempotency(long companyId, String key) {
    return jdbcTemplate
        .query(
            "select * from contact_exports where company_id = ? and idempotency_key = ?",
            this::map,
            companyId,
            key)
        .stream()
        .findFirst();
  }

  private ExportRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    String provider = rs.getString("output_provider");
    return new ExportRow(
        rs.getLong("id"),
        rs.getLong("company_id"),
        rs.getLong("requested_by_user_id"),
        rs.getString("source_type"),
        (Long) rs.getObject("source_id"),
        rs.getString("field_selection_json"),
        rs.getString("idempotency_key"),
        rs.getString("status"),
        rs.getLong("row_count"),
        provider == null ? null : StorageProviderType.valueOf(provider),
        rs.getString("output_bucket"),
        rs.getString("output_object_key"),
        (Long) rs.getObject("output_size_bytes"),
        (Long) rs.getObject("async_job_id"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("expires_at").toInstant());
  }

  public record ExportRow(
      long id,
      long companyId,
      long requestedByUserId,
      String sourceType,
      Long sourceId,
      String fieldsJson,
      String idempotencyKey,
      String status,
      long rowCount,
      StorageProviderType outputProvider,
      String outputBucket,
      String outputObjectKey,
      Long outputSizeBytes,
      Long asyncJobId,
      Instant createdAt,
      Instant expiresAt) {}
}
