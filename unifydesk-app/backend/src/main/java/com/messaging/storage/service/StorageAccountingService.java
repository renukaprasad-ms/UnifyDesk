package com.messaging.storage.service;

import com.messaging.common.id.IdGenerator;
import com.messaging.storage.enums.StorageEventType;
import com.messaging.storage.enums.StorageUsageType;
import com.messaging.storage.repository.StorageAccountingRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StorageAccountingService {

  private final StorageAccountingRepository repository;

  @Transactional
  public boolean record(
      long companyId,
      StorageUsageType usageType,
      String resourceType,
      String resourceId,
      StorageEventType eventType,
      long usedBytesDelta,
      long reservedBytesDelta,
      String idempotencyKey) {
    boolean inserted =
        repository.appendEvent(
            IdGenerator.nextId(),
            companyId,
            usageType,
            resourceType,
            resourceId,
            eventType,
            usedBytesDelta,
            reservedBytesDelta,
            idempotencyKey,
            Instant.now());

    if (inserted) {
      repository.applyDelta(companyId, usageType, usedBytesDelta, reservedBytesDelta);
    }
    return inserted;
  }

  @Transactional
  public void commitReservation(
      long companyId,
      StorageUsageType usageType,
      String resourceType,
      String resourceId,
      long bytes) {
    record(
        companyId,
        usageType,
        resourceType,
        resourceId,
        StorageEventType.COMMIT,
        bytes,
        -bytes,
        key(resourceType, resourceId, "commit"));
  }

  @Transactional
  public void releaseReservation(
      long companyId,
      StorageUsageType usageType,
      String resourceType,
      String resourceId,
      long bytes) {
    record(
        companyId,
        usageType,
        resourceType,
        resourceId,
        StorageEventType.RELEASE_RESERVED,
        0,
        -bytes,
        key(resourceType, resourceId, "release-reserved"));
  }

  @Transactional
  public void releaseUsage(
      long companyId,
      StorageUsageType usageType,
      String resourceType,
      String resourceId,
      long bytes) {
    record(
        companyId,
        usageType,
        resourceType,
        resourceId,
        StorageEventType.RELEASE_USED,
        -bytes,
        0,
        key(resourceType, resourceId, "release-used"));
  }

  @Transactional
  public void reconcile(
      long companyId, StorageUsageType usageType, long usedBytes, long reservedBytes) {
    repository.lockCompany(companyId);
    var current = repository.bucket(companyId, usageType);
    if (current.usedBytes() == usedBytes && current.reservedBytes() == reservedBytes) {
      return;
    }

    long eventId = IdGenerator.nextId();
    repository.appendEvent(
        eventId,
        companyId,
        usageType,
        "RECONCILIATION",
        usageType.name(),
        StorageEventType.RECONCILE,
        usedBytes - current.usedBytes(),
        reservedBytes - current.reservedBytes(),
        "reconcile:" + companyId + ":" + usageType + ":" + eventId,
        Instant.now());
    repository.replaceBucket(companyId, usageType, usedBytes, reservedBytes);
  }

  private String key(String resourceType, String resourceId, String action) {
    return resourceType + ":" + resourceId + ":" + action;
  }
}
