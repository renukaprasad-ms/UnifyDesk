package com.messaging.managedemail.control.scheduler;

import com.messaging.managedemail.control.config.ManagedEmailProperties;
import com.messaging.managedemail.control.provisioning.ManagedEmailProvisioningWorker;
import com.messaging.managedemail.control.service.ManagedEmailDomainService;
import com.messaging.managedemail.control.service.ManagedEmailTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ManagedEmailSchedulers {

  private final ManagedEmailProperties properties;
  private final ManagedEmailProvisioningWorker provisioningWorker;
  private final ManagedEmailDomainService domainService;
  private final ManagedEmailTenantService tenantService;

  @Scheduled(
      fixedDelayString = "${app.managed-email.provisioning-poll-interval:2s}",
      initialDelayString = "${app.managed-email.provisioning-poll-interval:2s}")
  public void provision() {
    if (!properties.isEnabled()) return;
    for (int i = 0; i < 20 && provisioningWorker.runOnce(); i++) {
      // Drain a bounded amount per scheduler tick.
    }
  }

  @Scheduled(
      fixedDelayString = "${app.managed-email.verification-refresh-interval:5m}",
      initialDelayString = "${app.managed-email.verification-refresh-interval:5m}")
  public void refreshVerification() {
    if (!properties.isEnabled()) return;
    tenantService.queueProviderRefreshes();
    domainService.queueVerificationRefreshes();
  }
}
