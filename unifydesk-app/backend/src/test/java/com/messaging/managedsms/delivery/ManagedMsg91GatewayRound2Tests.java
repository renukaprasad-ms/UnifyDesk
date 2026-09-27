package com.messaging.managedsms.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.transport.BroadcastTransportException;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagedMsg91GatewayRound2Tests {

  @Test
  void runtimeProviderModeMustMatchConfiguredTopologyBeforeCredentialLookup() {
    ManagedSmsRuntimeProperties properties = properties("CHILD_PER_COMPANY");
    ManagedSmsProviderCredentialResolver resolver = mock(ManagedSmsProviderCredentialResolver.class);
    ManagedMsg91Gateway gateway = new ManagedMsg91Gateway(new ObjectMapper(), properties, resolver);

    assertThatThrownBy(
            () ->
                gateway.send(
                    runtime("SHARED_MASTER", "master"),
                    "919999999999",
                    Map.of(),
                    List.of(),
                    "abc123"))
        .isInstanceOfSatisfying(
            BroadcastTransportException.class,
            exception ->
                assertThat(exception.code())
                    .isEqualTo("MANAGED_SMS_PROVIDER_CAPACITY_UNAVAILABLE"));

    verifyNoInteractions(resolver);
  }

  @Test
  void childModeWithoutExactChildCredentialFailsClosedBeforeNetworkSubmission() {
    ManagedSmsRuntimeProperties properties = properties("CHILD_PER_COMPANY");
    ManagedSmsProviderCredentialResolver resolver = mock(ManagedSmsProviderCredentialResolver.class);
    when(resolver.resolve("CHILD_PER_COMPANY", "client-a")).thenReturn(Optional.empty());
    ManagedMsg91Gateway gateway = new ManagedMsg91Gateway(new ObjectMapper(), properties, resolver);

    assertThatThrownBy(
            () ->
                gateway.send(
                    runtime("CHILD_PER_COMPANY", "client-a"),
                    "919999999999",
                    Map.of(),
                    List.of(),
                    "abc123"))
        .isInstanceOfSatisfying(
            BroadcastTransportException.class,
            exception -> {
              assertThat(exception.code())
                  .isEqualTo("MANAGED_SMS_PROVIDER_CAPACITY_UNAVAILABLE");
              assertThat(exception.getMessage())
                  .isEqualTo("Managed SMS provider capacity is unavailable");
            });

    verify(resolver).resolve("CHILD_PER_COMPANY", "client-a");
  }

  private ManagedSmsRuntimeProperties properties(String mode) {
    ManagedSmsRuntimeProperties properties = new ManagedSmsRuntimeProperties();
    properties.setEnabled(true);
    properties.setPlatformSendingEnabled(true);
    properties.setProvider("MSG91_SMS");
    properties.setProviderMode(mode);
    return properties;
  }

  private ManagedSmsRuntimeRepository.DeliveryRuntime runtime(String mode, String account) {
    Instant now = Instant.now();
    return new ManagedSmsRuntimeRepository.DeliveryRuntime(
        10L,
        60L,
        20L,
        30L,
        40L,
        now.minusSeconds(864000),
        "READY",
        true,
        now,
        41L,
        "pe",
        "ACTIVE",
        "ACTIVE",
        now,
        now.plusSeconds(3600),
        42L,
        "ACMEIN",
        "SERVICE",
        "ACTIVE",
        now,
        now.plusSeconds(3600),
        43L,
        44L,
        "dlt",
        "Body",
        "fingerprint",
        "[]",
        "{}",
        "ACTIVE",
        now,
        now.plusSeconds(3600),
        45L,
        mode,
        account,
        "provider-template",
        "provider-flow",
        "rules",
        "ACTIVE",
        now,
        "ACTIVE",
        true,
        "MESSAGING_MANAGED",
        "ACTIVE");
  }
}
