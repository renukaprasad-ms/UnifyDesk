package com.messaging.storage.service;

import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyRepository;
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

  private final CompanyRepository companyRepository;
  private final StorageReconciliationService reconciliationService;

  @Value("${app.storage.reconciliation.batch-size:100}")
  private int batchSize;

  @Scheduled(
      cron = "${app.storage.reconciliation.cron:0 30 3 * * *}",
      zone = "${app.storage.reconciliation.zone:UTC}")
  public void reconcile() {
    int page = 0;
    int reconciled = 0;

    while (true) {
      var companies =
          companyRepository.findByStatusNot(
              CompanyStatus.DELETED, PageRequest.of(page, Math.max(1, batchSize)));

      for (var company : companies.getContent()) {
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

      if (!companies.hasNext()) {
        break;
      }
      page++;
    }

    log.info("Storage reconciliation completed companies={}", reconciled);
  }
}
