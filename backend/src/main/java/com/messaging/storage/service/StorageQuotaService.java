package com.messaging.storage.service;

import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.service.EntitlementService;
import com.messaging.storage.dto.StorageUsageResponse;
import com.messaging.storage.enums.StorageEventType;
import com.messaging.storage.enums.StorageUsageType;
import com.messaging.storage.repository.StorageAccountingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

  private long safeAdd(long left, long right) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException exception) {
      return Long.MAX_VALUE;
    }
  }
}
