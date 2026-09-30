package com.messaging.contacts.processing;

import com.messaging.contacts.processing.ContactImportMutationPort.BatchResult;
import com.messaging.contacts.processing.ContactImportMutationPort.DuplicateStrategy;
import com.messaging.contacts.processing.ContactImportMutationPort.ImportMapping;
import com.messaging.contacts.processing.ContactImportMutationPort.RowFailure;
import com.messaging.contacts.processing.ContactImportRepository.ImportRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits one already-parsed import batch atomically with its durable cursor and row outcomes.
 *
 * <p>CSV/storage I/O stays outside this transaction. Contact mutations, failure rows and checkpoint
 * advancement either commit together or roll back together.
 */
@Service
@RequiredArgsConstructor
public class ContactImportBatchCoordinator {

  private final ContactImportRepository repository;

  @Transactional
  public BatchResult commit(
      ImportRow importRow,
      ImportMapping mapping,
      DuplicateStrategy duplicateStrategy,
      List<ContactImportMutationPort.ImportRow> batch,
      ContactImportMutationPort mutationPort) {
    BatchResult result =
        mutationPort.applyBatch(
            importRow.companyId(),
            importRow.id(),
            importRow.requestedByUserId(),
            mapping,
            duplicateStrategy,
            importRow.targetListId(),
            batch);

    List<RowFailure> failures =
        result.failures() == null ? List.of() : List.copyOf(result.failures());
    repository.appendFailures(importRow.companyId(), importRow.id(), failures);
    repository.advance(
        importRow.companyId(),
        importRow.id(),
        batch.getLast().rowNumber(),
        batch.size(),
        result.succeeded(),
        failures.size());
    return new BatchResult(result.succeeded(), failures);
  }
}
