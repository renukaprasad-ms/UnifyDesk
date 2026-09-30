package com.messaging.contacts.processing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.id.IdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactBulkService {

  private final ContactBulkRepository repository;
  private final ObjectMapper objectMapper;
  private final AsyncJobService asyncJobs;

  @Transactional
  public CreateResult create(
      long companyId,
      long userId,
      Selection selection,
      ContactBulkMutationPort.Operation operation,
      String idempotencyKey) {
    validate(selection, operation, idempotencyKey);

    Selection normalizedSelection = normalizeSelection(selection);
    ContactBulkMutationPort.Operation normalizedOperation = normalizeOperation(operation);
    String selectionJson = canonicalJson(normalizedSelection);
    String operationJson = canonicalJson(normalizedOperation);

    repository.lockIdempotency(companyId, idempotencyKey);
    var existing = repository.findByIdempotency(companyId, idempotencyKey);
    if (existing.isPresent()) {
      ContactBulkRepository.BulkRow row = existing.get();
      if (!sameCanonicalRequest(row, selectionJson, operationJson)) {
        throw new ConflictException("CONTACT_BULK_IDEMPOTENCY_CONFLICT");
      }
      return new CreateResult(row.id(), null);
    }

    long operationId = IdGenerator.nextId();
    AsyncJobResponse job =
        asyncJobs.create(
            companyId,
            userId,
            AsyncJobType.CONTACT_BULK,
            Long.toString(operationId),
            null,
            "Contact bulk operation queued");
    repository.create(
        operationId,
        companyId,
        userId,
        normalizedOperation.type(),
        selectionJson,
        operationJson,
        idempotencyKey,
        Long.parseLong(job.jobId()));
    return new CreateResult(operationId, job);
  }

  public ContactBulkRepository.BulkRow get(long companyId, long operationId) {
    return repository.require(companyId, operationId);
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

  private boolean sameCanonicalRequest(
      ContactBulkRepository.BulkRow row,
      String requestedSelectionJson,
      String requestedOperationJson) {
    Selection storedSelection = normalizeSelection(parseSelection(row.selectionJson()));
    ContactBulkMutationPort.Operation storedOperation =
        normalizeOperation(parseOperation(row.operationJson()));
    return Objects.equals(canonicalJson(storedSelection), requestedSelectionJson)
        && Objects.equals(canonicalJson(storedOperation), requestedOperationJson);
  }

  private Selection normalizeSelection(Selection selection) {
    return switch (selection.type()) {
      case IDS ->
          new Selection(
              SelectionType.IDS,
              selection.ids().stream()
                  .distinct()
                  .sorted()
                  .toList(),
              null,
              null);
      case LIST -> new Selection(SelectionType.LIST, null, selection.sourceId(), null);
      case SEGMENT -> new Selection(SelectionType.SEGMENT, null, selection.sourceId(), null);
      case FILTER ->
          new Selection(
              SelectionType.FILTER,
              null,
              null,
              normalizeFilter(selection.filter()));
    };
  }

  private ContactFilter normalizeFilter(ContactFilter filter) {
    String q =
        filter.q() == null || filter.q().isBlank()
            ? null
            : filter.q().trim().toLowerCase(Locale.ROOT);
    String status =
        filter.status() == null || filter.status().isBlank()
            ? null
            : filter.status().trim().toUpperCase(Locale.ROOT);
    String sort =
        filter.sort() == null || filter.sort().isBlank()
            ? null
            : filter.sort().trim();
    return new ContactFilter(
        q,
        status,
        filter.tagId(),
        filter.listId(),
        filter.createdAfter(),
        filter.createdBefore(),
        sort);
  }

  private ContactBulkMutationPort.Operation normalizeOperation(
      ContactBulkMutationPort.Operation operation) {
    return new ContactBulkMutationPort.Operation(
        operation.type(),
        operation.targetId(),
        operation.fieldKey(),
        canonicalValueJson(operation.valueJson()));
  }

  private String canonicalValueJson(String valueJson) {
    if (valueJson == null) return null;
    try {
      return objectMapper.writeValueAsString(canonicalNode(objectMapper.readTree(valueJson)));
    } catch (JsonProcessingException exception) {
      throw invalid();
    }
  }

  private JsonNode canonicalNode(JsonNode node) {
    if (node == null || node.isNull() || node.isValueNode()) return node;
    if (node.isArray()) {
      ArrayNode array = objectMapper.createArrayNode();
      for (JsonNode child : node) array.add(canonicalNode(child));
      return array;
    }
    if (node.isObject()) {
      ObjectNode object = objectMapper.createObjectNode();
      TreeMap<String, JsonNode> sorted = new TreeMap<>();
      node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
      sorted.forEach((key, value) -> object.set(key, canonicalNode(value)));
      return object;
    }
    return node;
  }

  private String canonicalJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw invalid();
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
      throw invalid();
    }
    if (selection.type() == SelectionType.IDS
        && (selection.ids() == null
            || selection.ids().isEmpty()
            || selection.ids().size() > 500
            || selection.ids().stream().anyMatch(id -> id == null || id <= 0))) {
      throw invalid();
    }
    if ((selection.type() == SelectionType.LIST
            || selection.type() == SelectionType.SEGMENT)
        && (selection.sourceId() == null || selection.sourceId() <= 0)) {
      throw invalid();
    }
    if (selection.type() == SelectionType.FILTER && selection.filter() == null) {
      throw invalid();
    }
    if (!Set.of(
            "ADD_TAG",
            "REMOVE_TAG",
            "ADD_TO_LIST",
            "REMOVE_FROM_LIST",
            "UPDATE_FIELD",
            "DELETE")
        .contains(operation.type())) {
      throw invalid();
    }
  }

  private BadRequestException invalid() {
    return new BadRequestException("CONTACT_BULK_INVALID");
  }

  public enum SelectionType {
    IDS,
    LIST,
    SEGMENT,
    FILTER
  }

  public record ContactFilter(
      String q,
      String status,
      Long tagId,
      Long listId,
      Instant createdAfter,
      Instant createdBefore,
      String sort) {}

  public record Selection(
      SelectionType type, List<Long> ids, Long sourceId, ContactFilter filter) {}

  public record CreateResult(long operationId, AsyncJobResponse job) {}
}
