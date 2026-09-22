package com.messaging.delivery.service;

import com.messaging.delivery.entity.EmailDeliveryReport;
import com.messaging.delivery.model.DetectedDeliveryReport;
import com.messaging.delivery.repository.EmailDeliveryReportRepository;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.service.InboxMessagePostProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailDeliveryReportCaptureService implements InboxMessagePostProcessor {

  private final EmailDeliveryReportParser parser;
  private final EmailDeliveryReportRepository repository;

  @Override
  public void afterPersist(InboxMessage message) {
    if (message == null || message.getId() == null) return;
    if (repository.findBySourceInboxMessageId(message.getId()).isPresent()) return;

    parser
        .parse(message)
        .ifPresent(
            detected -> {
              EmailDeliveryReport report = toEntity(message, detected);
              repository.save(report);
            });
  }

  private EmailDeliveryReport toEntity(
      InboxMessage message, DetectedDeliveryReport detected) {
    EmailDeliveryReport report = new EmailDeliveryReport();
    report.setCompanyId(message.getConnection().getCompanyId());
    report.setConnectionId(message.getConnection().getId());
    report.setSourceInboxMessageId(message.getId());
    report.setFailedRecipient(detected.failedRecipient());
    report.setNormalizedFailedRecipient(detected.normalizedFailedRecipient());
    report.setOriginalMessageId(detected.originalMessageId());
    report.setProviderCorrelationId(detected.providerCorrelationId());
    report.setStatusCode(detected.statusCode());
    report.setDiagnosticMessage(detected.diagnosticMessage());
    report.setBounceType(detected.bounceType());
    return report;
  }
}
