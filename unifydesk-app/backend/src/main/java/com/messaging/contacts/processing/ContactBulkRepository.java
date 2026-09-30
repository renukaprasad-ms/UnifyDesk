package com.messaging.contacts.processing;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ContactBulkRepository {

  private final JdbcTemplate jdbcTemplate;

  public Optional<BulkRow> findByIdempotency(long companyId, String key) {
    return jdbcTemplate
        .query(
            "select * from contact_bulk_operations where company_id = ? and idempotency_key = ?",
            this::map,
            companyId,
            key)
        .stream()
        .findFirst();
  }

  public BulkRow require(long companyId, long id) {
    return jdbcTemplate
        .query(
            "select * from contact_bulk_operations where company_id = ? and id = ?",
            this::map,
            companyId,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND"));
  }

  @Transactional
  public BulkRow create(
      long id,
      long companyId,
      long userId,
      String operationType,
      String selectionJson,
      String operationJson,
      String idempotencyKey,
      long jobId) {
    jdbcTemplate.update(
        """
        insert into contact_bulk_operations (
          id, company_id, requested_by_user_id, operation_type, selection_json, operation_json,
          idempotency_key, status, async_job_id, created_at
        ) values (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, 'QUEUED', ?, ?)
        """,
        id,
        companyId,
        userId,
        operationType,
        selectionJson,
        operationJson,
        idempotencyKey,
        jobId,
        Timestamp.from(Instant.now()));
    return require(companyId, id);
  }

  @Transactional
  public BulkRow start(long companyId, long id) {
    jdbcTemplate.update(
        "update contact_bulk_operations set status = 'RUNNING' where company_id = ? and id = ? and status in ('QUEUED','RUNNING')",
        companyId,
        id);
    return require(companyId, id);
  }

  @Transactional
  public void advance(
      long companyId,
      long id,
      long expectedCursor,
      long newCursor,
      long candidateDelta,
      long succeededDelta,
      long failedDelta) {
    int updated =
        jdbcTemplate.update(
            """
            update contact_bulk_operations
            set cursor = ?, candidate_count = candidate_count + ?,
                processed_count = processed_count + ?,
                succeeded_count = succeeded_count + ?,
                failed_count = failed_count + ?
            where company_id = ? and id = ? and status = 'RUNNING' and cursor = ?
            """,
            newCursor,
            candidateDelta,
            candidateDelta,
            succeededDelta,
            failedDelta,
            companyId,
            id,
            expectedCursor);
    if (updated != 1) throw new IllegalStateException("Bulk cursor conflict");
  }

  @Transactional
  public void complete(long companyId, long id) {
    jdbcTemplate.update(
        "update contact_bulk_operations set status = 'COMPLETED', completed_at = ? where company_id = ? and id = ? and status = 'RUNNING'",
        Timestamp.from(Instant.now()),
        companyId,
        id);
  }

  @Transactional
  public void fail(long companyId, long id) {
    jdbcTemplate.update(
        "update contact_bulk_operations set status = 'FAILED', completed_at = ? where company_id = ? and id = ? and status in ('QUEUED','RUNNING')",
        Timestamp.from(Instant.now()),
        companyId,
        id);
  }

  private BulkRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    return new BulkRow(
        rs.getLong("id"),
        rs.getLong("company_id"),
        rs.getLong("requested_by_user_id"),
        rs.getString("operation_type"),
        rs.getString("selection_json"),
        rs.getString("operation_json"),
        rs.getString("status"),
        rs.getLong("cursor"),
        rs.getLong("candidate_count"),
        rs.getLong("processed_count"),
        rs.getLong("succeeded_count"),
        rs.getLong("failed_count"),
        (Long) rs.getObject("async_job_id"));
  }

  public record BulkRow(
      long id,
      long companyId,
      long requestedByUserId,
      String operationType,
      String selectionJson,
      String operationJson,
      String status,
      long cursor,
      long candidateCount,
      long processedCount,
      long succeededCount,
      long failedCount,
      Long asyncJobId) {}
}
