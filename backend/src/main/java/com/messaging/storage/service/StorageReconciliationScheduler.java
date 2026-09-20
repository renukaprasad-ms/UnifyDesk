package com.messaging.storage.service;

import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageReconciliationScheduler {

  private static final Set<CompanyStatus> RECONCILABLE_STATUSES =
      Set.of(
          CompanyStatus.ACTIVE,
          CompanyStatus.SUSPENDED,
          CompanyStatus.DELETION_PENDING);

  private final CompanyRepository companyRepository;
  private final StorageReconciliationService reconciliationService;

  @Value("${app.storage.reconciliation.batch-size:100}")
  private int batchSize;

  @Scheduled(
      cron = "${app.storage.reconciliation.cron:0 30 3 * * *}",
      zone = "${app.storage.reconciliation.zone:UTC}")
  public void reconcile() {
    long afterId = 0L;
    int reconciled = 0;
    int effectiveBatchSize = Math.max(1, batchSize);

    while (true) {
      var companies =
          companyRepository.findStorageReconciliationBatch(
              afterId, RECONCILABLE_STATUSES, PageRequest.of(0, effectiveBatchSize));

      if (companies.isEmpty()) {
        break;
      }

      for (var company : companies) {
        afterId = company.getId();
        try {
          reconciliationService.reconcileCompany(company.getId());
          reconciled++;
        } catch (RuntimeException exception) {
          log.error(
              "Storage reconciliation failed companyId={} reason={}",
              company.getId(),
              exception.getClass().getSimpleName(),
              exception);
        }
      }
    }

    log.info("Storage reconciliation completed companies={}", reconciled);
  }
}
