package com.messaging.contacts.processing;

import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.contacts.audience.ContactAudienceProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactBulkWorker {

  private final ContactBulkRepository repository;
  private final ContactBulkService bulkService;
  private final ContactBulkSelectionResolver selectionResolver;
  private final ContactAudienceProperties properties;
  private final AsyncJobService asyncJobs;
  private final ObjectProvider<ContactBulkMutationPort> mutationPortProvider;
  private final ContactBulkDeleteCoordinator deleteCoordinator;

  public void process(long companyId, long operationId, long jobId) {
    ContactBulkMutationPort port = mutationPortProvider.getIfAvailable();
    if (port == null) throw new IllegalStateException("CONTACT_BULK_CORE_ADAPTER_NOT_INTEGRATED");
    ContactBulkRepository.BulkRow row = repository.start(companyId, operationId);
    var selection = bulkService.parseSelection(row.selectionJson());
    var operation = bulkService.parseOperation(row.operationJson());
    asyncJobs.startOrResume(jobId, row.candidateCount(), "Bulk operation running");

    while (true) {
      List<Long> ids =
          selectionResolver.page(
              companyId, selection, row.cursor(), properties.boundedBatchSize());
      if (ids.isEmpty()) {
        repository.complete(companyId, operationId);
        asyncJobs.complete(jobId, "Bulk operation completed");
        return;
      }
      if ("DELETE".equals(operation.type())) {
        for (Long contactId : ids) {
          long expectedCursor = row.cursor();
          deleteCoordinator.deleteAndCheckpoint(
              companyId,
              operationId,
              row.requestedByUserId(),
              expectedCursor,
              contactId);
          asyncJobs.incrementProgress(jobId, 1, "Bulk operation running");
          row = repository.require(companyId, operationId);
        }
      } else {
        var result =
            port.apply(
                companyId, operationId, row.requestedByUserId(), operation, ids);
        long newCursor = ids.getLast();
        repository.advance(
            companyId,
            operationId,
            row.cursor(),
            newCursor,
            ids.size(),
            result.succeeded(),
            result.failed());
        asyncJobs.incrementProgress(jobId, ids.size(), "Bulk operation running");
        row = repository.require(companyId, operationId);
      }
    }
  }
}
