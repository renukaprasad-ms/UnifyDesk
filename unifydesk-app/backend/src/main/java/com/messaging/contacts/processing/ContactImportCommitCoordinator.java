package com.messaging.contacts.processing;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.common.exception.ConflictException;
import com.messaging.contacts.processing.ContactImportMutationPort.DuplicateStrategy;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactImportCommitCoordinator {

  private final ContactImportRepository repository;
  private final ContactStorageAdapter storage;
  private final AsyncJobService asyncJobs;

  @Transactional
  public ContactImportService.CommitResult queue(
      long companyId,
      long userId,
      long importId,
      String checksum,
      String mappingJson,
      String mappingHash,
      DuplicateStrategy duplicateStrategy,
      Long targetListId,
      String idempotencyKey,
      long actualSizeBytes) {
    repository.lockIdempotency(companyId, idempotencyKey);
    var replay = repository.findByIdempotency(companyId, idempotencyKey);
    if (replay.isPresent()) {
      ContactImportRepository.ImportRow existing = replay.get();
      if (!Objects.equals(existing.contentSha256(), checksum)
          || !Objects.equals(existing.mappingHash(), mappingHash)) {
        throw new ConflictException("CONTACT_IMPORT_IDEMPOTENCY_CONFLICT");
      }
      return new ContactImportService.CommitResult(existing, null);
    }

    ContactImportRepository.ImportRow current = repository.require(companyId, importId);
    AsyncJobResponse job =
        asyncJobs.create(
            companyId,
            userId,
            AsyncJobType.CONTACT_IMPORT,
            Long.toString(importId),
            null,
            "Contact import queued");
    ContactImportRepository.QueueResult queued =
        repository.queue(
            companyId,
            importId,
            checksum,
            mappingJson,
            mappingHash,
            duplicateStrategy.name(),
            targetListId,
            idempotencyKey,
            Long.parseLong(job.jobId()));
    if (queued.newlyQueued()) {
      storage.commitImportReservation(
          companyId, importId, current.expectedSizeBytes(), actualSizeBytes);
    }
    return new ContactImportService.CommitResult(queued.row(), job);
  }
}
