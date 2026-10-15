package com.messaging.managedemail.control.service;

import com.messaging.broadcast.enums.BroadcastSenderStatus;
import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.service.EntitlementService;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.DomainStatus;
import com.messaging.managedemail.control.repository.ManagedEmailDomainRepository;
import com.messaging.managedemail.control.repository.ManagedEmailSenderBindingRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedEmailEntitlementService {

  public static final String ENABLED = "MANAGED_EMAIL_ENABLED";
  public static final String DOMAINS = "MANAGED_EMAIL_DOMAINS";
  public static final String SENDERS = "MANAGED_EMAIL_SENDERS";

  private final EntitlementService entitlementService;
  private final ManagedEmailDomainRepository domainRepository;
  private final ManagedEmailSenderBindingRepository senderBindingRepository;

  public void requireEnabled(Long companyId) {
    if (!entitlementService.booleanValue(companyId, ENABLED)) {
      throw new PlanEntitlementException("UnifyDesk Managed Email is not enabled for this plan");
    }
  }

  public void requireDomainCapacity(Long companyId) {
    long usage =
        domainRepository.countByCompanyIdAndStatusNotIn(
            companyId, Set.of(DomainStatus.DELETED, DomainStatus.RETIRED, DomainStatus.REJECTED));
    entitlementService.assertWithinLimit(companyId, DOMAINS, usage);
  }

  public void requireSenderCapacity(Long companyId) {
    entitlementService.assertWithinLimit(
        companyId, SENDERS, senderBindingRepository.countByCompanyIdAndSenderStatus(
            companyId, BroadcastSenderStatus.ACTIVE));
  }

  public boolean enabled(Long companyId) {
    return entitlementService.booleanValue(companyId, ENABLED);
  }

  public long domainLimit(Long companyId) {
    return entitlementService.integerValue(companyId, DOMAINS);
  }

  public long senderLimit(Long companyId) {
    return entitlementService.integerValue(companyId, SENDERS);
  }
}
