package com.messaging.managedsms.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import com.messaging.managedsms.safety.ManagedSmsProviderSafetyService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ManagedSmsDeliveryGuardRound2Tests {

  private final ManagedSmsRuntimeProperties properties = properties();
  private final ManagedSmsDeliveryGuard guard =
      new ManagedSmsDeliveryGuard(
          mock(ManagedSmsRuntimeRepository.class),
          properties,
          mock(ManagedSmsProviderSafetyService.class),
          mock(org.springframework.jdbc.core.JdbcTemplate.class),
          new com.messaging.managedsms.metareach.MetaReachProperties());

  @Test
  void activeStatusesStillFailWhenAnyComplianceFactExpires() {
    Instant now = Instant.now();
    Instant future = now.plusSeconds(600);
    Instant recent = now.minusSeconds(60);
    Instant old = now.minus(Duration.ofHours(2));

    assertThat(guard.fresh(runtime(recent, future, future, future, recent, "flow"), now))
        .isTrue();
    assertThat(guard.fresh(runtime(recent, now.minusSeconds(1), future, future, recent, "flow"), now))
        .isFalse();
    assertThat(guard.fresh(runtime(recent, future, now.minusSeconds(1), future, recent, "flow"), now))
        .isFalse();
    assertThat(guard.fresh(runtime(recent, future, future, now.minusSeconds(1), recent, "flow"), now))
        .isFalse();
    assertThat(guard.fresh(runtime(old, future, future, future, recent, "flow"), now))
        .isFalse();
    assertThat(guard.fresh(runtime(recent, future, future, future, old, "flow"), now))
        .isFalse();
  }

  @Test
  void runtimeReadinessRequiresFlowReference() {
    Instant now = Instant.now();
    ManagedSmsRuntimeRepository.DeliveryRuntime runtime =
        runtime(
            now,
            now.plusSeconds(600),
            now.plusSeconds(600),
            now.plusSeconds(600),
            now,
            "");
    assertThat(runtime.currentlySendable()).isFalse();
  }

  private ManagedSmsRuntimeProperties properties() {
    ManagedSmsRuntimeProperties value = new ManagedSmsRuntimeProperties();
    value.setComplianceStaleAfter(Duration.ofHours(1));
    return value;
  }

  private ManagedSmsRuntimeRepository.DeliveryRuntime runtime(
      Instant tenantSync,
      Instant profileStale,
      Instant headerStale,
      Instant templateStale,
      Instant bindingSync,
      String flow) {
    Instant now = Instant.now();
    return new ManagedSmsRuntimeRepository.DeliveryRuntime(
        10L,
        60L,
        20L,
        30L,
        40L,
        now.minus(Duration.ofDays(10)),
        "READY",
        true,
        tenantSync,
        41L,
        "pe",
        "ACTIVE",
        "ACTIVE",
        now,
        profileStale,
        42L,
        "ACMEIN",
        "SERVICE",
        "ACTIVE",
        now,
        headerStale,
        43L,
        44L,
        "dlt",
        "Body",
        "fingerprint",
        "[]",
        "{}",
        "ACTIVE",
        now,
        templateStale,
        45L,
        "CHILD_PER_COMPANY",
        "client-a",
        "provider-template",
        flow,
        "rules",
        "ACTIVE",
        bindingSync,
        "ACTIVE",
        true,
        "MESSAGING_MANAGED",
        "ACTIVE");
  }
}
