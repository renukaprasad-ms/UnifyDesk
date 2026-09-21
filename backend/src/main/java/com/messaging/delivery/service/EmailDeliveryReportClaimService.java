package com.messaging.delivery.service;

import com.messaging.delivery.config.EmailDeliveryProperties;
import com.messaging.delivery.entity.EmailDeliveryReport;
import com.messaging.delivery.repository.EmailDeliveryReportRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailDeliveryReportClaimService {

  private final EmailDeliveryReportRepository repository;
  private final EmailDeliveryProperties properties;

  @Transactional
  public List<Long> claim() {
    Instant now = Instant.now();
    List<EmailDeliveryReport> reports =
        repository.findClaimableForUpdate(
            now,
            now.minus(properties.getDeliveryReportStaleAfter()),
            Math.max(1, properties.getDeliveryReportClaimSize()));
    for (EmailDeliveryReport report : reports) {
      report.setClaimedAt(now);
      report.setAttemptCount(report.getAttemptCount() + 1);
      report.setLastError(null);
    }
    repository.saveAll(reports);
    return reports.stream().map(EmailDeliveryReport::getId).toList();
  }
}
