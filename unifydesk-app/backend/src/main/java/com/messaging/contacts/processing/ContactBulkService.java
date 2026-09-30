package com.messaging.contacts.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.audience.SegmentFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactBulkService {

  private final ContactBulkRepository repository;
  private final ObjectMapper objectMapper;
  private final AsyncJobService asyncJobs;

  public CreateResult create(
      long companyId,
      long userId,
      Selection selection,
      ContactBulkMutationPort.Operation operation,
      String idempotencyKey) {
    validate(selection, operation, idempotencyKey);
    var existing = repository.findByIdempotency(companyId, idempotencyKey);
    if (existing.isPresent()) return new CreateResult(existing.get().id(), null);

    long operationId = IdGenerator.nextId();
    AsyncJobResponse job =
        asyncJobs.create(
            companyId,
            userId,
            AsyncJobType.CONTACT_BULK,
            Long.toString(operationId),
            null,
            "Contact bulk operation queued");
    try {
      repository.create(
          operationId,
          companyId,
          userId,
          operation.type(),
          objectMapper.writeValueAsString(selection),
          objectMapper.writeValueAsString(operation),
          idempotencyKey,
          Long.parseLong(job.jobId()));
    } catch (Exception exception) {
      throw new com.messaging.common.exception.BadRequestException("CONTACT_BULK_INVALID");
    }
    return new CreateResult(operationId, job);
  }

  public Selection parseSelection(String json) {
    try {
      return objectMapper.readValue(json, Selection.class);
    } catch (Exception exception) {
      throw new IllegalStateException("Stored bulk selection is invalid", exception);
    }
  }

  public ContactBulkMutationPort.Operation parseOperation(String json) {
    try {
      return objectMapper.readValue(json, ContactBulkMutationPort.Operation.class);
    } catch (Exception exception) {
      throw new IllegalStateException("Stored bulk operation is invalid", exception);
    }
  }

  private void validate(
      Selection selection, ContactBulkMutationPort.Operation operation, String idempotencyKey) {
    if (selection == null
        || selection.type() == null
        || operation == null
        || operation.type() == null
        || idempotencyKey == null
        || idempotencyKey.isBlank()
        || idempotencyKey.length() > 160) {
      throw new com.messaging.common.exception.BadRequestException("CONTACT_BULK_INVALID");
    }
    if (selection.type() == SelectionType.IDS
        && (selection.ids() == null || selection.ids().isEmpty() || selection.ids().size() > 500)) {
      throw new com.messaging.common.exception.BadRequestException("CONTACT_BULK_INVALID");
    }
    if (!Set.of(
            "ADD_TAG", "REMOVE_TAG", "ADD_TO_LIST", "REMOVE_FROM_LIST", "UPDATE_FIELD", "DELETE")
        .contains(operation.type())) {
      throw new com.messaging.common.exception.BadRequestException("CONTACT_BULK_INVALID");
    }
  }

  public enum SelectionType {
    IDS,
    LIST,
    SEGMENT,
    FILTER
  }

  public record Selection(
      SelectionType type, List<Long> ids, Long sourceId, SegmentFilter filter) {}

  public record CreateResult(long operationId, AsyncJobResponse job) {}
}
