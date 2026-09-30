package com.messaging.contacts.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.common.exception.ConflictException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ContactBulkServiceTests {

  private ContactBulkRepository repository;
  private AsyncJobService asyncJobs;
  private ObjectMapper objectMapper;
  private ContactBulkService service;

  @BeforeEach
  void setUp() {
    repository = mock(ContactBulkRepository.class);
    asyncJobs = mock(AsyncJobService.class);
    objectMapper = new ObjectMapper();
    service = new ContactBulkService(repository, objectMapper, asyncJobs);
  }

  @Test
  void sameKeyAndSameLogicalRequestReturnsOriginalOperation() throws Exception {
    ContactBulkService.Selection storedSelection =
        new ContactBulkService.Selection(
            ContactBulkService.SelectionType.IDS, List.of(1L, 2L), null, null);
    ContactBulkMutationPort.Operation operation =
        new ContactBulkMutationPort.Operation("ADD_TAG", 9L, null, null);
    ContactBulkRepository.BulkRow existing =
        bulkRow(
            objectMapper.writeValueAsString(storedSelection),
            objectMapper.writeValueAsString(operation));
    when(repository.findByIdempotency(5L, "same")).thenReturn(Optional.of(existing));

    var result =
        service.create(
            5L,
            7L,
            new ContactBulkService.Selection(
                ContactBulkService.SelectionType.IDS, List.of(2L, 1L, 2L), null, null),
            operation,
            "same");

    assertEquals(44L, result.operationId());
    verify(asyncJobs, never())
        .create(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    InOrder order = inOrder(repository);
    order.verify(repository).lockIdempotency(5L, "same");
    order.verify(repository).findByIdempotency(5L, "same");
  }

  @Test
  void sameKeyWithDifferentSelectionConflicts() throws Exception {
    ContactBulkMutationPort.Operation operation =
        new ContactBulkMutationPort.Operation("ADD_TAG", 9L, null, null);
    when(repository.findByIdempotency(5L, "key"))
        .thenReturn(
            Optional.of(
                bulkRow(
                    objectMapper.writeValueAsString(
                        new ContactBulkService.Selection(
                            ContactBulkService.SelectionType.IDS,
                            List.of(1L),
                            null,
                            null)),
                    objectMapper.writeValueAsString(operation))));

    assertThrows(
        ConflictException.class,
        () ->
            service.create(
                5L,
                7L,
                new ContactBulkService.Selection(
                    ContactBulkService.SelectionType.IDS, List.of(2L), null, null),
                operation,
                "key"));
  }

  @Test
  void sameKeyWithDifferentOperationConflicts() throws Exception {
    ContactBulkService.Selection selection =
        new ContactBulkService.Selection(
            ContactBulkService.SelectionType.IDS, List.of(1L), null, null);
    when(repository.findByIdempotency(5L, "key"))
        .thenReturn(
            Optional.of(
                bulkRow(
                    objectMapper.writeValueAsString(selection),
                    objectMapper.writeValueAsString(
                        new ContactBulkMutationPort.Operation(
                            "ADD_TAG", 9L, null, null)))));

    assertThrows(
        ConflictException.class,
        () ->
            service.create(
                5L,
                7L,
                selection,
                new ContactBulkMutationPort.Operation("DELETE", null, null, null),
                "key"));
  }

  @Test
  void canonicalizesJsonObjectFieldOrderForUpdateFieldReplay() throws Exception {
    ContactBulkService.Selection selection =
        new ContactBulkService.Selection(
            ContactBulkService.SelectionType.IDS, List.of(1L), null, null);
    ContactBulkMutationPort.Operation stored =
        new ContactBulkMutationPort.Operation(
            "UPDATE_FIELD", 88L, null, "{\"a\":1,\"b\":2}");
    when(repository.findByIdempotency(5L, "key"))
        .thenReturn(
            Optional.of(
                bulkRow(
                    objectMapper.writeValueAsString(selection),
                    objectMapper.writeValueAsString(stored))));

    var result =
        service.create(
            5L,
            7L,
            selection,
            new ContactBulkMutationPort.Operation(
                "UPDATE_FIELD", 88L, null, "{\"b\":2,\"a\":1}"),
            "key");

    assertEquals(44L, result.operationId());
  }

  private ContactBulkRepository.BulkRow bulkRow(
      String selectionJson, String operationJson) {
    return new ContactBulkRepository.BulkRow(
        44L,
        5L,
        7L,
        "ADD_TAG",
        selectionJson,
        operationJson,
        "QUEUED",
        0,
        0,
        0,
        0,
        0,
        100L);
  }
}
