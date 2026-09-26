package com.messaging.managedemail.control.service;

import com.messaging.managedemail.control.config.ManagedEmailProperties;
import com.messaging.managedemail.control.dto.ManagedEmailDtos.OverviewResponse;
import com.messaging.managedemail.control.entity.ManagedEmailTenant;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.SendingStatus;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.TenantStatus;
import com.messaging.managedemail.control.repository.ManagedEmailTenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedEmailOverviewService {

  private final ManagedEmailProperties properties;
  private final ManagedEmailEntitlementService entitlementService;
  private final ManagedEmailTenantRepository tenantRepository;

  @Transactional(readOnly = true)
  public OverviewResponse get(Long companyId) {
    boolean planEnabled = entitlementService.enabled(companyId);
    boolean enabled = properties.isEnabled() && planEnabled;
    ManagedEmailTenant tenant = tenantRepository.findByCompanyId(companyId).orElse(null);

    String serviceStatus;
    SendingStatus sendingStatus;
    if (!enabled) {
      serviceStatus = "DISABLED";
      sendingStatus = SendingStatus.DISABLED;
    } else if (tenant == null) {
      serviceStatus = "NOT_CONFIGURED";
      sendingStatus = SendingStatus.DISABLED;
    } else {
      serviceStatus = mapStatus(tenant.getStatus());
      sendingStatus =
          properties.isPlatformSendingEnabled()
              ? tenant.getSendingStatus()
              : SendingStatus.DISABLED;
    }

    return new OverviewResponse(
        enabled,
        serviceStatus,
        sendingStatus,
        "Managed by UnifyDesk",
        entitlementService.domainLimit(companyId),
        entitlementService.senderLimit(companyId),
        null);
  }

  private String mapStatus(TenantStatus status) {
    return switch (status) {
      case PROVISIONING -> "PROVISIONING";
      case ACTIVE -> "ACTIVE";
      case SUSPENDED -> "SUSPENDED";
      case ERROR -> "ERROR";
      case DELETING, DELETED -> "DISABLED";
    };
  }
}
