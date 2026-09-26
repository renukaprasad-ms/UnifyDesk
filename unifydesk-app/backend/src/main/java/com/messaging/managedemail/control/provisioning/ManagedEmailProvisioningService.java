package com.messaging.managedemail.control.provisioning;

import com.messaging.managedemail.control.entity.ManagedEmailProvisioningOperation;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.*;
import com.messaging.managedemail.control.repository.ManagedEmailProvisioningOperationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedEmailProvisioningService {

  private final ManagedEmailProvisioningOperationRepository operationRepository;

  public String requestKey(String purpose, Long companyId, String clientKey) {
    if (clientKey == null || clientKey.isBlank()) {
      throw new IllegalArgumentException("Idempotency key is required");
    }
    return purpose + ":" + companyId + ":" + sha256(clientKey.trim());
  }

  public Optional<ManagedEmailProvisioningOperation> find(String key) {
    return operationRepository.findByIdempotencyKey(key);
  }

  public ManagedEmailProvisioningOperation enqueue(
      Long companyId,
      ProvisioningResourceType resourceType,
      Long resourceId,
      ProvisioningOperationType operationType,
      String idempotencyKey) {
    return operationRepository
        .findByIdempotencyKey(idempotencyKey)
        .orElseGet(
            () -> {
              ManagedEmailProvisioningOperation operation =
                  new ManagedEmailProvisioningOperation();
              operation.setCompanyId(companyId);
              operation.setResourceType(resourceType);
              operation.setResourceId(resourceId);
              operation.setOperationType(operationType);
              operation.setIdempotencyKey(idempotencyKey);
              operation.setStatus(ProvisioningOperationStatus.PENDING);
              operation.setNextAttemptAt(Instant.now());
              return operationRepository.save(operation);
            });
  }

  public ManagedEmailProvisioningOperation enqueueOrReset(
      Long companyId,
      ProvisioningResourceType resourceType,
      Long resourceId,
      ProvisioningOperationType operationType,
      String idempotencyKey) {
    ManagedEmailProvisioningOperation operation =
        operationRepository
            .findByIdempotencyKey(idempotencyKey)
            .orElseGet(ManagedEmailProvisioningOperation::new);
    if (operation.getId() == null) {
      operation.setCompanyId(companyId);
      operation.setResourceType(resourceType);
      operation.setResourceId(resourceId);
      operation.setOperationType(operationType);
      operation.setIdempotencyKey(idempotencyKey);
      operation.setAttemptCount(0);
    }
    if (operation.getStatus() != ProvisioningOperationStatus.PROCESSING) {
      operation.setStatus(ProvisioningOperationStatus.PENDING);
      operation.setNextAttemptAt(Instant.now());
      operation.setClaimedAt(null);
      operation.setCompletedAt(null);
      operation.setLastErrorCode(null);
      operation.setLastErrorMessage(null);
    }
    return operationRepository.save(operation);
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash, 0, 16);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to create idempotency hash", exception);
    }
  }
}
