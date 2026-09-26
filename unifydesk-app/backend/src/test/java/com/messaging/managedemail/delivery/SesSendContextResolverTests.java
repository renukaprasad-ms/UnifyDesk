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

    BroadcastSenderConnection connection =
        connection(BroadcastSenderManagementMode.CUSTOMER_MANAGED);
    BroadcastSender sender = sender();

    SesSendContext context = resolver.resolve(connection, sender);

    assertThat(context.managed()).isFalse();
    assertThat(context.client()).isSameAs(client);
    verify(customer).client(connection);
    verifyNoInteractions(managed, runtime, budget);
  }

  @Test
  void managedSesUsesPlatformCredentialsAndPersistedTenantContext() {
    AmazonSesClientManager customer = mock(AmazonSesClientManager.class);
    ManagedSesClientProvider managed = mock(ManagedSesClientProvider.class);
    ManagedEmailRuntimeRepository runtime = mock(ManagedEmailRuntimeRepository.class);
    ManagedEmailProviderBudgetService budget = mock(ManagedEmailProviderBudgetService.class);
    ManagedEmailRuntimeProperties properties = new ManagedEmailRuntimeProperties();
    properties.setRegion("us-east-1");
    SesV2Client client = mock(SesV2Client.class);
    when(managed.client("us-east-1")).thenReturn(client);
    when(runtime.findSenderRuntime(10L, 20L)).thenReturn(Optional.of(activeRuntime("offers@example.test")));
    when(budget.hasUsableProviderSnapshot(any())).thenReturn(true);
    SesSendContextResolver resolver =
        new SesSendContextResolver(
            customer,
            managed,
            runtime,
            properties,
            new ManagedEmailAddressPolicy(),
            budget);

    SesSendContext context =
        resolver.resolve(connection(BroadcastSenderManagementMode.MESSAGING_MANAGED), sender());

    assertThat(context.managed()).isTrue();
    assertThat(context.client()).isSameAs(client);
    assertThat(context.tenantName()).isEqualTo("tenant-10");
    assertThat(context.configurationSetName()).isEqualTo("cfg-10");
    assertThat(context.domainId()).isEqualTo(40L);
    assertThat(context.managedFromAddress()).isEqualTo("offers@example.test");
    verifyNoInteractions(customer);
  }

  @Test
  void tamperedFromDomainFailsBeforeManagedClientIsUsed() {
    AmazonSesClientManager customer = mock(AmazonSesClientManager.class);
    ManagedSesClientProvider managed = mock(ManagedSesClientProvider.class);
    ManagedEmailRuntimeRepository runtime = mock(ManagedEmailRuntimeRepository.class);
    ManagedEmailProviderBudgetService budget = mock(ManagedEmailProviderBudgetService.class);
    ManagedEmailRuntimeProperties properties = new ManagedEmailRuntimeProperties();
    properties.setRegion("us-east-1");
    when(runtime.findSenderRuntime(10L, 20L)).thenReturn(Optional.of(activeRuntime("ceo@amazon.com")));
    when(budget.hasUsableProviderSnapshot(any())).thenReturn(true);
    SesSendContextResolver resolver =
        new SesSendContextResolver(
            customer,
            managed,
            runtime,
            properties,
            new ManagedEmailAddressPolicy(),
            budget);

    assertThatThrownBy(
            () ->
                resolver.resolve(
                    connection(BroadcastSenderManagementMode.MESSAGING_MANAGED), sender()))
        .isInstanceOf(BroadcastTransportException.class);
    verifyNoInteractions(managed, customer);
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

  private ManagedEmailRuntimeRepository.ManagedSenderRuntime activeRuntime(String fromAddress) {
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
        "example.test",
        "us-east-1",
        "tenant-10",
        "cfg-10",
        20.0,
        10000.0,
        100.0,
        Instant.now());
  }
}
