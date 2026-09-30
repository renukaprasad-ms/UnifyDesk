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
public class ContactImportRepository {

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public ImportRow create(
      long companyId,
      long userId,
      StorageProviderType provider,
      String bucket,
      String objectKey,
      long expectedSize,
      Instant expiresAt) {
    long id = IdGenerator.nextId();
    jdbcTemplate.update(
        """
        insert into contact_imports (
          id, company_id, requested_by_user_id, storage_provider, storage_bucket,
          storage_object_key, expected_size_bytes, status, created_at, expires_at
        ) values (?, ?, ?, ?, ?, ?, ?, 'DRAFT_UPLOAD', ?, ?)
        """,
        id,
        companyId,
        userId,
        provider.name(),
        bucket,
        objectKey,
        expectedSize,
        Timestamp.from(Instant.now()),
        Timestamp.from(expiresAt));
    return require(companyId, id);
  }

  public ImportRow require(long companyId, long id) {
    return jdbcTemplate
        .query(
            "select * from contact_imports where company_id = ? and id = ?",
            this::map,
            companyId,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND"));
  }

  @Transactional
  public void markUploaded(long companyId, long id) {
    jdbcTemplate.update(
        """
        update contact_imports
        set status = case when status = 'DRAFT_UPLOAD' then 'UPLOADED' else status end
        where company_id = ? and id = ?
        """,
        companyId,
        id);
  }

  @Transactional
  public void markPreviewed(long companyId, long id) {
    jdbcTemplate.update(
        """
        update contact_imports
        set status = 'PREVIEWED'
        where company_id = ? and id = ? and status in ('UPLOADED','PREVIEWED')
        """,
        companyId,
        id);
  }

  @Transactional
  public QueueResult queue(
      long companyId,
      long id,
      String contentSha256,
      String mappingJson,
      String mappingHash,
      String duplicateStrategy,
      Long targetListId,
      String idempotencyKey,
      long asyncJobId) {
    ImportRow current = requireForUpdate(companyId, id);
    Optional<ImportRow> existing = findByIdempotency(companyId, idempotencyKey);
    if (existing.isPresent() && existing.get().id() != id) {
      ImportRow other = existing.get();
      if (Objects.equals(other.contentSha256(), contentSha256)
          && Objects.equals(other.mappingHash(), mappingHash)) {
        return new QueueResult(other, false);
      }
      throw new com.messaging.common.exception.ConflictException(
          "CONTACT_IMPORT_IDEMPOTENCY_CONFLICT");
    }
    if (!Set.of("UPLOADED", "PREVIEWED").contains(current.status())) {
      if (Objects.equals(current.idempotencyKey(), idempotencyKey)
          && Objects.equals(current.contentSha256(), contentSha256)
          && Objects.equals(current.mappingHash(), mappingHash)) {
        return new QueueResult(current, false);
      }
      throw new com.messaging.common.exception.ConflictException(
          "CONTACT_IMPORT_IDEMPOTENCY_CONFLICT");
    }
    jdbcTemplate.update(
        """
        update contact_imports
        set content_sha256 = ?, mapping_json = ?::jsonb, mapping_hash = ?,
            duplicate_strategy = ?, target_list_id = ?, idempotency_key = ?,
            status = 'QUEUED', async_job_id = ?
        where company_id = ? and id = ?
        """,
        contentSha256,
        mappingJson,
        mappingHash,
        duplicateStrategy,
        targetListId,
        idempotencyKey,
        asyncJobId,
        companyId,
        id);
    return new QueueResult(require(companyId, id), true);
  }

  @Transactional
  public ImportRow start(long companyId, long id) {
    jdbcTemplate.update(
        """
        update contact_imports
        set status = 'RUNNING', started_at = coalesce(started_at, ?)
        where company_id = ? and id = ? and status in ('QUEUED','RUNNING')
        """,
        Timestamp.from(Instant.now()),
        companyId,
        id);
    return require(companyId, id);
  }

  @Transactional
  public void advance(
      long companyId,
      long id,
      long rowCursor,
      long processedDelta,
      long succeededDelta,
      long failedDelta) {
    jdbcTemplate.update(
        """
        update contact_imports
        set processed_row_cursor = greatest(processed_row_cursor, ?),
            processed_rows = processed_rows + ?,
            succeeded_rows = succeeded_rows + ?,
            failed_rows = failed_rows + ?
        where company_id = ? and id = ? and status = 'RUNNING'
        """,
        rowCursor,
        processedDelta,
        succeededDelta,
        failedDelta,
        companyId,
        id);
  }

  @Transactional
  public void complete(long companyId, long id, long totalRows) {
    jdbcTemplate.update(
        """
        update contact_imports
        set status = 'COMPLETED', total_rows = ?, completed_at = ?
        where company_id = ? and id = ? and status = 'RUNNING'
        """,
        totalRows,
        Timestamp.from(Instant.now()),
        companyId,
        id);
  }

  @Transactional
  public void fail(long companyId, long id) {
    jdbcTemplate.update(
        """
        update contact_imports
        set status = 'FAILED', completed_at = ?
        where company_id = ? and id = ? and status in ('QUEUED','RUNNING')
        """,
        Timestamp.from(Instant.now()),
        companyId,
        id);
  }

  private Optional<ImportRow> findByIdempotency(long companyId, String key) {
    return jdbcTemplate
        .query(
            "select * from contact_imports where company_id = ? and idempotency_key = ?",
            this::map,
            companyId,
            key)
        .stream()
        .findFirst();
  }

  private ImportRow requireForUpdate(long companyId, long id) {
    return jdbcTemplate
        .query(
            "select * from contact_imports where company_id = ? and id = ? for update",
            this::map,
            companyId,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND"));
  }

  private ImportRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    return new ImportRow(
        rs.getLong("id"),
        rs.getLong("company_id"),
        rs.getLong("requested_by_user_id"),
        StorageProviderType.valueOf(rs.getString("storage_provider")),
        rs.getString("storage_bucket"),
        rs.getString("storage_object_key"),
        rs.getLong("expected_size_bytes"),
        rs.getString("content_sha256"),
        rs.getString("mapping_json"),
        rs.getString("mapping_hash"),
        rs.getString("duplicate_strategy"),
        (Long) rs.getObject("target_list_id"),
        rs.getString("idempotency_key"),
        rs.getString("status"),
        rs.getLong("total_rows"),
        rs.getLong("processed_rows"),
        rs.getLong("succeeded_rows"),
        rs.getLong("failed_rows"),
        rs.getLong("processed_row_cursor"),
        (Long) rs.getObject("async_job_id"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("expires_at").toInstant());
  }

  public record QueueResult(ImportRow row, boolean newlyQueued) {}

  public record ImportRow(
      long id,
      long companyId,
      long requestedByUserId,
      StorageProviderType provider,
      String bucket,
      String objectKey,
      long expectedSizeBytes,
      String contentSha256,
      String mappingJson,
      String mappingHash,
      String duplicateStrategy,
      Long targetListId,
      String idempotencyKey,
      String status,
      long totalRows,
      long processedRows,
      long succeededRows,
      long failedRows,
      long processedRowCursor,
      Long asyncJobId,
      Instant createdAt,
      Instant expiresAt) {}
}
