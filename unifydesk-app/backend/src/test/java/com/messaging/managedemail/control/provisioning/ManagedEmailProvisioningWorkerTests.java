package com.messaging.managedemail.control.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderConnectionStatus;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.managedemail.control.aws.ManagedEmailSesGateway;
import com.messaging.managedemail.control.aws.ManagedEmailSesGateway.DkimRecord;
import com.messaging.managedemail.control.aws.ManagedEmailSesGateway.DomainIdentitySnapshot;
import com.messaging.managedemail.control.aws.ManagedEmailSesGateway.TenantSnapshot;
import com.messaging.managedemail.control.config.ManagedEmailProperties;
import com.messaging.managedemail.control.dns.ManagedDnsProvider;
import com.messaging.managedemail.control.entity.ManagedEmailDnsRecord;
import com.messaging.managedemail.control.entity.ManagedEmailDomain;
import com.messaging.managedemail.control.entity.ManagedEmailProvisioningOperation;
import com.messaging.managedemail.control.entity.ManagedEmailTenant;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.DkimStatus;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.DomainStatus;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.DomainType;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.ProvisioningOperationStatus;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.ProvisioningOperationType;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.ProvisioningResourceType;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.SendingStatus;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.TenantStatus;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.VerificationStatus;
import com.messaging.managedemail.control.repository.ManagedEmailDnsRecordRepository;
import com.messaging.managedemail.control.repository.ManagedEmailDomainRepository;
import com.messaging.managedemail.control.repository.ManagedEmailProvisioningOperationRepository;
import com.messaging.managedemail.control.repository.ManagedEmailTenantRepository;
import com.messaging.managedemail.control.service.ManagedEmailSenderLifecycleService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ManagedEmailProvisioningWorkerTests {

  @Mock private ManagedEmailProvisioningOperationRepository operationRepository;
  @Mock private ManagedEmailTenantRepository tenantRepository;
  @Mock private ManagedEmailDomainRepository domainRepository;
  @Mock private ManagedEmailDnsRecordRepository dnsRepository;
  @Mock private ManagedEmailSesGateway sesGateway;
  @Mock private ManagedDnsProvider dnsProvider;
  @Mock private ManagedEmailSenderLifecycleService senderLifecycleService;
  @Mock private BroadcastSenderConnectionRepository connectionRepository;
  @Mock private ChannelConnectionRepository channelConnectionRepository;

  private ManagedEmailProvisioningWorker worker;

  @BeforeEach
  void setUp() {
    ManagedEmailProperties properties = new ManagedEmailProperties();
    properties.setEnabled(true);
    properties.setPlatformSendingEnabled(true);
    properties.setSnsTopicArn("arn:aws:sns:ap-south-1:123456789012:managed-email");
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

    worker =
        new ManagedEmailProvisioningWorker(
            operationRepository,
            tenantRepository,
            domainRepository,
            dnsRepository,
            sesGateway,
            dnsProvider,
            properties,
            senderLifecycleService,
            connectionRepository,
            channelConnectionRepository,
            new TransactionTemplate(transactionManager));
  }



  @Test
  void persistsExactProviderObservationTimestampEvenWhenPersistenceIsLater() {
    Instant providerObservedAt = Instant.parse("2026-09-26T10:00:00Z");
    Instant persistenceHappensLater = Instant.parse("2026-09-26T10:00:10Z");
    ManagedEmailTenant tenant = tenant();
    tenant.setBroadcastConnectionId(44L);
    BroadcastSenderConnection connection = managedConnection();
    when(tenantRepository.findByIdAndCompanyId(33L, 1L)).thenReturn(Optional.of(tenant));
    when(connectionRepository.findByIdAndCompanyId(44L, 1L)).thenReturn(Optional.of(connection));

    TenantSnapshot snapshot =
        new TenantSnapshot(
            "ENABLED", "LOW", 25.0, 50000.0, 1234.0, true, true, providerObservedAt);

    worker.persistTenantProviderState(1L, 33L, snapshot);

    assertThat(connection.getLastValidatedAt()).isEqualTo(providerObservedAt);
    assertThat(tenant.getLastProviderSyncAt()).isEqualTo(providerObservedAt);
    assertThat(connection.getLastValidatedAt()).isBefore(persistenceHappensLater);
  }

  @Test
  void retryingPersistenceWithSameSnapshotNeverMakesObservationLookNewer() {
    Instant providerObservedAt = Instant.parse("2026-09-26T10:00:00Z");
    ManagedEmailTenant tenant = tenant();
    tenant.setBroadcastConnectionId(44L);
    BroadcastSenderConnection connection = managedConnection();
    when(tenantRepository.findByIdAndCompanyId(33L, 1L)).thenReturn(Optional.of(tenant));
    when(connectionRepository.findByIdAndCompanyId(44L, 1L)).thenReturn(Optional.of(connection));
    TenantSnapshot snapshot =
        new TenantSnapshot(
            "ENABLED", "LOW", 25.0, 50000.0, 1234.0, true, true, providerObservedAt);

    worker.persistTenantProviderState(1L, 33L, snapshot);
    connection.setLastValidatedAt(Instant.parse("2026-09-26T10:10:00Z"));
    tenant.setLastProviderSyncAt(Instant.parse("2026-09-26T10:10:00Z"));
    worker.persistTenantProviderState(1L, 33L, snapshot);

    assertThat(connection.getLastValidatedAt()).isEqualTo(providerObservedAt);
    assertThat(tenant.getLastProviderSyncAt()).isEqualTo(providerObservedAt);
  }

  @Test
  void newerProviderReadMovesQuotaValuesAndTimestampTogether() {
    Instant firstObservedAt = Instant.parse("2026-09-26T10:00:00Z");
    Instant secondObservedAt = Instant.parse("2026-09-26T10:05:00Z");
    ManagedEmailTenant tenant = tenant();
    tenant.setBroadcastConnectionId(44L);
    BroadcastSenderConnection connection = managedConnection();
    when(tenantRepository.findByIdAndCompanyId(33L, 1L)).thenReturn(Optional.of(tenant));
    when(connectionRepository.findByIdAndCompanyId(44L, 1L)).thenReturn(Optional.of(connection));

    worker.persistTenantProviderState(
        1L,
        33L,
        new TenantSnapshot(
            "ENABLED", "LOW", 40.0, 80000.0, 1000.0, true, true, firstObservedAt));
    worker.persistTenantProviderState(
        1L,
        33L,
        new TenantSnapshot(
            "ENABLED", "LOW", 5.0, 10000.0, 9000.0, true, true, secondObservedAt));

    assertThat(connection.getMaxSendRate()).isEqualTo(5.0);
    assertThat(connection.getMax24HourSend()).isEqualTo(10000.0);
    assertThat(connection.getSentLast24Hours()).isEqualTo(9000.0);
    assertThat(connection.getLastValidatedAt()).isEqualTo(secondObservedAt);
  }

  @Test
  void providerObservationLowerBoundIncludesSendAcceptedBetweenObservationAndPersistence() {
    Instant providerObservedAt = Instant.parse("2026-09-26T10:00:00Z");
    Instant localAcceptedSendAt = Instant.parse("2026-09-26T10:00:05Z");
    Instant persistenceAt = Instant.parse("2026-09-26T10:00:10Z");
    ManagedEmailTenant tenant = tenant();
    tenant.setBroadcastConnectionId(44L);
    BroadcastSenderConnection connection = managedConnection();
    when(tenantRepository.findByIdAndCompanyId(33L, 1L)).thenReturn(Optional.of(tenant));
    when(connectionRepository.findByIdAndCompanyId(44L, 1L)).thenReturn(Optional.of(connection));

    worker.persistTenantProviderState(
        1L,
        33L,
        new TenantSnapshot(
            "ENABLED", "LOW", 25.0, 50000.0, 1234.0, true, true, providerObservedAt));

    long localSendsSinceProviderObservation =
        java.util.stream.Stream.of(localAcceptedSendAt)
            .filter(acceptedAt -> !acceptedAt.isBefore(connection.getLastValidatedAt()))
            .count();

    assertThat(connection.getLastValidatedAt()).isEqualTo(providerObservedAt);
    assertThat(localAcceptedSendAt).isAfter(connection.getLastValidatedAt());
    assertThat(localAcceptedSendAt).isBefore(persistenceAt);
    assertThat(localSendsSinceProviderObservation).isEqualTo(1);
  }

  @Test
  void productionAccountSynchronizesManagedConnectionAndEnablesSending() {
    ManagedEmailTenant tenant = tenant();
    tenant.setBroadcastConnectionId(44L);
    BroadcastSenderConnection connection = managedConnection();
    when(tenantRepository.findByIdAndCompanyId(33L, 1L)).thenReturn(Optional.of(tenant));
    when(connectionRepository.findByIdAndCompanyId(44L, 1L)).thenReturn(Optional.of(connection));

    worker.persistTenantProviderState(
        1L,
        33L,
        new TenantSnapshot("ENABLED", "LOW", 25.0, 50000.0, 1234.0, true, true, Instant.parse("2026-09-26T10:00:00Z")));

    assertThat(connection.isSendingEnabled()).isTrue();
    assertThat(connection.isProductionAccessEnabled()).isTrue();
    assertThat(connection.getMaxSendRate()).isEqualTo(25.0);
    assertThat(connection.getMax24HourSend()).isEqualTo(50000.0);
    assertThat(connection.getSentLast24Hours()).isEqualTo(1234.0);
    assertThat(connection.getLastValidatedAt()).isNotNull();
  }

  @Test
  void sandboxFailsClosedEvenWithQuotaAndAccountSendingEnabled() {
    ManagedEmailTenant tenant = tenant();
    tenant.setBroadcastConnectionId(44L);
    BroadcastSenderConnection connection = managedConnection();
    when(tenantRepository.findByIdAndCompanyId(33L, 1L)).thenReturn(Optional.of(tenant));
    when(connectionRepository.findByIdAndCompanyId(44L, 1L)).thenReturn(Optional.of(connection));

    worker.persistTenantProviderState(
        1L,
        33L,
        new TenantSnapshot("ENABLED", "LOW", 25.0, 50000.0, 100.0, true, false, Instant.parse("2026-09-26T10:00:00Z")));

    assertThat(connection.isSendingEnabled()).isFalse();
    assertThat(connection.isProductionAccessEnabled()).isFalse();
  }

  @Test
  void accountSendingDisabledFailsClosed() {
    ManagedEmailTenant tenant = tenant();
    tenant.setBroadcastConnectionId(44L);
    BroadcastSenderConnection connection = managedConnection();
    when(tenantRepository.findByIdAndCompanyId(33L, 1L)).thenReturn(Optional.of(tenant));
    when(connectionRepository.findByIdAndCompanyId(44L, 1L)).thenReturn(Optional.of(connection));

    worker.persistTenantProviderState(
        1L,
        33L,
        new TenantSnapshot("ENABLED", "LOW", 25.0, 50000.0, 100.0, false, true, Instant.parse("2026-09-26T10:00:00Z")));

    assertThat(connection.isSendingEnabled()).isFalse();
  }

  @Test
  void missingProviderQuotaFailsClosed() {
    ManagedEmailTenant tenant = tenant();
    tenant.setBroadcastConnectionId(44L);
    BroadcastSenderConnection connection = managedConnection();
    when(tenantRepository.findByIdAndCompanyId(33L, 1L)).thenReturn(Optional.of(tenant));
    when(connectionRepository.findByIdAndCompanyId(44L, 1L)).thenReturn(Optional.of(connection));

    worker.persistTenantProviderState(
        1L,
        33L,
        new TenantSnapshot("ENABLED", "LOW", null, 50000.0, 100.0, true, true, Instant.parse("2026-09-26T10:00:00Z")));

    assertThat(connection.isSendingEnabled()).isFalse();
  }

  @Test
  void quotaRefreshMovesManagedConnectionUpAndDown() {
    ManagedEmailTenant tenant = tenant();
    tenant.setBroadcastConnectionId(44L);
    BroadcastSenderConnection connection = managedConnection();
    when(tenantRepository.findByIdAndCompanyId(33L, 1L)).thenReturn(Optional.of(tenant));
    when(connectionRepository.findByIdAndCompanyId(44L, 1L)).thenReturn(Optional.of(connection));

    worker.persistTenantProviderState(
        1L,
        33L,
        new TenantSnapshot(
            "ENABLED",
            "LOW",
            40.0,
            80000.0,
            1000.0,
            true,
            true,
            Instant.parse("2026-09-26T10:00:00Z")));
    assertThat(connection.getMaxSendRate()).isEqualTo(40.0);
    assertThat(connection.getMax24HourSend()).isEqualTo(80000.0);
    assertThat(connection.getSentLast24Hours()).isEqualTo(1000.0);

    worker.persistTenantProviderState(
        1L,
        33L,
        new TenantSnapshot(
            "ENABLED",
            "LOW",
            5.0,
            10000.0,
            9000.0,
            true,
            true,
            Instant.parse("2026-09-26T10:05:00Z")));
    assertThat(connection.getMaxSendRate()).isEqualTo(5.0);
    assertThat(connection.getMax24HourSend()).isEqualTo(10000.0);
    assertThat(connection.getSentLast24Hours()).isEqualTo(9000.0);
    assertThat(connection.isSendingEnabled()).isTrue();
  }

  @Test
  void customerManagedSesConnectionIsNotChangedByManagedRefresh() {
    ManagedEmailTenant tenant = tenant();
    tenant.setBroadcastConnectionId(44L);
    BroadcastSenderConnection connection = managedConnection();
    connection.setManagementMode(BroadcastSenderManagementMode.CUSTOMER_MANAGED);
    connection.setMaxSendRate(7.0);
    connection.setMax24HourSend(700.0);
    connection.setSentLast24Hours(70.0);
    connection.setSendingEnabled(true);
    when(tenantRepository.findByIdAndCompanyId(33L, 1L)).thenReturn(Optional.of(tenant));
    when(connectionRepository.findByIdAndCompanyId(44L, 1L)).thenReturn(Optional.of(connection));

    worker.persistTenantProviderState(
        1L,
        33L,
        new TenantSnapshot(
            "ENABLED",
            "LOW",
            50.0,
            50000.0,
            500.0,
            true,
            true,
            Instant.parse("2026-09-26T10:00:00Z")));

    assertThat(connection.getMaxSendRate()).isEqualTo(7.0);
    assertThat(connection.getMax24HourSend()).isEqualTo(700.0);
    assertThat(connection.getSentLast24Hours()).isEqualTo(70.0);
    assertThat(connection.isSendingEnabled()).isTrue();
  }

  @Test
  void persistsEasyDkimAndActivatesOnlyAfterVerificationAndDkimSucceed() {
    ManagedEmailProvisioningOperation operation =
        operation(100L, ProvisioningOperationType.ENSURE_DOMAIN_IDENTITY, 22L);
    ManagedEmailDomain domain = domain(DomainStatus.PROVISIONING);
    ManagedEmailTenant tenant = tenant();
    List<ManagedEmailDnsRecord> persistedRecords = new ArrayList<>();

    stubClaim(operation);
    when(domainRepository.findByIdAndCompanyId(22L, 1L)).thenReturn(Optional.of(domain));
    when(tenantRepository.findByIdAndCompanyId(33L, 1L)).thenReturn(Optional.of(tenant));
    when(domainRepository.save(any(ManagedEmailDomain.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(tenantRepository.save(any(ManagedEmailTenant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(dnsRepository
            .findByDomainIdAndRecordPurposeAndRecordTypeAndRecordNameAndRecordValue(
                eq(22L), any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(dnsRepository.save(any(ManagedEmailDnsRecord.class)))
        .thenAnswer(
            invocation -> {
              ManagedEmailDnsRecord record = invocation.getArgument(0);
              if (!persistedRecords.contains(record)) {
                persistedRecords.add(record);
              }
              return record;
            });
    when(dnsRepository.findByDomainIdAndCompanyIdOrderByCreatedAtAsc(22L, 1L))
        .thenAnswer(invocation -> List.copyOf(persistedRecords));
    when(sesGateway.ensureTenant("ud-c-token")).thenReturn(new TenantSnapshot("ENABLED", "LOW"));
    when(sesGateway.ensureDomainIdentity("ud-c-token", "ud-c-token", "example.com"))
        .thenReturn(
            new DomainIdentitySnapshot(
                "PENDING",
                "PENDING",
                List.of(
                    new DkimRecord(
                        "token._domainkey.example.com", "token.dkim.amazonses.com"))));
    when(sesGateway.getDomainIdentity("example.com"))
        .thenReturn(
            new DomainIdentitySnapshot(
                "SUCCESS",
                "SUCCESS",
                List.of(
                    new DkimRecord(
                        "token._domainkey.example.com", "token.dkim.amazonses.com"))));

    assertThat(worker.runOnce()).isTrue();

    assertThat(domain.getStatus()).isEqualTo(DomainStatus.ACTIVE);
    assertThat(domain.getVerificationStatus()).isEqualTo(VerificationStatus.SUCCESS);
    assertThat(domain.getDkimStatus()).isEqualTo(DkimStatus.SUCCESS);
    assertThat(persistedRecords).hasSize(1);
    assertThat(persistedRecords.getFirst().getRecordName())
        .isEqualTo("token._domainkey.example.com");
    assertThat(persistedRecords.getFirst().getRecordValue())
        .isEqualTo("token.dkim.amazonses.com");
    verify(senderLifecycleService).setDomainSendersEnabled(1L, 22L, true);
    assertThat(operation.getStatus()).isEqualTo(ProvisioningOperationStatus.SUCCEEDED);
  }

  @Test
  void verificationLossSuspendsAnActiveDomainAndItsSenders() {
    ManagedEmailProvisioningOperation operation =
        operation(101L, ProvisioningOperationType.REFRESH_DOMAIN_VERIFICATION, 22L);
    ManagedEmailDomain domain = domain(DomainStatus.ACTIVE);
    ManagedEmailTenant tenant = tenant();

    stubClaim(operation);
    when(domainRepository.findByIdAndCompanyId(22L, 1L)).thenReturn(Optional.of(domain));
    when(tenantRepository.findByIdAndCompanyId(33L, 1L)).thenReturn(Optional.of(tenant));
    when(domainRepository.save(any(ManagedEmailDomain.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(tenantRepository.save(any(ManagedEmailTenant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(dnsRepository.findByDomainIdAndCompanyIdOrderByCreatedAtAsc(22L, 1L))
        .thenReturn(List.of());
    when(sesGateway.getTenant("ud-c-token")).thenReturn(new TenantSnapshot("ENABLED", "LOW"));
    when(sesGateway.getDomainIdentity("example.com"))
        .thenReturn(new DomainIdentitySnapshot("FAILED", "SUCCESS", List.of()));

    worker.runOnce();

    assertThat(domain.getStatus()).isEqualTo(DomainStatus.SUSPENDED_VERIFICATION);
    assertThat(domain.getSuspendedAt()).isNotNull();
    verify(senderLifecycleService).setDomainSendersEnabled(1L, 22L, false);
  }

  @Test
  void transientProviderFailureSchedulesRetryWithoutLeakingProviderDetails() {
    ManagedEmailProvisioningOperation operation =
        operation(102L, ProvisioningOperationType.ENSURE_TENANT, 33L);
    ManagedEmailTenant tenant = tenant();

    stubClaim(operation);
    when(tenantRepository.findByIdAndCompanyId(33L, 1L)).thenReturn(Optional.of(tenant));
    when(sesGateway.ensureTenant("ud-c-token"))
        .thenThrow(new ServiceUnavailableException("provider timeout secret detail"));

    worker.runOnce();

    assertThat(operation.getStatus()).isEqualTo(ProvisioningOperationStatus.RETRY);
    assertThat(operation.getNextAttemptAt()).isNotNull();
    assertThat(operation.getLastErrorMessage()).isEqualTo("Managed Email provider operation failed");
  }

  private void stubClaim(ManagedEmailProvisioningOperation operation) {
    when(operationRepository.findNextClaimable(any())).thenReturn(Optional.of(operation));
    when(operationRepository.save(any(ManagedEmailProvisioningOperation.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(operationRepository.findById(operation.getId())).thenReturn(Optional.of(operation));
  }

  private ManagedEmailProvisioningOperation operation(
      Long id, ProvisioningOperationType type, Long resourceId) {
    ManagedEmailProvisioningOperation operation = new ManagedEmailProvisioningOperation();
    operation.setId(id);
    operation.setCompanyId(1L);
    operation.setResourceType(
        type == ProvisioningOperationType.ENSURE_TENANT
            ? ProvisioningResourceType.TENANT
            : ProvisioningResourceType.DOMAIN);
    operation.setResourceId(resourceId);
    operation.setOperationType(type);
    operation.setIdempotencyKey("op-" + id);
    operation.setStatus(ProvisioningOperationStatus.PENDING);
    return operation;
  }

  private ManagedEmailDomain domain(DomainStatus status) {
    ManagedEmailDomain domain = new ManagedEmailDomain();
    domain.setId(22L);
    domain.setCompanyId(1L);
    domain.setTenantId(33L);
    domain.setDomainType(DomainType.CUSTOMER_DOMAIN);
    domain.setNormalizedDomain("example.com");
    domain.setDisplayDomain("example.com");
    domain.setStatus(status);
    domain.setVerificationStatus(VerificationStatus.PENDING);
    domain.setDkimStatus(DkimStatus.PENDING);
    return domain;
  }

  private BroadcastSenderConnection managedConnection() {
    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(44L);
    connection.setCompanyId(1L);
    connection.setChannelConnectionId(44L);
    connection.setProvider(BroadcastSenderProvider.AMAZON_SES);
    connection.setManagementMode(BroadcastSenderManagementMode.MESSAGING_MANAGED);
    connection.setStatus(BroadcastSenderConnectionStatus.ACTIVE);
    return connection;
  }

  private ManagedEmailTenant tenant() {
    ManagedEmailTenant tenant = new ManagedEmailTenant();
    tenant.setId(33L);
    tenant.setCompanyId(1L);
    tenant.setProviderTenantName("ud-c-token");
    tenant.setProviderConfigurationSetName("ud-c-token");
    tenant.setStatus(TenantStatus.ACTIVE);
    tenant.setSendingStatus(SendingStatus.ENABLED);
    return tenant;
  }
}
