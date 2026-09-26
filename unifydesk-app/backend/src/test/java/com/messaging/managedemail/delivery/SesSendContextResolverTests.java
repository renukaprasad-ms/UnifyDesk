package com.messaging.managedemail.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.ses.AmazonSesClientManager;
import com.messaging.broadcast.transport.BroadcastTransportException;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sesv2.SesV2Client;

class SesSendContextResolverTests {

  @Test
  void customerManagedSesStillUsesCustomerCredentialPath() {
    AmazonSesClientManager customer = mock(AmazonSesClientManager.class);
    ManagedSesClientProvider managed = mock(ManagedSesClientProvider.class);
    ManagedEmailRuntimeRepository runtime = mock(ManagedEmailRuntimeRepository.class);
    ManagedEmailProviderBudgetService budget = mock(ManagedEmailProviderBudgetService.class);
    ManagedEmailRuntimeProperties properties = new ManagedEmailRuntimeProperties();
    SesV2Client client = mock(SesV2Client.class);
    when(customer.client(any())).thenReturn(client);
    SesSendContextResolver resolver =
        new SesSendContextResolver(
            customer,
            managed,
            runtime,
            properties,
            new ManagedEmailAddressPolicy(),
            budget);

    SesSendContext context =
        resolver.resolve(connection(BroadcastSenderManagementMode.CUSTOMER_MANAGED), sender());

    assertThat(context.managed()).isFalse();
    assertThat(context.client()).isSameAs(client);
    verify(customer).client(any());
    verifyNoInteractions(managed, runtime, budget);
  }

  @Test
  void managedSesUsesStrictPersistedMailboxAndPlatformCredentials() {
    Fixture fixture = fixture("offers@example.test", "example.test");

    SesSendContext context =
        fixture.resolver.resolve(
            connection(BroadcastSenderManagementMode.MESSAGING_MANAGED), sender());

    assertThat(context.managed()).isTrue();
    assertThat(context.client()).isSameAs(fixture.client);
    assertThat(context.managedFromAddress()).isEqualTo("offers@example.test");
    verify(fixture.managed).client("us-east-1");
  }

  @Test
  void everyMalformedPersistedMailboxFailsBeforeSesClientInvocation() {
    String[] invalid = {
      "ceo@amazon.com",
      "bad..local@example.test",
      ".bad@example.test",
      "bad.@example.test",
      "bad @example.test",
      "bad@example.test extra",
      "Name <offers@example.test>",
      "bad\rexample@example.test",
      "bad\nexample@example.test",
      "bad@@example.test",
      "bad@example..test",
      "bad@-example.test",
      "bad@example-.test",
      "\"offers\"@example.test",
      " offers@example.test",
      "offers@example.test "
    };

    for (String address : invalid) {
      Fixture fixture = fixture(address, "example.test");

      assertThatThrownBy(
              () ->
                  fixture.resolver.resolve(
                      connection(BroadcastSenderManagementMode.MESSAGING_MANAGED), sender()))
          .isInstanceOf(BroadcastTransportException.class);

      verifyNoInteractions(fixture.managed);
    }
  }

  @Test
  void normalizedIdnDomainCanMatchBoundAsciiDomain() {
    Fixture fixture = fixture("offers@bücher.example", "xn--bcher-kva.example");

    SesSendContext context =
        fixture.resolver.resolve(
            connection(BroadcastSenderManagementMode.MESSAGING_MANAGED), sender());

    assertThat(context.managedFromAddress()).isEqualTo("offers@bücher.example");
    verify(fixture.managed).client("us-east-1");
  }

  private Fixture fixture(String fromAddress, String boundDomain) {
    AmazonSesClientManager customer = mock(AmazonSesClientManager.class);
    ManagedSesClientProvider managed = mock(ManagedSesClientProvider.class);
    ManagedEmailRuntimeRepository runtime = mock(ManagedEmailRuntimeRepository.class);
    ManagedEmailProviderBudgetService budget = mock(ManagedEmailProviderBudgetService.class);
    ManagedEmailRuntimeProperties properties = new ManagedEmailRuntimeProperties();
    properties.setRegion("us-east-1");
    SesV2Client client = mock(SesV2Client.class);
    ManagedEmailRuntimeRepository.ManagedSenderRuntime senderRuntime =
        activeRuntime(fromAddress, boundDomain);
    when(runtime.findSenderRuntime(10L, 20L)).thenReturn(Optional.of(senderRuntime));
    when(budget.hasUsableProviderSnapshot(senderRuntime)).thenReturn(true);
    when(managed.client("us-east-1")).thenReturn(client);

    SesSendContextResolver resolver =
        new SesSendContextResolver(
            customer,
            managed,
            runtime,
            properties,
            new ManagedEmailAddressPolicy(),
            budget);
    return new Fixture(resolver, managed, client);
  }

  private BroadcastSenderConnection connection(BroadcastSenderManagementMode mode) {
    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(30L);
    connection.setCompanyId(10L);
    connection.setManagementMode(mode);
    return connection;
  }

  private BroadcastSender sender() {
    BroadcastSender sender = new BroadcastSender();
    sender.setId(20L);
    sender.setCompanyId(10L);
    return sender;
  }

  private ManagedEmailRuntimeRepository.ManagedSenderRuntime activeRuntime(
      String fromAddress, String domain) {
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
        true,
        true,
        "ACTIVE",
        "ENABLED",
        "HEALTHY",
        "ESTABLISHED",
        "ACTIVE",
        domain,
        "us-east-1",
        "tenant-10",
        "cfg-10",
        20.0,
        10000.0,
        100.0,
        Instant.now());
  }

  private record Fixture(
      SesSendContextResolver resolver,
      ManagedSesClientProvider managed,
      SesV2Client client) {}
}
