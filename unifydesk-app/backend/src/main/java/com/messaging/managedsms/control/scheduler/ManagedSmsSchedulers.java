package com.messaging.managedsms.control.scheduler;

import com.messaging.managedsms.control.config.ManagedSmsProperties;
import com.messaging.managedsms.control.provisioning.ManagedSmsProvisioningWorker;
import com.messaging.managedsms.control.service.ManagedSmsComplianceService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ManagedSmsSchedulers {

  private final ManagedSmsProperties properties;
  private final ManagedSmsProvisioningWorker worker;
  private final ManagedSmsComplianceService complianceService;

  @Scheduled(
      fixedDelayString = "${app.managed-sms.provisioning-poll-interval:5s}",
      initialDelayString = "${app.managed-sms.provisioning-poll-interval:5s}")
  public void provision() {
    if (!properties.isEnabled()) return;
    for (int i = 0; i < 20 && worker.runOnce(); i++) {
      // Bounded drain per scheduler tick.
    }
  }

  @Scheduled(
      fixedDelayString = "${app.managed-sms.compliance-refresh-interval:5m}",
      initialDelayString = "${app.managed-sms.compliance-refresh-interval:5m}")
  public void refreshCompliance() {
    if (properties.isEnabled()) complianceService.refreshStaleState();
  }
}
