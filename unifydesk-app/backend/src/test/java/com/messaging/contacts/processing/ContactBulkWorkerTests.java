package com.messaging.contacts.processing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.contacts.audience.ContactAudienceProperties;
import com.messaging.contacts.processing.ContactBulkService.Selection;
import com.messaging.contacts.processing.ContactBulkService.SelectionType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ContactBulkWorkerTests {

  @Test
  void deleteCheckpointsEachContactThroughCoordinator() {
    ContactBulkRepository repository = mock(ContactBulkRepository.class);
    ContactBulkService bulkService = mock(ContactBulkService.class);
    ContactBulkSelectionResolver selectionResolver = mock(ContactBulkSelectionResolver.class);
    ContactAudienceProperties properties = mock(ContactAudienceProperties.class);
    AsyncJobService asyncJobs = mock(AsyncJobService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ContactBulkMutationPort> mutationPortProvider = mock(ObjectProvider.class);
    ContactBulkMutationPort mutationPort = mock(ContactBulkMutationPort.class);
    ContactBulkDeleteCoordinator deleteCoordinator = mock(ContactBulkDeleteCoordinator.class);

    ContactBulkWorker worker =
        new ContactBulkWorker(
            repository,
            bulkService,
            selectionResolver,
            properties,
            asyncJobs,
            mutationPortProvider,
            deleteCoordinator);

    ContactBulkRepository.BulkRow initial = row(0);
    ContactBulkRepository.BulkRow afterFirst = row(10);
    ContactBulkRepository.BulkRow afterSecond = row(20);
    Selection selection = new Selection(SelectionType.IDS, List.of(10L, 20L), null, null);
    ContactBulkMutationPort.Operation operation =
        new ContactBulkMutationPort.Operation("DELETE", null, null, null);

    when(mutationPortProvider.getIfAvailable()).thenReturn(mutationPort);
    when(repository.start(1L, 2L)).thenReturn(initial);
    when(repository.require(1L, 2L)).thenReturn(afterFirst, afterSecond);
    when(bulkService.parseSelection("{}")).thenReturn(selection);
    when(bulkService.parseOperation("{}")).thenReturn(operation);
    when(properties.boundedBatchSize()).thenReturn(500);
    when(selectionResolver.page(1L, selection, 0L, 500)).thenReturn(List.of(10L, 20L));
    when(selectionResolver.page(1L, selection, 20L, 500)).thenReturn(List.of());
    when(deleteCoordinator.deleteAndCheckpoint(1L, 2L, 7L, 0L, 10L))
        .thenReturn(new ContactBulkMutationPort.BatchResult(1, 0));
    when(deleteCoordinator.deleteAndCheckpoint(1L, 2L, 7L, 10L, 20L))
        .thenReturn(new ContactBulkMutationPort.BatchResult(1, 0));

    worker.process(1L, 2L, 3L);

    verify(deleteCoordinator).deleteAndCheckpoint(1L, 2L, 7L, 0L, 10L);
    verify(deleteCoordinator).deleteAndCheckpoint(1L, 2L, 7L, 10L, 20L);
    verify(mutationPort, never()).apply(anyLong(), anyLong(), anyLong(), any(), any());
    verify(repository).complete(1L, 2L);
    verify(asyncJobs).complete(3L, "Bulk operation completed");
  }

  private ContactBulkRepository.BulkRow row(long cursor) {
    return new ContactBulkRepository.BulkRow(
        2L,
        1L,
        7L,
        "DELETE",
        "{}",
        "{}",
        "RUNNING",
        cursor,
        cursor == 0 ? 0 : cursor == 10 ? 1 : 2,
        cursor == 0 ? 0 : cursor == 10 ? 1 : 2,
        cursor == 0 ? 0 : cursor == 10 ? 1 : 2,
        0,
        3L);
  }
}
