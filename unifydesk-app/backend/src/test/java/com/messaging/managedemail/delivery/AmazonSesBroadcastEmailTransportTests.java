package com.messaging.managedemail.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.transport.AmazonSesBroadcastEmailTransport;
import com.messaging.broadcast.transport.BroadcastEmailCommand;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

class AmazonSesBroadcastEmailTransportTests {

  @Test
  void managedRequestContainsTenantConfigurationSetSafeTagsAndPersistedFrom() {
    SesSendContextResolver resolver = mock(SesSendContextResolver.class);
    SesV2Client client = mock(SesV2Client.class);
    when(resolver.resolve(any(), any()))
        .thenReturn(
            new SesSendContext(
                client, true, "tenant-10", "cfg-10", 40L, "offers@example.test"));
    when(client.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("provider-1").build());

    BroadcastSender sender = sender();
    sender.setFromAddress("stale-or-tampered@other.test");
    AmazonSesBroadcastEmailTransport transport = new AmazonSesBroadcastEmailTransport(resolver);
    transport.send(connection(), sender, command());

    ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(client).sendEmail(captor.capture());
    SendEmailRequest request = captor.getValue();
    assertThat(request.fromEmailAddress()).isEqualTo("offers@example.test");
    assertThat(request.tenantName()).isEqualTo("tenant-10");
    assertThat(request.configurationSetName()).isEqualTo("cfg-10");
    Map<String, String> tags =
        request.emailTags().stream()
            .collect(Collectors.toMap(tag -> tag.name(), tag -> tag.value()));
    assertThat(tags)
        .containsEntry("ud_company_id", "10")
        .containsEntry("ud_run_id", "60")
        .containsEntry("ud_sender_id", "20")
        .containsEntry("ud_domain_id", "40")
        .containsEntry("ud_recipient_id", "70")
        .containsEntry("ud_correlation", "corr-70");
  }

  @Test
  void customerManagedRequestRemainsUnchanged() {
    SesSendContextResolver resolver = mock(SesSendContextResolver.class);
    SesV2Client client = mock(SesV2Client.class);
    when(resolver.resolve(any(), any()))
        .thenReturn(new SesSendContext(client, false, null, null, null, null));
    when(client.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("provider-2").build());

    AmazonSesBroadcastEmailTransport transport = new AmazonSesBroadcastEmailTransport(resolver);
    transport.send(connection(), sender(), command());

    ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(client).sendEmail(captor.capture());
    assertThat(captor.getValue().fromEmailAddress()).isEqualTo("sender@example.test");
    assertThat(captor.getValue().tenantName()).isNull();
    assertThat(captor.getValue().configurationSetName()).isNull();
  }

  private BroadcastSenderConnection connection() {
    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(30L);
    connection.setCompanyId(10L);
    return connection;
  }

  private BroadcastSender sender() {
    BroadcastSender sender = new BroadcastSender();
    sender.setId(20L);
    sender.setCompanyId(10L);
    sender.setFromAddress("sender@example.test");
    return sender;
  }

  private BroadcastEmailCommand command() {
    return new BroadcastEmailCommand(
        "recipient@example.test",
        "Subject",
        "Text",
        null,
        null,
        Map.of(),
        "<id@example.test>",
        "corr-70",
        10L,
        60L,
        20L,
        70L);
  }
}
