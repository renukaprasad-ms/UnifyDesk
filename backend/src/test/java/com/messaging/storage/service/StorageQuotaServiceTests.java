package com.messaging.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.service.EntitlementService;
import com.messaging.storage.enums.StorageEventType;
import com.messaging.storage.enums.StorageUsageType;
import com.messaging.storage.repository.StorageAccountingRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorageQuotaServiceTests {

  private static final long GIB = 1024L * 1024L * 1024L;

  @Mock private StorageAccountingRepository repository;
  @Mock private StorageAccountingService accountingService;
  @Mock private EntitlementService entitlementService;

  @Test
  void reserveRejectsWhenCompanyWouldExceedPlanLimit() {
    StorageQuotaService service = service();
    when(entitlementService.decimalValue(10L, StorageQuotaService.STORAGE_ENTITLEMENT))
        .thenReturn(BigDecimal.ONE);
    when(repository.totals(10L))
        .thenReturn(new StorageAccountingRepository.UsageTotals(GIB - 50, 0));
    when(repository.hasEvent("MEDIA:20:reserve")).thenReturn(false);

    assertThatThrownBy(
            () -> service.reserve(10L, StorageUsageType.EMAIL_ATTACHMENT, "MEDIA", "20", 100))
        .isInstanceOf(PlanEntitlementException.class);

    verify(accountingService, never())
        .record(
            10L,
            StorageUsageType.EMAIL_ATTACHMENT,
            "MEDIA",
            "20",
            StorageEventType.RESERVE,
            0,
            100,
            "MEDIA:20:reserve");
  }

  @Test
  void duplicateReservationIsIdempotentAndDoesNotConsumeQuotaAgain() {
    StorageQuotaService service = service();
    when(repository.hasEvent("MEDIA:20:reserve")).thenReturn(true);

    boolean created = service.reserve(10L, StorageUsageType.EMAIL_ATTACHMENT, "MEDIA", "20", 100);

    assertThat(created).isFalse();
    verify(entitlementService, never()).decimalValue(10L, StorageQuotaService.STORAGE_ENTITLEMENT);
    verify(accountingService, never())
        .record(
            10L,
            StorageUsageType.EMAIL_ATTACHMENT,
            "MEDIA",
            "20",
            StorageEventType.RESERVE,
            0,
            100,
            "MEDIA:20:reserve");
  }

  @Test
  void usageSeparatesCommittedAndReservedBytes() {
    StorageQuotaService service = service();
    when(entitlementService.decimalValue(10L, StorageQuotaService.STORAGE_ENTITLEMENT))
        .thenReturn(BigDecimal.ONE);
    when(repository.totals(10L)).thenReturn(new StorageAccountingRepository.UsageTotals(200, 100));
    when(repository.breakdown(10L)).thenReturn(List.of());

    var usage = service.usage(10L);

    assertThat(usage.usedBytes()).isEqualTo(200);
    assertThat(usage.reservedBytes()).isEqualTo(100);
    assertThat(usage.remainingBytes()).isEqualTo(GIB - 300);
    assertThat(usage.breakdown()).isEmpty();
  }

  private StorageQuotaService service() {
    return new StorageQuotaService(repository, accountingService, entitlementService);
  }
}
