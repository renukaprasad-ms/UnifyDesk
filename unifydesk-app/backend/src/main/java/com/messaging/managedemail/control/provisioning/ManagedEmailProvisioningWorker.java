package com.messaging.managedemail.control.provisioning;

import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderConnectionStatus;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.NotFoundException;
import com.messaging.managedemail.control.aws.ManagedEmailSesGateway;
import com.messaging.managedemail.control.aws.ManagedEmailSesGateway.DomainIdentitySnapshot;
import com.messaging.managedemail.control.aws.ManagedEmailSesGateway.TenantSnapshot;
import com.messaging.managedemail.control.config.ManagedEmailProperties;
import com.messaging.managedemail.control.dns.ManagedDnsProvider;
import com.messaging.managedemail.control.entity.*;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.*;
import com.messaging.managedemail.control.repository.*;
import com.messaging.managedemail.control.service.ManagedEmailSenderLifecycleService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagedEmailProvisioningWorker {

  private static final int MAX_ATTEMPTS = 8;

  private final ManagedEmailProvisioningOperationRepository operationRepository;
  private final ManagedEmailTenantRepository tenantRepository;
  private final ManagedEmailDomainRepository domainRepository;
  private final ManagedEmailDnsRecordRepository dnsRepository;
  private final ManagedEmailSesGateway sesGateway;
  private final ManagedDnsProvider dnsProvider;
  private final ManagedEmailProperties properties;
  private final ManagedEmailSenderLifecycleService senderLifecycleService;
  private final BroadcastSenderConnectionRepository connectionRepository;
  private final ChannelConnectionRepository channelConnectionRepository;
  private final TransactionTemplate transactionTemplate;

  public boolean runOnce() {
    ManagedEmailProvisioningOperation operation = claim();
    if (operation == null) return false;
    try {
      switch (operation.getOperationType()) {
        case ENSURE_TENANT, ENSURE_CONFIGURATION_SET -> ensureTenant(operation);
        case ENSURE_DOMAIN_IDENTITY, ENSURE_HOSTED_DNS -> ensureDomain(operation);
        case REFRESH_DOMAIN_VERIFICATION -> refreshDomain(operation);
        case DELETE_DOMAIN_IDENTITY -> deleteDomain(operation);
        case DELETE_TENANT_RESOURCES ->
            throw new IllegalStateException(
                "Tenant decommission endpoint is not enabled in Round 1");
      }
      succeed(operation.getId());
    } catch (RuntimeException exception) {
      retryOrFail(operation.getId(), exception);
    }
    return true;
  }

  private ManagedEmailProvisioningOperation claim() {
    return transactionTemplate.execute(
        status -> {
          Instant staleBefore = Instant.now().minus(properties.getProvisioningStaleAfter());
          ManagedEmailProvisioningOperation operation =
              operationRepository.findNextClaimable(staleBefore).orElse(null);
          if (operation == null) return null;
          operation.setStatus(ProvisioningOperationStatus.PROCESSING);
          operation.setClaimedAt(Instant.now());
          operation.setAttemptCount(operation.getAttemptCount() + 1);
          operation.setNextAttemptAt(null);
          return operationRepository.save(operation);
        });
  }

  private void ensureTenant(ManagedEmailProvisioningOperation operation) {
    TenantWork work = tenantWork(operation);
    TenantSnapshot snapshot =
        ensureTenantProviderState(work.companyId(), work.providerTenantName());
    sesGateway.ensureConfigurationSet(
        work.providerTenantName(), work.configurationSetName(), properties.getSnsTopicArn());
    persistTenantProviderState(work.companyId(), work.tenantId(), snapshot);
  }

  private void ensureDomain(ManagedEmailProvisioningOperation operation) {
    DomainWork work = domainWork(operation);
    TenantSnapshot tenantSnapshot =
        ensureTenantProviderState(work.companyId(), work.providerTenantName());
    sesGateway.ensureConfigurationSet(
        work.providerTenantName(), work.configurationSetName(), properties.getSnsTopicArn());
    persistTenantProviderState(work.companyId(), work.tenantId(), tenantSnapshot);

    DomainIdentitySnapshot snapshot =
        sesGateway.ensureDomainIdentity(
            work.providerTenantName(), work.configurationSetName(), work.domain());
    persistDkimRecords(work, snapshot);

    if (work.type() == DomainType.UNIFYDESK_HOSTED) {
      ensureHostedDns(work);
    }
    DomainIdentitySnapshot refreshed = sesGateway.getDomainIdentity(work.domain());
    applyDomainState(work.companyId(), work.domainId(), refreshed);
  }

  private void refreshDomain(ManagedEmailProvisioningOperation operation) {
    DomainWork work = domainWork(operation);
    TenantSnapshot tenantSnapshot =
        readTenantProviderState(work.companyId(), work.providerTenantName());
    persistTenantProviderState(work.companyId(), work.tenantId(), tenantSnapshot);
    if (work.type() == DomainType.UNIFYDESK_HOSTED) {
      DomainIdentitySnapshot snapshot = sesGateway.getDomainIdentity(work.domain());
      persistDkimRecords(work, snapshot);
      ensureHostedDns(work);
    }
    applyDomainState(work.companyId(), work.domainId(), sesGateway.getDomainIdentity(work.domain()));
  }

  private void deleteDomain(ManagedEmailProvisioningOperation operation) {
    DomainWork work = domainWork(operation);
    if (work.type() == DomainType.UNIFYDESK_HOSTED) {
      List<ManagedDnsProvider.DnsRecord> exactRecords =
          transactionTemplate.execute(
              status ->
                  dnsRepository
                      .findByDomainIdAndCompanyIdOrderByCreatedAtAsc(
                          work.domainId(), work.companyId())
                      .stream()
                      .filter(
                          record ->
                              record.getSource() == DnsRecordSource.UNIFYDESK
                                  || record.getRecordPurpose() == DnsRecordPurpose.DKIM)
                      .map(
                          record ->
                              new ManagedDnsProvider.DnsRecord(
                                  record.getRecordType(),
                                  record.getRecordName(),
                                  record.getRecordValue(),
                                  record.getPriority()))
                      .toList());
      dnsProvider.deleteExact(exactRecords == null ? List.of() : exactRecords);
    }
    sesGateway.deleteDomainIdentity(work.providerTenantName(), work.domain());
    transactionTemplate.executeWithoutResult(
        status -> {
          ManagedEmailDomain domain =
              domainRepository
                  .findByIdAndCompanyId(work.domainId(), work.companyId())
                  .orElseThrow(() -> new NotFoundException("Managed Email domain not found"));
          domain.setReleasedAt(Instant.now());
          domain.setStatus(
              domain.getDomainType() == DomainType.UNIFYDESK_HOSTED
                  ? DomainStatus.RETIRED
                  : DomainStatus.DELETED);
          domainRepository.save(domain);
        });
  }

  private void persistDkimRecords(DomainWork work, DomainIdentitySnapshot snapshot) {
    transactionTemplate.executeWithoutResult(
        status -> {
          for (ManagedEmailSesGateway.DkimRecord item : snapshot.dkimRecords()) {
            ManagedEmailDnsRecord record =
                dnsRepository
                    .findByDomainIdAndRecordPurposeAndRecordTypeAndRecordNameAndRecordValue(
                        work.domainId(),
                        DnsRecordPurpose.DKIM,
                        DnsRecordType.CNAME,
                        item.name(),
                        item.value())
                    .orElseGet(ManagedEmailDnsRecord::new);
            if (record.getId() == null) {
              record.setDomainId(work.domainId());
              record.setCompanyId(work.companyId());
              record.setRecordPurpose(DnsRecordPurpose.DKIM);
              record.setRecordType(DnsRecordType.CNAME);
              record.setRecordName(item.name());
              record.setRecordValue(item.value());
              record.setRequired(true);
              record.setSource(DnsRecordSource.SES);
              record.setStatus(DnsRecordStatus.PENDING);
            }
            dnsRepository.save(record);
          }
          ManagedEmailDomain domain =
              domainRepository
                  .findByIdAndCompanyId(work.domainId(), work.companyId())
                  .orElseThrow(() -> new NotFoundException("Managed Email domain not found"));
          domain.setProviderIdentityName(domain.getNormalizedDomain());
          domain.setStatus(
              domain.getDomainType() == DomainType.CUSTOMER_DOMAIN
                  ? DomainStatus.PENDING_DNS
                  : DomainStatus.VERIFYING);
          domainRepository.save(domain);
        });
  }

  private void ensureHostedDns(DomainWork work) {
    if (!dnsProvider.available()) {
      throw new com.messaging.common.exception.ServiceUnavailableException(
          "Managed Email hosted DNS is not configured");
    }
    List<ManagedDnsProvider.DnsRecord> records =
        transactionTemplate.execute(
            status ->
                dnsRepository
                    .findByDomainIdAndCompanyIdOrderByCreatedAtAsc(
                        work.domainId(), work.companyId())
                    .stream()
                    .filter(record -> record.getRecordPurpose() == DnsRecordPurpose.DKIM)
                    .map(
                        record ->
                            new ManagedDnsProvider.DnsRecord(
                                record.getRecordType(),
                                record.getRecordName(),
                                record.getRecordValue(),
                                record.getPriority()))
                    .toList());
    if (records != null) {
      for (ManagedDnsProvider.DnsRecord record : records) {
        dnsProvider.ensure(record);
      }
    }

    ManagedDnsProvider.DnsRecord dmarc =
        new ManagedDnsProvider.DnsRecord(
            DnsRecordType.TXT,
            "_dmarc." + work.domain(),
            "v=DMARC1; p=quarantine",
            null);
    dnsProvider.ensure(dmarc);
    transactionTemplate.executeWithoutResult(
        status -> {
          ManagedEmailDnsRecord record =
              dnsRepository
                  .findByDomainIdAndRecordPurposeAndRecordTypeAndRecordNameAndRecordValue(
                      work.domainId(),
                      DnsRecordPurpose.DMARC,
                      DnsRecordType.TXT,
                      dmarc.name(),
                      dmarc.value())
                  .orElseGet(ManagedEmailDnsRecord::new);
          if (record.getId() == null) {
            record.setDomainId(work.domainId());
            record.setCompanyId(work.companyId());
            record.setRecordPurpose(DnsRecordPurpose.DMARC);
            record.setRecordType(DnsRecordType.TXT);
            record.setRecordName(dmarc.name());
            record.setRecordValue(dmarc.value());
            record.setRequired(false);
            record.setSource(DnsRecordSource.UNIFYDESK);
          }
          record.setStatus(DnsRecordStatus.PRESENT);
          record.setLastCheckedAt(Instant.now());
          dnsRepository.save(record);

          ManagedEmailDomain domain =
              domainRepository
                  .findByIdAndCompanyId(work.domainId(), work.companyId())
                  .orElseThrow(() -> new NotFoundException("Managed Email domain not found"));
          domain.setDmarcStatus(DmarcStatus.PRESENT_ENFORCING);
          domain.setDmarcPolicy("quarantine");
          domain.setLastDnsCheckAt(Instant.now());
          domainRepository.save(domain);
        });
  }

  private void applyDomainState(Long companyId, Long domainId, DomainIdentitySnapshot snapshot) {
    transactionTemplate.executeWithoutResult(
        tx -> {
          ManagedEmailDomain domain =
              domainRepository
                  .findByIdAndCompanyId(domainId, companyId)
                  .orElseThrow(() -> new NotFoundException("Managed Email domain not found"));
          DomainStatus priorStatus = domain.getStatus();
          VerificationStatus verification = mapVerification(snapshot.verificationStatus());
          DkimStatus dkim = mapDkim(snapshot.dkimStatus());
          domain.setVerificationStatus(verification);
          domain.setDkimStatus(dkim);
          domain.setLastProviderSyncAt(Instant.now());
          domain.setLastDnsCheckAt(Instant.now());

          boolean active =
              verification == VerificationStatus.SUCCESS && dkim == DkimStatus.SUCCESS;
          if (active) {
            domain.setStatus(DomainStatus.ACTIVE);
            if (domain.getActivatedAt() == null) domain.setActivatedAt(Instant.now());
            domain.setVerificationFailedAt(null);
            domain.setSuspendedAt(null);
          } else if (priorStatus == DomainStatus.ACTIVE
              || priorStatus == DomainStatus.SUSPENDED_VERIFICATION) {
            domain.setStatus(DomainStatus.SUSPENDED_VERIFICATION);
            if (domain.getVerificationFailedAt() == null) {
              domain.setVerificationFailedAt(Instant.now());
            }
            if (domain.getSuspendedAt() == null) domain.setSuspendedAt(Instant.now());
          } else {
            domain.setStatus(
                domain.getDomainType() == DomainType.CUSTOMER_DOMAIN
                    ? DomainStatus.PENDING_DNS
                    : DomainStatus.VERIFYING);
          }
          domainRepository.save(domain);

          List<ManagedEmailDnsRecord> records =
              dnsRepository.findByDomainIdAndCompanyIdOrderByCreatedAtAsc(
                  domain.getId(), domain.getCompanyId());
          for (ManagedEmailDnsRecord record : records) {
            if (record.getRecordPurpose() == DnsRecordPurpose.DKIM) {
              record.setStatus(active ? DnsRecordStatus.PRESENT : DnsRecordStatus.PENDING);
              record.setLastCheckedAt(Instant.now());
              dnsRepository.save(record);
            }
          }
          senderLifecycleService.setDomainSendersEnabled(
              domain.getCompanyId(), domain.getId(), active);
        });
  }

  void persistTenantProviderState(Long companyId, Long tenantId, TenantSnapshot snapshot) {
    transactionTemplate.executeWithoutResult(
        status -> {
          ManagedEmailTenant tenant =
              tenantRepository
                  .findByIdAndCompanyId(tenantId, companyId)
                  .orElseThrow(() -> new NotFoundException("Managed Email tenant not found"));
          boolean manuallyOrPolicySuspended = tenant.getStatus() == TenantStatus.SUSPENDED;
          boolean tenantProviderSendingEnabled =
              "ENABLED".equals(snapshot.sendingStatus())
                  || "REINSTATED".equals(snapshot.sendingStatus());
          if (!manuallyOrPolicySuspended) {
            tenant.setStatus(TenantStatus.ACTIVE);
            tenant.setSendingStatus(
                tenantProviderSendingEnabled
                    ? SendingStatus.ENABLED
                    : SendingStatus.PROVIDER_DISABLED);
          } else {
            tenant.setSendingStatus(SendingStatus.DISABLED);
          }
          tenant.setReputationStatus(snapshot.reputationStatus());
          tenant.setProviderMaxSendRate(snapshot.maxSendRate());
          tenant.setProviderMax24HourSend(snapshot.max24HourSend());
          tenant.setProviderSentLast24Hours(snapshot.sentLast24Hours());
          tenant.setProviderAccountSendingEnabled(
              Boolean.TRUE.equals(snapshot.accountSendingEnabled()));
          tenant.setProviderProductionAccessEnabled(
              Boolean.TRUE.equals(snapshot.productionAccessEnabled()));
          tenant.setLastProviderSyncAt(snapshot.providerObservedAt());
          tenantRepository.save(tenant);

          if (tenant.getBroadcastConnectionId() != null) {
            BroadcastSenderConnection connection =
                connectionRepository
                    .findByIdAndCompanyId(
                        tenant.getBroadcastConnectionId(), tenant.getCompanyId())
                    .orElse(null);
            if (connection != null
                && connection.getProvider()
                    == com.messaging.broadcast.enums.BroadcastSenderProvider.AMAZON_SES
                && connection.getManagementMode()
                    == com.messaging.broadcast.enums.BroadcastSenderManagementMode.MESSAGING_MANAGED) {
              boolean safeToSend =
                  properties.isEnabled()
                      && properties.isPlatformSendingEnabled()
                      && tenant.getStatus() == TenantStatus.ACTIVE
                      && tenant.getSendingStatus() == SendingStatus.ENABLED
                      && tenant.isProviderAccountSendingEnabled()
                      && tenant.isProviderProductionAccessEnabled()
                      && providerQuotaAvailable(snapshot);
              connection.setSendingEnabled(safeToSend);
              connection.setStatus(BroadcastSenderConnectionStatus.ACTIVE);
              connection.setProductionAccessEnabled(tenant.isProviderProductionAccessEnabled());
              connection.setMaxSendRate(tenant.getProviderMaxSendRate());
              connection.setMax24HourSend(tenant.getProviderMax24HourSend());
              connection.setSentLast24Hours(tenant.getProviderSentLast24Hours());
              connection.setLastValidatedAt(snapshot.providerObservedAt());
              connectionRepository.save(connection);
              channelConnectionRepository
                  .findByIdAndCompanyId(
                      connection.getChannelConnectionId(), tenant.getCompanyId())
                  .ifPresent(
                      channel -> {
                        channel.setStatus(ChannelConnectionStatus.ACTIVE);
                        channel.setLastSyncAt(Instant.now());
                        channelConnectionRepository.save(channel);
                      });
            }
          }
        });
  }

  private TenantWork tenantWork(ManagedEmailProvisioningOperation operation) {
    return transactionTemplate.execute(
        status -> {
          ManagedEmailTenant tenant =
              tenantRepository
                  .findByIdAndCompanyId(operation.getResourceId(), operation.getCompanyId())
                  .orElseThrow(() -> new NotFoundException("Managed Email tenant not found"));
          return new TenantWork(
              tenant.getCompanyId(),
              tenant.getId(),
              tenant.getProviderTenantName(),
              tenant.getProviderConfigurationSetName());
        });
  }

  private DomainWork domainWork(ManagedEmailProvisioningOperation operation) {
    return transactionTemplate.execute(
        status -> {
          ManagedEmailDomain domain =
              domainRepository
                  .findByIdAndCompanyId(operation.getResourceId(), operation.getCompanyId())
                  .orElseThrow(() -> new NotFoundException("Managed Email domain not found"));
          ManagedEmailTenant tenant =
              tenantRepository
                  .findByIdAndCompanyId(domain.getTenantId(), domain.getCompanyId())
                  .orElseThrow(() -> new NotFoundException("Managed Email tenant not found"));
          return new DomainWork(
              domain.getId(),
              domain.getCompanyId(),
              tenant.getId(),
              domain.getNormalizedDomain(),
              domain.getDomainType(),
              tenant.getProviderTenantName(),
              tenant.getProviderConfigurationSetName());
        });
  }

  private void succeed(Long operationId) {
    transactionTemplate.executeWithoutResult(
        status -> {
          ManagedEmailProvisioningOperation operation =
              operationRepository.findById(operationId).orElseThrow();
          operation.setStatus(ProvisioningOperationStatus.SUCCEEDED);
          operation.setCompletedAt(Instant.now());
          operation.setClaimedAt(null);
          operation.setNextAttemptAt(null);
          operation.setLastErrorCode(null);
          operation.setLastErrorMessage(null);
          operationRepository.save(operation);
        });
  }

  private void retryOrFail(Long operationId, RuntimeException exception) {
    log.error("Provisioning operation {} failed: {}", operationId, exception.getMessage(), exception);
    transactionTemplate.executeWithoutResult(
        status -> {
          ManagedEmailProvisioningOperation operation =
              operationRepository.findById(operationId).orElseThrow();
          operation.setClaimedAt(null);
          operation.setLastErrorCode(exception.getClass().getSimpleName());
          operation.setLastErrorMessage(exception.getMessage() != null ? exception.getMessage() : "Managed Email provider operation failed");
          if (operation.getAttemptCount() >= MAX_ATTEMPTS) {
            operation.setStatus(ProvisioningOperationStatus.FAILED);
            operation.setCompletedAt(Instant.now());
            markResourceError(operation);
          } else {
            operation.setStatus(ProvisioningOperationStatus.RETRY);
            operation.setNextAttemptAt(
                Instant.now().plus(backoff(operation.getAttemptCount())));
          }
          operationRepository.save(operation);
        });
  }

  private void markResourceError(ManagedEmailProvisioningOperation operation) {
    if (operation.getResourceType() == ProvisioningResourceType.DOMAIN) {
      domainRepository
          .findByIdAndCompanyId(operation.getResourceId(), operation.getCompanyId())
          .ifPresent(
              domain -> {
                domain.setStatus(DomainStatus.ERROR);
                domain.setSuspendedAt(Instant.now());
                domainRepository.save(domain);
                senderLifecycleService.setDomainSendersEnabled(
                    domain.getCompanyId(), domain.getId(), false);
              });
    } else if (operation.getResourceType() == ProvisioningResourceType.TENANT) {
      tenantRepository
          .findByIdAndCompanyId(operation.getResourceId(), operation.getCompanyId())
          .ifPresent(
              tenant -> {
                tenant.setStatus(TenantStatus.ERROR);
                tenant.setSendingStatus(SendingStatus.DISABLED);
                tenantRepository.save(tenant);
              });
    }
  }



  private TenantSnapshot ensureTenantProviderState(Long companyId, String providerTenantName) {
    try {
      return sesGateway.ensureTenant(providerTenantName);
    } catch (RuntimeException exception) {
      failClosedManagedConnection(companyId);
      throw exception;
    }
  }

  private TenantSnapshot readTenantProviderState(Long companyId, String providerTenantName) {
    try {
      return sesGateway.getTenant(providerTenantName);
    } catch (RuntimeException exception) {
      failClosedManagedConnection(companyId);
      throw exception;
    }
  }

  private boolean providerQuotaAvailable(TenantSnapshot snapshot) {
    return snapshot.providerObservedAt() != null
        && snapshot.maxSendRate() != null
        && snapshot.maxSendRate() > 0
        && snapshot.max24HourSend() != null
        && snapshot.max24HourSend() > 0
        && snapshot.sentLast24Hours() != null
        && snapshot.sentLast24Hours() >= 0;
  }

  private void failClosedManagedConnection(Long companyId) {
    try {
      transactionTemplate.executeWithoutResult(
          status ->
              connectionRepository
                  .findFirstByCompanyIdAndProviderAndManagementModeAndStatusNot(
                      companyId,
                      com.messaging.broadcast.enums.BroadcastSenderProvider.AMAZON_SES,
                      com.messaging.broadcast.enums.BroadcastSenderManagementMode.MESSAGING_MANAGED,
                      BroadcastSenderConnectionStatus.DISABLED)
                  .ifPresent(
                      connection -> {
                        connection.setSendingEnabled(false);
                        connectionRepository.save(connection);
                      }));
    } catch (RuntimeException ignored) {
      // Preserve the original provisioning failure; runtime gates also fail closed on stale state.
    }
  }

  private Duration backoff(int attempt) {
    long seconds = Math.min(900L, 2L << Math.min(attempt, 8));
    return Duration.ofSeconds(seconds);
  }

  private VerificationStatus mapVerification(String value) {
    if (value == null) return VerificationStatus.UNKNOWN;
    return switch (value) {
      case "SUCCESS" -> VerificationStatus.SUCCESS;
      case "FAILED" -> VerificationStatus.FAILED;
      case "PENDING", "NOT_STARTED" -> VerificationStatus.PENDING;
      default -> VerificationStatus.UNKNOWN;
    };
  }

  private DkimStatus mapDkim(String value) {
    if (value == null) return DkimStatus.UNKNOWN;
    return switch (value) {
      case "SUCCESS" -> DkimStatus.SUCCESS;
      case "FAILED" -> DkimStatus.FAILED;
      case "PENDING", "NOT_STARTED" -> DkimStatus.PENDING;
      default -> DkimStatus.UNKNOWN;
    };
  }

  private record TenantWork(
      Long companyId, Long tenantId, String providerTenantName, String configurationSetName) {}

  private record DomainWork(
      Long domainId,
      Long companyId,
      Long tenantId,
      String domain,
      DomainType type,
      String providerTenantName,
      String configurationSetName) {}
}
