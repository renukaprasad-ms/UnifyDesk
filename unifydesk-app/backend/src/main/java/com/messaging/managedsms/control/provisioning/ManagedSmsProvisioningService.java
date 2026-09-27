package com.messaging.managedsms.control.provisioning;

import com.messaging.managedsms.control.entity.ManagedSmsProvisioningOperation;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.repository.ManagedSmsProvisioningOperationRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedSmsProvisioningService {

  private final ManagedSmsProvisioningOperationRepository repository;

  public ManagedSmsProvisioningOperation enqueue(
      Long companyId,
      Long tenantId,
      ProvisioningResourceType resourceType,
      Long resourceId,
      ProvisioningOperationType operationType,
      String idempotencyKey) {
    return repository
        .findByIdempotencyKey(idempotencyKey)
        .orElseGet(
            () -> {
              ManagedSmsProvisioningOperation operation = new ManagedSmsProvisioningOperation();
              operation.setCompanyId(companyId);
              operation.setTenantId(tenantId);
              operation.setResourceType(resourceType);
              operation.setResourceId(resourceId);
              operation.setOperationType(operationType);
              operation.setIdempotencyKey(idempotencyKey);
              operation.setStatus(ProvisioningOperationStatus.PENDING);
              operation.setNextAttemptAt(Instant.now());
              return repository.save(operation);
            });
  }

  public ManagedSmsProvisioningOperation enqueueOrReset(
      Long companyId,
      Long tenantId,
      ProvisioningResourceType resourceType,
      Long resourceId,
      ProvisioningOperationType operationType,
      String idempotencyKey) {
    ManagedSmsProvisioningOperation operation =
        repository.findByIdempotencyKey(idempotencyKey).orElseGet(ManagedSmsProvisioningOperation::new);
    if (operation.getId() == null) {
      operation.setCompanyId(companyId);
      operation.setTenantId(tenantId);
      operation.setResourceType(resourceType);
      operation.setResourceId(resourceId);
      operation.setOperationType(operationType);
      operation.setIdempotencyKey(idempotencyKey);
    }
    if (operation.getStatus() != ProvisioningOperationStatus.RUNNING) {
      operation.setStatus(ProvisioningOperationStatus.PENDING);
      operation.setNextAttemptAt(Instant.now());
      operation.setLeaseUntil(null);
      operation.setLastErrorCode(null);
      operation.setLastErrorMessage(null);
    }
    return repository.save(operation);
  }
}
