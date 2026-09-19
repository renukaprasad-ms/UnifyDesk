package com.messaging.storage.service;

import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.service.EntitlementService;
import com.messaging.media.enums.MediaStatus;
import com.messaging.media.repository.MediaRepository;
import com.messaging.storage.dto.StorageUsageResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StorageQuotaService {

  public static final String STORAGE_ENTITLEMENT = "STORAGE_GB";
  private static final BigDecimal BYTES_PER_GIB = BigDecimal.valueOf(1024L * 1024L * 1024L);

  private final MediaRepository mediaRepository;
  private final EntitlementService entitlementService;

  @Transactional(readOnly = true)
  public void assertCanReserve(Long companyId, long requestedBytes) {
    StorageUsageResponse usage = usage(companyId);
    if (usage.limitBytes() >= 0
        && requestedBytes > Math.max(0, usage.limitBytes() - usage.reservedBytes())) {
      throw new PlanEntitlementException(
          "Workspace storage limit reached (limit=" + usage.limitBytes() + " bytes)");
    }
  }

  @Transactional(readOnly = true)
  public StorageUsageResponse usage(Long companyId) {
    long used =
        mediaRepository.sumSizeBytesByCompanyAndStatuses(
            companyId, List.of(MediaStatus.ACTIVE));
    long reserved =
        mediaRepository.sumSizeBytesByCompanyAndStatuses(
            companyId, List.of(MediaStatus.ACTIVE, MediaStatus.PENDING));
    long limit = limitBytes(companyId);
    long remaining = limit < 0 ? -1 : Math.max(0, limit - reserved);
    double percent =
        limit <= 0
            ? 0
            : BigDecimal.valueOf(reserved)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(limit), 2, RoundingMode.HALF_UP)
                .doubleValue();

    return new StorageUsageResponse(
        companyId.toString(), used, reserved, limit, remaining, Math.min(percent, 100));
  }

  private long limitBytes(Long companyId) {
    BigDecimal gigabytes = entitlementService.decimalValue(companyId, STORAGE_ENTITLEMENT);
    if (gigabytes.signum() < 0) {
      return -1;
    }
    return gigabytes.multiply(BYTES_PER_GIB).longValue();
  }
}
