package com.messaging.managedsms.control.service;

import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.*;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.id.IdGenerator;
import com.messaging.managedsms.control.config.ManagedSmsProperties;
import com.messaging.managedsms.control.entity.ManagedSmsTenant;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.provisioning.ManagedSmsProvisioningService;
import com.messaging.managedsms.control.repository.ManagedSmsTenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsTenantService {

  private static final long CONNECTION_LOCK_NAMESPACE = 0x534d53434f4e4e00L;

  private final ManagedSmsTenantRepository tenantRepository;
  private final BroadcastSenderConnectionRepository connectionRepository;
  private final ChannelConnectionRepository channelConnectionRepository;
  private final ManagedSmsProvisioningService provisioningService;
  private final ManagedSmsProperties properties;
  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public ManagedSmsTenant ensureTenant(Long companyId, Long userId) {
    advisoryLock(CONNECTION_LOCK_NAMESPACE ^ companyId);
    return tenantRepository.findByCompanyId(companyId).orElseGet(() -> create(companyId, userId));
  }

  @Transactional
  public void updateSendingState(ManagedSmsTenant tenant, boolean ready) {
    boolean enabled =
        ready
            && properties.isEnabled()
            && properties.isPlatformSendingEnabled()
            && tenant.getStatus() != TenantStatus.SUSPENDED;
    tenant.setStatus(
        tenant.getStatus() == TenantStatus.SUSPENDED
            ? TenantStatus.SUSPENDED
            : ready ? TenantStatus.READY : TenantStatus.PROVIDER_SYNCING);
    tenant.setSendingEnabled(enabled);
    tenantRepository.save(tenant);

    connectionRepository
        .findByIdAndCompanyId(tenant.getBroadcastConnectionId(), tenant.getCompanyId())
        .ifPresent(
            connection -> {
              connection.setSendingEnabled(enabled);
              connection.setStatus(
                  tenant.getStatus() == TenantStatus.SUSPENDED
                      ? BroadcastSenderConnectionStatus.DISABLED
                      : BroadcastSenderConnectionStatus.ACTIVE);
              connection.setLastValidatedAt(tenant.getLastProviderSyncAt());
              connectionRepository.save(connection);
            });
  }

  private ManagedSmsTenant create(Long companyId, Long userId) {
    BroadcastSenderConnection connection =
        connectionRepository
            .findFirstByCompanyIdAndProviderAndManagementModeAndStatusNot(
                companyId,
                BroadcastSenderProvider.MSG91_SMS,
                BroadcastSenderManagementMode.MESSAGING_MANAGED,
                BroadcastSenderConnectionStatus.DISABLED)
            .orElseGet(() -> createConnection(companyId, userId));

    ManagedSmsTenant tenant = new ManagedSmsTenant();
    tenant.setCompanyId(companyId);
    tenant.setBroadcastConnectionId(connection.getId());
    tenant.setStatus(TenantStatus.ONBOARDING);
    tenant.setProviderMode(properties.getProviderMode());
    tenant.setSendingEnabled(false);
    ManagedSmsTenant saved = tenantRepository.save(tenant);
    provisioningService.enqueue(
        companyId,
        saved.getId(),
        ProvisioningResourceType.TENANT,
        saved.getId(),
        ProvisioningOperationType.ENSURE_TENANT,
        "managed-sms:tenant:ensure:" + companyId);
    return saved;
  }

  private BroadcastSenderConnection createConnection(Long companyId, Long userId) {
    Long id = IdGenerator.nextId();
    ChannelConnection channel = new ChannelConnection();
    channel.setId(id);
    channel.setCompanyId(companyId);
    channel.setChannelType(ChannelType.SMS);
    channel.setProviderType(ProviderType.MSG91_SMS);
    channel.setName("UnifyDesk Managed SMS");
    channel.setExternalAccountId("managed-sms-" + companyId);
    channel.setStatus(ChannelConnectionStatus.ACTIVE);
    channel.setCreatedByUserId(userId);
    channelConnectionRepository.save(channel);

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(id);
    connection.setChannelConnectionId(id);
    connection.setCompanyId(companyId);
    connection.setProvider(BroadcastSenderProvider.MSG91_SMS);
    connection.setManagementMode(BroadcastSenderManagementMode.MESSAGING_MANAGED);
    connection.setAuthMode(BroadcastSenderAuthMode.API_KEY);
    connection.setName("UnifyDesk Managed SMS");
    connection.setRegion("MANAGED");
    connection.setEncryptedCredentials(null);
    connection.setProviderConfigJson(null);
    connection.setRoleArn(null);
    connection.setExternalId(null);
    connection.setStatus(BroadcastSenderConnectionStatus.ACTIVE);
    connection.setProductionAccessEnabled(false);
    connection.setSendingEnabled(false);
    connection.setCreatedByUserId(userId);
    return connectionRepository.save(connection);
  }

  private void advisoryLock(long key) {
    jdbcTemplate.queryForObject("select pg_advisory_xact_lock(?)::text", String.class, key);
  }
}
