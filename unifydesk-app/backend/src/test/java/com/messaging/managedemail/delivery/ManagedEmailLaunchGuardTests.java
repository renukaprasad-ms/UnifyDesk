package com.messaging.managedemail.delivery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.messaging.billing.service.EntitlementService;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.common.exception.BadRequestException;
import com.messaging.managedemail.metering.ManagedEmailUsageService;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagedEmailLaunchGuardTests {

  @Test
  void globalKillSwitchRejectsManagedLaunch() {
    Fixture fixture = fixture();
    fixture.properties.setSendingEnabled(false);
    assertThatThrownBy(() -> fixture.guard.validateLaunch(10L, fixture.sender, fixture.connection, 1))
        .isInstanceOf(BadRequestException.class)
        .hasMessage("MANAGED_EMAIL_PLATFORM_DISABLED");
  }

  @Test
  void inactiveTenantRejectsManagedLaunch() {
    Fixture fixture = fixture();
    when(fixture.runtime.findSenderRuntime(10L, 20L))
        .thenReturn(Optional.of(runtime("SUSPENDED", "ACTIVE")));
    assertThatThrownBy(() -> fixture.guard.validateLaunch(10L, fixture.sender, fixture.connection, 1))
        .isInstanceOf(BadRequestException.class)
        .hasMessage("MANAGED_EMAIL_SENDING_SUSPENDED");
  }

  @Test
  void inactiveDomainRejectsManagedLaunch() {
    Fixture fixture = fixture();
    when(fixture.runtime.findSenderRuntime(10L, 20L))
        .thenReturn(Optional.of(runtime("ACTIVE", "SUSPENDED_VERIFICATION")));
    assertThatThrownBy(() -> fixture.guard.validateLaunch(10L, fixture.sender, fixture.connection, 1))
        .isInstanceOf(BadRequestException.class)
        .hasMessage("MANAGED_EMAIL_SENDING_SUSPENDED");
  }

  private Fixture fixture() {
    ManagedEmailRuntimeRepository runtime = mock(ManagedEmailRuntimeRepository.class);
    EntitlementService entitlement = mock(EntitlementService.class);
    ManagedEmailUsageService usage = mock(ManagedEmailUsageService.class);
    ManagedEmailRuntimeProperties properties = new ManagedEmailRuntimeProperties();
    properties.setSendingEnabled(true);
    properties.setRegion("us-east-1");
    properties.setCampaignSizeCap(1000);
    properties.setNewTenantDailyCap(1000);
    properties.setMinimumReputationSampleSize(100);
    properties.setWarningHardBounceRate(new BigDecimal("0.02"));
    properties.setWarningComplaintRate(new BigDecimal("0.001"));
    properties.setThrottleHardBounceRate(new BigDecimal("0.04"));
    properties.setThrottleComplaintRate(new BigDecimal("0.002"));
    properties.setSuspensionHardBounceRate(new BigDecimal("0.08"));
    properties.setSuspensionComplaintRate(new BigDecimal("0.005"));
    properties.setThrottleRateMultiplier(new BigDecimal("0.5"));
    when(entitlement.booleanValue(10L, "MANAGED_EMAIL_ENABLED")).thenReturn(true);
    when(usage.canReserve(10L, 1)).thenReturn(true);
    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(30L);
    connection.setCompanyId(10L);
    connection.setManagementMode(BroadcastSenderManagementMode.MESSAGING_MANAGED);
    BroadcastSender sender = new BroadcastSender();
    sender.setId(20L);
    sender.setCompanyId(10L);
    return new Fixture(
        new ManagedEmailLaunchGuard(runtime, properties, entitlement, usage),
        runtime,
        properties,
        connection,
        sender);
  }

  private ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime(
      String tenantStatus, String domainStatus) {
    return new ManagedEmailRuntimeRepository.ManagedSenderRuntime(
        10L, 20L, 30L, 50L, 40L, "ACTIVE", "ACTIVE", "MESSAGING_MANAGED", true,
        tenantStatus, "ENABLED", "HEALTHY", "ESTABLISHED", domainStatus, "example.test",
        "us-east-1", "tenant-10", "cfg-10", 20.0, 10000.0);
  }

  private record Fixture(
      ManagedEmailLaunchGuard guard,
      ManagedEmailRuntimeRepository runtime,
      ManagedEmailRuntimeProperties properties,
      BroadcastSenderConnection connection,
      BroadcastSender sender) {}
}
