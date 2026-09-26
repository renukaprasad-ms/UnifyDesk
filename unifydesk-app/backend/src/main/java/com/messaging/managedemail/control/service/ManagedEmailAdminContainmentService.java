package com.messaging.managedemail.control.service;

import com.messaging.admin.enums.PlatformPermission;
import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderConnectionStatus;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.common.exception.NotFoundException;
import com.messaging.managedemail.control.dto.ManagedEmailAdminDtos.ContainmentResponse;
import com.messaging.managedemail.control.entity.ManagedEmailAdminAction;
import com.messaging.managedemail.control.entity.ManagedEmailTenant;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.AdminActionType;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.ProvisioningOperationType;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.ProvisioningResourceType;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.SendingStatus;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.TenantStatus;
import com.messaging.managedemail.control.provisioning.ManagedEmailProvisioningService;
import com.messaging.managedemail.control.repository.ManagedEmailAdminActionRepository;
import com.messaging.managedemail.control.repository.ManagedEmailTenantRepository;
import com.messaging.security.service.CurrentUserService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedEmailAdminContainmentService {

  private final ManagedEmailTenantRepository tenantRepository;
  private final BroadcastSenderConnectionRepository connectionRepository;
  private final ManagedEmailAdminActionRepository actionRepository;
  private final ManagedEmailProvisioningService provisioningService;
  private final CurrentUserService currentUserService;
  private final PlatformAdminAuthorizationService authorizationService;

  @Transactional
  public ContainmentResponse suspend(Long companyId, String reason) {
    Long actor = requirePrivileged(companyId);
    ManagedEmailTenant tenant = requireTenant(companyId);
    Instant now = Instant.now();
    tenant.setStatus(TenantStatus.SUSPENDED);
    tenant.setSendingStatus(SendingStatus.DISABLED);
    tenant.setSuspensionReason("ADMIN");
    tenant.setSuspendedAt(now);
    tenantRepository.save(tenant);

    BroadcastSenderConnection connection = findManagedConnection(tenant);
    if (connection != null) {
      connection.setSendingEnabled(false);
      connectionRepository.save(connection);
    }
    record(actor, companyId, AdminActionType.SUSPEND, reason, now);
    return response(companyId, tenant, connection, now);
  }

  @Transactional
  public ContainmentResponse resume(Long companyId, String reason) {
    Long actor = requirePrivileged(companyId);
    ManagedEmailTenant tenant = requireTenant(companyId);
    Instant now = Instant.now();
    tenant.setStatus(TenantStatus.PROVISIONING);
    tenant.setSendingStatus(SendingStatus.DISABLED);
    tenant.setSuspensionReason(null);
    tenant.setSuspendedAt(null);
    tenantRepository.save(tenant);

    BroadcastSenderConnection connection = findManagedConnection(tenant);
    if (connection != null) {
      connection.setSendingEnabled(false);
      connectionRepository.save(connection);
    }
    provisioningService.enqueueOrReset(
        companyId,
        ProvisioningResourceType.TENANT,
        tenant.getId(),
        ProvisioningOperationType.ENSURE_TENANT,
        "tenant:ensure:" + companyId);
    record(actor, companyId, AdminActionType.RESUME, reason, now);
    return response(companyId, tenant, connection, now);
  }

  private Long requirePrivileged(Long companyId) {
    Long actor = currentUserService.currentUserId();
    authorizationService.requirePermission(actor, PlatformPermission.COMPANY_SUSPEND);
    authorizationService.requireCompanyAccess(actor, companyId);
    return actor;
  }

  private ManagedEmailTenant requireTenant(Long companyId) {
    return tenantRepository
        .findByCompanyId(companyId)
        .orElseThrow(() -> new NotFoundException("Managed Email is not configured for company"));
  }

  private BroadcastSenderConnection findManagedConnection(ManagedEmailTenant tenant) {
    if (tenant.getBroadcastConnectionId() != null) {
      BroadcastSenderConnection connection =
          connectionRepository
              .findByIdAndCompanyId(tenant.getBroadcastConnectionId(), tenant.getCompanyId())
              .orElse(null);
      if (connection != null) return connection;
    }
    return connectionRepository
        .findFirstByCompanyIdAndProviderAndManagementModeAndStatusNot(
            tenant.getCompanyId(),
            BroadcastSenderProvider.AMAZON_SES,
            BroadcastSenderManagementMode.MESSAGING_MANAGED,
            BroadcastSenderConnectionStatus.DISABLED)
        .orElse(null);
  }

  private void record(
      Long actor, Long companyId, AdminActionType action, String reason, Instant timestamp) {
    ManagedEmailAdminAction audit = new ManagedEmailAdminAction();
    audit.setCompanyId(companyId);
    audit.setActorUserId(actor);
    audit.setAction(action);
    audit.setReason(reason.trim());
    audit.setCreatedAt(timestamp);
    actionRepository.save(audit);
  }

  private ContainmentResponse response(
      Long companyId,
      ManagedEmailTenant tenant,
      BroadcastSenderConnection connection,
      Instant timestamp) {
    return new ContainmentResponse(
        companyId.toString(),
        tenant.getStatus(),
        tenant.getSendingStatus(),
        connection != null && connection.isSendingEnabled(),
        timestamp);
  }
}
