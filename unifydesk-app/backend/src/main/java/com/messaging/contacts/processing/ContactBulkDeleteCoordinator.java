package com.messaging.contacts.processing;

import com.messaging.contacts.core.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ContactBulkDeleteCoordinator {

  private final ContactService contactService;
  private final ContactBulkRepository repository;
  private final PlatformTransactionManager transactionManager;

  public ContactBulkMutationPort.BatchResult deleteAndCheckpoint(
      long companyId,
      long operationId,
      long requestedByUserId,
      long expectedCursor,
      long contactId) {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    try {
      transaction.executeWithoutResult(
          status -> {
            contactService.delete(companyId, contactId, requestedByUserId);
            repository.advance(companyId, operationId, expectedCursor, contactId, 1, 1, 0);
          });
      return new ContactBulkMutationPort.BatchResult(1, 0);
    } catch (RuntimeException exception) {
      repository.advance(companyId, operationId, expectedCursor, contactId, 1, 0, 1);
      return new ContactBulkMutationPort.BatchResult(0, 1);
    }
  }
}
