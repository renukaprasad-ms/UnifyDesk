package com.messaging.contacts.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.common.id.IdGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactBulkService {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final AsyncJobService asyncJobs;

  public CreateResult create(
      long companyId,
      long userId,
      Selection selection,
      ContactBulkMutationPort.Operation operation,
      String idempotencyKey) {
    if (selection == null || operation == null || idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new com.messaging.common.exception.BadRequestException("CONTACT_FIELD_INVALID");
    }
    long existing =
        jdbcTemplate.query(
                "select id from contact_bulk_operations where company_id = ? and idempotency_key = ?",
                (rs, rowNum) -> rs.getLong(1),
                companyId,
                idempotencyKey)
            .stream()
            .findFirst()
            .orElse(0L);
    if (existing != 0) {
      return new CreateResult(existing, null);
    }
    AsyncJobResponse job =
        asyncJobs.create(companyId, userId, AsyncJobType.CONTACT_BULK, null, null, "Contact bulk operation queued");
    long id = IdGenerator.nextId();
    try {
      jdbcTemplate.update(
          """
          insert into contact_bulk_operations (
            id, company_id, requested_by_user_id, operation_type, selection_json, operation_json,
            idempotency_key, status, async_job_id, created_at
          ) values (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, 'QUEUED', ?, ?)
          on conflict (company_id, idempotency_key) do nothing
          """,
          id,
          companyId,
          userId,
          operation.type(),
          objectMapper.writeValueAsString(selection),
          objectMapper.writeValueAsString(operation),
          idempotencyKey,
          Long.parseLong(job.id()),
          Timestamp.from(Instant.now()));
    } catch (Exception exception) {
      throw new com.messaging.common.exception.BadRequestException("CONTACT_FIELD_INVALID");
    }
    return new CreateResult(id, job);
  }

  public enum SelectionType {
    IDS,
    LIST,
    SEGMENT,
    FILTER
  }

  public record Selection(
      SelectionType type, java.util.List<Long> ids, Long sourceId, com.messaging.contacts.audience.SegmentFilter filter) {}

  public record CreateResult(long operationId, AsyncJobResponse job) {}
}
