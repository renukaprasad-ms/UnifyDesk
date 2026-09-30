package com.messaging.contacts.processing;

import com.messaging.contacts.processing.ContactImportMutationPort.RowFailure;
import com.messaging.media.enums.StorageProviderType;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ContactImportRepository {

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public ImportRow create(
      long id,
      long companyId,
      long userId,
      String originalFileName,
      String contentType,
      StorageProviderType provider,
      String bucket,
      String objectKey,
      long expectedSize,
      Instant expiresAt) {
    jdbcTemplate.update(
        """
        insert into contact_imports (
          id, company_id, requested_by_user_id, original_file_name, content_type,
          storage_provider, storage_bucket, storage_object_key, expected_size_bytes,
          status, created_at, expires_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT_UPLOAD', ?, ?)
        """,
        id,
        companyId,
        userId,
        originalFileName,
        contentType,
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
  public void appendFailures(long companyId, long importId, List<RowFailure> failures) {
    if (failures == null || failures.isEmpty()) return;
    Instant now = Instant.now();
    jdbcTemplate.batchUpdate(
        """
        insert into contact_import_error_rows (
          import_id, company_id, row_number, error_code, safe_message, created_at
        ) values (?, ?, ?, ?, ?, ?)
        on conflict (import_id, row_number, error_code) do nothing
        """,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int index) throws SQLException {
            RowFailure failure = failures.get(index);
            ps.setLong(1, importId);
            ps.setLong(2, companyId);
            ps.setLong(3, failure.rowNumber());
            ps.setString(4, safe(failure.code(), 64));
            ps.setString(5, safe(failure.safeMessage(), 500));
            ps.setTimestamp(6, Timestamp.from(now));
          }

          @Override
          public int getBatchSize() {
            return failures.size();
          }
        });
  }

  public List<StoredError> pageErrors(
      long companyId, long importId, long afterRow, String afterCode, int limit) {
    return jdbcTemplate.query(
        """
        select row_number, error_code, safe_message
        from contact_import_error_rows
        where company_id = ? and import_id = ?
          and (row_number > ? or (row_number = ? and error_code > ?))
        order by row_number asc, error_code asc
        limit ?
        """,
        (rs, rowNum) ->
            new StoredError(
                rs.getLong("row_number"),
                rs.getString("error_code"),
                rs.getString("safe_message")),
        companyId,
        importId,
        afterRow,
        afterRow,
        afterCode == null ? "" : afterCode,
        limit);
  }

  @Transactional
  public void attachErrorReport(
      long companyId, long importId, ContactStorageAdapter.PublishedObject object) {
    jdbcTemplate.update(
        """
        update contact_imports
        set error_report_provider = ?, error_report_bucket = ?,
            error_report_object_key = ?, error_report_size_bytes = ?
        where company_id = ? and id = ?
        """,
        object.provider().name(),
        object.bucket(),
        object.objectKey(),
        object.sizeBytes(),
        companyId,
        importId);
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

  public Optional<ImportRow> findByIdempotency(long companyId, String key) {
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

  private ImportRow map(ResultSet rs, int rowNum) throws SQLException {
    String errorProvider = rs.getString("error_report_provider");
    return new ImportRow(
        rs.getLong("id"),
        rs.getLong("company_id"),
        rs.getLong("requested_by_user_id"),
        rs.getString("original_file_name"),
        rs.getString("content_type"),
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
        errorProvider == null ? null : StorageProviderType.valueOf(errorProvider),
        rs.getString("error_report_bucket"),
        rs.getString("error_report_object_key"),
        (Long) rs.getObject("error_report_size_bytes"),
        (Long) rs.getObject("async_job_id"),
        rs.getTimestamp("created_at").toInstant(),
        instant(rs.getTimestamp("started_at")),
        instant(rs.getTimestamp("completed_at")),
        rs.getTimestamp("expires_at").toInstant());
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private static String safe(String value, int max) {
    String normalized = value == null || value.isBlank() ? "UNKNOWN" : value.replaceAll("[\\r\\n\\t]+", " ");
    return normalized.length() <= max ? normalized : normalized.substring(0, max);
  }

  public record QueueResult(ImportRow row, boolean newlyQueued) {}

  public record StoredError(long rowNumber, String code, String safeMessage) {}

  public record ImportRow(
      long id,
      long companyId,
      long requestedByUserId,
      String originalFileName,
      String contentType,
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
      StorageProviderType errorReportProvider,
      String errorReportBucket,
      String errorReportObjectKey,
      Long errorReportSizeBytes,
      Long asyncJobId,
      Instant createdAt,
      Instant startedAt,
      Instant completedAt,
      Instant expiresAt) {}
}
