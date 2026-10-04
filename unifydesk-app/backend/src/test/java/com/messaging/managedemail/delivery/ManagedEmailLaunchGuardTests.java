package com.messaging.managedemail.delivery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.messaging.billing.service.EntitlementService;
import com.messaging.billing.service.ManagedFinancialExposureGuard;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.common.exception.BadRequestException;
import com.messaging.managedemail.metering.ManagedEmailUsageService;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagedEmailLaunchGuardTests {

  @Test
  void activeManagedEmailLaunchIsAllowedThroughFinancialBoundary() {
    Fixture fixture = fixture();
    when(fixture.runtime.findSenderRuntime(10L, 20L))
        .thenReturn(Optional.of(runtime(true, true, "ACTIVE", "ACTIVE")));

    fixture.guard.validateLaunch(10L, fixture.sender, fixture.connection, 1);

    verify(fixture.exposure).assertAllowed(10L);
    verify(fixture.usage).canReserve(10L, 1);
  }

  @Test
  void blockedBillingStatusRejectsManagedEmailBeforeReservationChecks() {
    Fixture fixture = fixture();
    doThrow(new com.messaging.billing.exception.PlanEntitlementException(
            ManagedFinancialExposureGuard.BLOCKED))
        .when(fixture.exposure)
        .assertAllowed(10L);

    assertThatThrownBy(
            () -> fixture.guard.validateLaunch(10L, fixture.sender, fixture.connection, 1))
        .isInstanceOf(com.messaging.billing.exception.PlanEntitlementException.class)
        .hasMessage(ManagedFinancialExposureGuard.BLOCKED);

    verifyNoInteractions(fixture.runtime, fixture.usage);
  }

  @Test
  void customerOwnedEmailDoesNotUseManagedFinancialExposureGuard() {
    Fixture fixture = fixture();
    fixture.connection.setManagementMode(BroadcastSenderManagementMode.CUSTOMER_MANAGED);

    fixture.guard.validateLaunch(10L, fixture.sender, fixture.connection, 1);

    verifyNoInteractions(fixture.exposure, fixture.runtime, fixture.usage);
  }

  @Test
  void globalKillSwitchRejectsManagedLaunch() {
    Fixture fixture = fixture();
    fixture.properties.setSendingEnabled(false);
    assertSuspendedOrPlatform(fixture, "MANAGED_EMAIL_PLATFORM_DISABLED");
  }

  @Test
  void inactiveTenantRejectsManagedLaunch() {
    Fixture fixture = fixture();
    when(fixture.runtime.findSenderRuntime(10L, 20L))
        .thenReturn(Optional.of(runtime(true, true, "SUSPENDED", "ACTIVE")));
    assertSuspendedOrPlatform(fixture, "MANAGED_EMAIL_SENDING_SUSPENDED");
  }

  @Test
  void inactiveDomainRejectsManagedLaunch() {
    Fixture fixture = fixture();
    when(fixture.runtime.findSenderRuntime(10L, 20L))
        .thenReturn(
            Optional.of(runtime(true, true, "ACTIVE", "SUSPENDED_VERIFICATION")));
    assertSuspendedOrPlatform(fixture, "MANAGED_EMAIL_SENDING_SUSPENDED");
  }

  @Test
  void sandboxAndAccountDisabledRejectLaunch() {
    Fixture fixture = fixture();
    when(fixture.runtime.findSenderRuntime(10L, 20L))
        .thenReturn(Optional.of(runtime(false, true, "ACTIVE", "ACTIVE")));
    assertSuspendedOrPlatform(fixture, "MANAGED_EMAIL_SENDING_SUSPENDED");

    when(fixture.runtime.findSenderRuntime(10L, 20L))
        .thenReturn(Optional.of(runtime(true, false, "ACTIVE", "ACTIVE")));
    assertSuspendedOrPlatform(fixture, "MANAGED_EMAIL_SENDING_SUSPENDED");
  }

  private void assertSuspendedOrPlatform(Fixture fixture, String message) {
    assertThatThrownBy(
            () -> fixture.guard.validateLaunch(10L, fixture.sender, fixture.connection, 1))
        .isInstanceOf(BadRequestException.class)
        .hasMessage(message);
  }

  private Fixture fixture() {
    ManagedEmailRuntimeRepository runtime = mock(ManagedEmailRuntimeRepository.class);
    EntitlementService entitlement = mock(EntitlementService.class);
    ManagedEmailUsageService usage = mock(ManagedEmailUsageService.class);
    ManagedFinancialExposureGuard exposure = mock(ManagedFinancialExposureGuard.class);
    ManagedEmailProviderBudgetService budget = mock(ManagedEmailProviderBudgetService.class);
    ManagedEmailRuntimeProperties properties = properties();
    when(entitlement.booleanValue(10L, "MANAGED_EMAIL_ENABLED")).thenReturn(true);
    when(usage.canReserve(10L, 1)).thenReturn(true);
    when(budget.hasUsableProviderSnapshot(any())).thenReturn(true);

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(30L);
    connection.setCompanyId(10L);
    connection.setManagementMode(BroadcastSenderManagementMode.MESSAGING_MANAGED);
    connection.setProvider(BroadcastSenderProvider.AMAZON_SES);
    BroadcastSender sender = new BroadcastSender();
    sender.setId(20L);
    sender.setCompanyId(10L);
    sender.setProvider(BroadcastSenderProvider.AMAZON_SES);
    return new Fixture(
        new ManagedEmailLaunchGuard(
            runtime,
            properties,
            entitlement,
            exposure,
            usage,
            new ManagedEmailAddressPolicy(),
            budget),
        runtime,
        properties,
        exposure,
        usage,
        connection,
        sender);
  }

  private ManagedEmailRuntimeProperties properties() {
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
    return properties;
  }

  private ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime(
      boolean productionAccess,
      boolean sendingEnabled,
      String tenantStatus,
      String domainStatus) {
    return new ManagedEmailRuntimeRepository.ManagedSenderRuntime(
        10L,
        20L,
        30L,
        50L,
        40L,
        "offers@example.test",
        "ACTIVE",
        "ACTIVE",
        "MESSAGING_MANAGED",
        productionAccess,
        sendingEnabled,
        tenantStatus,
        "ENABLED",
        "HEALTHY",
        "ESTABLISHED",
        domainStatus,
        "example.test",
        "us-east-1",
        "tenant-10",
        "cfg-10",
        20.0,
        10000.0,
        50.0,
        Instant.now());
  }

  private record Fixture(
      ManagedEmailLaunchGuard guard,
      ManagedEmailRuntimeRepository runtime,
      ManagedEmailRuntimeProperties properties,
      ManagedFinancialExposureGuard exposure,
      ManagedEmailUsageService usage,
      BroadcastSenderConnection connection,
      BroadcastSender sender) {}
}
