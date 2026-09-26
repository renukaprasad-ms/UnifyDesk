package com.messaging.managedemail.control.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.messaging.admin.enums.PlatformPermission;
import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.managedemail.control.entity.ManagedEmailAdminAction;
import com.messaging.managedemail.control.entity.ManagedEmailTenant;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.AdminActionType;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.SendingStatus;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.TenantStatus;
import com.messaging.managedemail.control.provisioning.ManagedEmailProvisioningService;
import com.messaging.managedemail.control.repository.ManagedEmailAdminActionRepository;
import com.messaging.managedemail.control.repository.ManagedEmailTenantRepository;
import com.messaging.security.service.CurrentUserService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagedEmailAdminContainmentServiceTests {

  @Mock private ManagedEmailTenantRepository tenantRepository;
  @Mock private BroadcastSenderConnectionRepository connectionRepository;
  @Mock private ManagedEmailAdminActionRepository actionRepository;
  @Mock private ManagedEmailProvisioningService provisioningService;
  @Mock private CurrentUserService currentUserService;
  @Mock private PlatformAdminAuthorizationService authorizationService;

  private ManagedEmailAdminContainmentService service;

  @BeforeEach
  void setUp() {
    service =
        new ManagedEmailAdminContainmentService(
            tenantRepository,
            connectionRepository,
            actionRepository,
            provisioningService,
            currentUserService,
            authorizationService);
    when(currentUserService.currentUserId()).thenReturn(7L);
  }

  @Test
  void suspensionImmediatelyMakesManagedConnectionNonSendableAndAuditsActor() {
    ManagedEmailTenant tenant = tenant(TenantStatus.ACTIVE);
    BroadcastSenderConnection connection = managedConnection();
    connection.setSendingEnabled(true);
    when(tenantRepository.findByCompanyId(1L)).thenReturn(Optional.of(tenant));
    when(connectionRepository.findByIdAndCompanyId(44L, 1L)).thenReturn(Optional.of(connection));

    var response = service.suspend(1L, "abuse investigation");

    assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
    assertThat(tenant.getSendingStatus()).isEqualTo(SendingStatus.DISABLED);
    assertThat(connection.isSendingEnabled()).isFalse();
    assertThat(response.connectionSendingEnabled()).isFalse();

    ArgumentCaptor<ManagedEmailAdminAction> audit =
        ArgumentCaptor.forClass(ManagedEmailAdminAction.class);
    verify(actionRepository).save(audit.capture());
    assertThat(audit.getValue().getActorUserId()).isEqualTo(7L);
    assertThat(audit.getValue().getCompanyId()).isEqualTo(1L);
    assertThat(audit.getValue().getAction()).isEqualTo(AdminActionType.SUSPEND);
    assertThat(audit.getValue().getReason()).isEqualTo("abuse investigation");
    assertThat(audit.getValue().getCreatedAt()).isNotNull();
  }

  @Test
  void resumeRequiresPrivilegedAuthorizationBeforeTenantMutation() {
    doThrow(new ForbiddenException("denied"))
        .when(authorizationService)
        .requirePermission(7L, PlatformPermission.COMPANY_SUSPEND);

    assertThatThrownBy(() -> service.resume(1L, "remediated"))
        .isInstanceOf(ForbiddenException.class);

    verifyNoInteractions(tenantRepository, connectionRepository, actionRepository, provisioningService);
  }

  @Test
  void resumeStaysFailClosedUntilProviderReconciliation() {
    ManagedEmailTenant tenant = tenant(TenantStatus.SUSPENDED);
    BroadcastSenderConnection connection = managedConnection();
    connection.setSendingEnabled(false);
    when(tenantRepository.findByCompanyId(1L)).thenReturn(Optional.of(tenant));
    when(connectionRepository.findByIdAndCompanyId(44L, 1L)).thenReturn(Optional.of(connection));

    var response = service.resume(1L, "remediation verified");

    assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
    assertThat(tenant.getSendingStatus()).isEqualTo(SendingStatus.DISABLED);
    assertThat(connection.isSendingEnabled()).isFalse();
    assertThat(response.connectionSendingEnabled()).isFalse();
    verify(provisioningService)
        .enqueueOrReset(
            1L,
            com.messaging.managedemail.control.enums.ManagedEmailEnums.ProvisioningResourceType.TENANT,
            33L,
            com.messaging.managedemail.control.enums.ManagedEmailEnums.ProvisioningOperationType.ENSURE_TENANT,
            "tenant:ensure:1");
  }

  private ManagedEmailTenant tenant(TenantStatus status) {
    ManagedEmailTenant tenant = new ManagedEmailTenant();
    tenant.setId(33L);
    tenant.setCompanyId(1L);
    tenant.setBroadcastConnectionId(44L);
    tenant.setStatus(status);
    tenant.setSendingStatus(
        status == TenantStatus.ACTIVE ? SendingStatus.ENABLED : SendingStatus.DISABLED);
    return tenant;
  }

  private BroadcastSenderConnection managedConnection() {
    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(44L);
    connection.setCompanyId(1L);
    connection.setProvider(BroadcastSenderProvider.AMAZON_SES);
    connection.setManagementMode(BroadcastSenderManagementMode.MESSAGING_MANAGED);
    return connection;
  }
}
