package com.messaging.storage.service;

import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.service.EntitlementService;
import com.messaging.common.id.IdGenerator;
import com.messaging.storage.dto.StorageUsageResponse;
import com.messaging.storage.enums.StorageEventType;
import com.messaging.storage.enums.StorageUsageType;
import com.messaging.storage.repository.StorageAccountingRepository;
import com.messaging.storage.repository.StorageAccountingRepository.StorageEventInsert;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StorageQuotaService {

  public static final String STORAGE_ENTITLEMENT = "STORAGE_GB";
  private static final BigDecimal BYTES_PER_GIB = BigDecimal.valueOf(1024L * 1024L * 1024L);

  private final StorageAccountingRepository accountingRepository;
  private final StorageAccountingService accountingService;
  private final EntitlementService entitlementService;

  @Transactional(readOnly = true)
  public void assertCanReserve(Long companyId, long requestedBytes) {
    assertAvailable(companyId, requestedBytes, accountingRepository.totals(companyId));
  }

  @Transactional
  public boolean reserve(
      long companyId,
      StorageUsageType usageType,
      String resourceType,
      String resourceId,
      long requestedBytes) {
    if (requestedBytes <= 0) {
      return false;
    }

    accountingRepository.lockCompany(companyId);
    String idempotencyKey = resourceType + ":" + resourceId + ":reserve";
    if (accountingRepository.hasEvent(idempotencyKey)) {
      return false;
    }
    assertAvailable(companyId, requestedBytes, accountingRepository.totals(companyId));

    return accountingService.record(
        companyId,
        usageType,
        resourceType,
        resourceId,
        StorageEventType.RESERVE,
        0,
        requestedBytes,
        idempotencyKey);
  }

  @Transactional
  public boolean consume(
      long companyId,
      StorageUsageType usageType,
      String resourceType,
      String resourceId,
      long requestedBytes) {
    if (requestedBytes <= 0) {
      return false;
    }

    accountingRepository.lockCompany(companyId);
    String idempotencyKey = resourceType + ":" + resourceId + ":add-used";
    if (accountingRepository.hasEvent(idempotencyKey)) {
      return false;
    }
    assertAvailable(companyId, requestedBytes, accountingRepository.totals(companyId));

    return accountingService.record(
        companyId,
        usageType,
        resourceType,
        resourceId,
        StorageEventType.ADD_USED,
        requestedBytes,
        0,
        idempotencyKey);
  }

  @Transactional
  public int consumeBatch(
      long companyId,
      StorageUsageType usageType,
      List<StorageConsumption> consumptions) {
    if (consumptions == null || consumptions.isEmpty()) {
      return 0;
    }

    Map<String, StorageConsumption> unique = new LinkedHashMap<>();
    for (StorageConsumption consumption : consumptions) {
      if (consumption == null
          || consumption.bytes() <= 0
          || consumption.resourceType() == null
          || consumption.resourceType().isBlank()
          || consumption.resourceId() == null
          || consumption.resourceId().isBlank()) {
        continue;
      }
      String key = consumption.resourceType() + ":" + consumption.resourceId() + ":add-used";
      unique.putIfAbsent(key, consumption);
    }

    if (unique.isEmpty()) {
      return 0;
    }

    accountingRepository.lockCompany(companyId);
    Set<String> existing = accountingRepository.existingEventKeys(List.copyOf(unique.keySet()));

    List<Map.Entry<String, StorageConsumption>> pending =
        unique.entrySet().stream().filter(entry -> !existing.contains(entry.getKey())).toList();
    if (pending.isEmpty()) {
      return 0;
    }

    long requestedBytes = 0;
    for (Map.Entry<String, StorageConsumption> entry : pending) {
      requestedBytes = safeAdd(requestedBytes, entry.getValue().bytes());
    }
    assertAvailable(companyId, requestedBytes, accountingRepository.totals(companyId));

    Instant now = Instant.now();
    List<StorageEventInsert> events = new ArrayList<>(pending.size());
    for (Map.Entry<String, StorageConsumption> entry : pending) {
      StorageConsumption consumption = entry.getValue();
      events.add(
          new StorageEventInsert(
              IdGenerator.nextId(),
              companyId,
              usageType,
              consumption.resourceType(),
              consumption.resourceId(),
              StorageEventType.ADD_USED,
              consumption.bytes(),
              0,
              entry.getKey(),
              now));
    }

    int[] results = accountingRepository.appendEvents(events);
    long appliedBytes = 0;
    int inserted = 0;
    for (int i = 0; i < results.length; i++) {
      if (results[i] > 0) {
        inserted++;
        appliedBytes = safeAdd(appliedBytes, pending.get(i).getValue().bytes());
      }
    }

    if (appliedBytes > 0) {
      accountingRepository.applyDelta(companyId, usageType, appliedBytes, 0);
    }
    return inserted;
  }

  @Transactional(readOnly = true)
  public StorageUsageResponse usage(Long companyId) {
    var totals = accountingRepository.totals(companyId);
    long used = totals.usedBytes();
    long reserved = totals.reservedBytes();
    long limit = limitBytes(companyId);
    long billable = safeAdd(used, reserved);
    long remaining = limit < 0 ? -1 : Math.max(0, limit - billable);
    double percent =
        limit <= 0
            ? 0
            : BigDecimal.valueOf(billable)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(limit), 2, RoundingMode.HALF_UP)
                .doubleValue();

    return new StorageUsageResponse(
        companyId.toString(),
        used,
        reserved,
        limit,
        remaining,
        Math.min(percent, 100),
        accountingRepository.breakdown(companyId));
  }

  private void assertAvailable(
      long companyId, long requestedBytes, StorageAccountingRepository.UsageTotals totals) {
    long limit = limitBytes(companyId);
    if (limit < 0) {
      return;
    }

    long allocated = safeAdd(totals.usedBytes(), totals.reservedBytes());
    long remaining = Math.max(0, limit - allocated);
    if (requestedBytes > remaining) {
      throw new PlanEntitlementException(
          "Workspace storage limit reached (remaining="
              + remaining
              + " bytes, requested="
              + requestedBytes
              + " bytes)");
    }
  }

  private long limitBytes(Long companyId) {
    BigDecimal gigabytes = entitlementService.decimalValue(companyId, STORAGE_ENTITLEMENT);
    if (gigabytes.signum() < 0) {
      return -1;
    }
    return gigabytes.multiply(BYTES_PER_GIB).longValue();
  }

  public record StorageConsumption(String resourceType, String resourceId, long bytes) {}

  private long safeAdd(long left, long right) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException exception) {
      return Long.MAX_VALUE;
    }
  }
}
