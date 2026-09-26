package com.messaging.managedemail.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagedEmailDeliveryGuardTests {

  @Test
  void tenantSuspensionAfterLaunchBlocksDelivery() {
    Fixture fixture = fixture();
    when(fixture.runtime.findSenderRuntime(10L, 20L))
        .thenReturn(Optional.of(runtime("offers@example.test", true, true, "SUSPENDED", "ACTIVE")));

    assertThat(fixture.eligible()).isEmpty();
  }

  @Test
  void domainSuspensionAfterLaunchBlocksDelivery() {
    Fixture fixture = fixture();
    when(fixture.runtime.findSenderRuntime(10L, 20L))
        .thenReturn(
            Optional.of(
                runtime(
                    "offers@example.test",
                    true,
                    true,
                    "ACTIVE",
                    "SUSPENDED_VERIFICATION")));

    assertThat(fixture.eligible()).isEmpty();
  }

  @Test
  void sandboxAndAccountSendingDisabledBlockDelivery() {
    Fixture fixture = fixture();
    when(fixture.runtime.findSenderRuntime(10L, 20L))
        .thenReturn(Optional.of(runtime("offers@example.test", false, true, "ACTIVE", "ACTIVE")));
    assertThat(fixture.eligible()).isEmpty();

    when(fixture.runtime.findSenderRuntime(10L, 20L))
        .thenReturn(Optional.of(runtime("offers@example.test", true, false, "ACTIVE", "ACTIVE")));
    assertThat(fixture.eligible()).isEmpty();
  }

  @Test
  void tamperedFromDomainBlocksDelivery() {
    Fixture fixture = fixture();
    when(fixture.runtime.findSenderRuntime(10L, 20L))
        .thenReturn(Optional.of(runtime("ceo@amazon.com", true, true, "ACTIVE", "ACTIVE")));

    assertThat(fixture.eligible()).isEmpty();
  }

  @Test
  void activeProductionSenderIsEligible() {
    Fixture fixture = fixture();
    when(fixture.runtime.findSenderRuntime(10L, 20L))
        .thenReturn(Optional.of(runtime("offers@example.test", true, true, "ACTIVE", "ACTIVE")));

    assertThat(fixture.eligible()).isPresent();
  }

  @Test
  void killSwitchBlocksDelivery() {
    Fixture fixture = fixture();
    fixture.properties.setSendingEnabled(false);

    assertThat(fixture.eligible()).isEmpty();
    verifyNoInteractions(fixture.runtime);
  }

  private Fixture fixture() {
    ManagedEmailRuntimeRepository runtime = mock(ManagedEmailRuntimeRepository.class);
    ManagedEmailProviderBudgetService budget = mock(ManagedEmailProviderBudgetService.class);
    when(budget.hasUsableProviderSnapshot(any())).thenReturn(true);
    ManagedEmailRuntimeProperties properties = properties();

    BroadcastRun run = new BroadcastRun();
    run.setId(60L);
    run.setCompanyId(10L);
    run.setSenderId(20L);
    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(30L);
    BroadcastSender sender = new BroadcastSender();
    sender.setId(20L);
    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setId(70L);
    recipient.setCompanyId(10L);
    recipient.setSenderId(20L);
    ManagedEmailDeliveryGuard guard =
        new ManagedEmailDeliveryGuard(
            runtime, properties, new ManagedEmailAddressPolicy(), budget);
    return new Fixture(guard, runtime, properties, run, connection, sender, recipient);
  }

  private ManagedEmailRuntimeProperties properties() {
    ManagedEmailRuntimeProperties properties = new ManagedEmailRuntimeProperties();
    properties.setSendingEnabled(true);
    properties.setRegion("us-east-1");
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
      String fromAddress,
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
        fromAddress,
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
      ManagedEmailDeliveryGuard guard,
      ManagedEmailRuntimeRepository runtime,
      ManagedEmailRuntimeProperties properties,
      BroadcastRun run,
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastRecipient recipient) {
    Optional<ManagedEmailRuntimeRepository.ManagedSenderRuntime> eligible() {
      return guard.eligible(run, connection, sender, recipient);
    }
  }
}
