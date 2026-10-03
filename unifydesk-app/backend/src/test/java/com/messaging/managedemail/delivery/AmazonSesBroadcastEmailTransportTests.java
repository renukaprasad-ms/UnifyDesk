package com.messaging.managedemail.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.transport.AmazonSesBroadcastEmailTransport;
import com.messaging.broadcast.transport.BroadcastEmailCommand;
import com.messaging.broadcast.transport.BroadcastEmailComplianceMetadata;
import jakarta.mail.internet.InternetAddress;
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
    Fixture fixture = fixture(true);
    BroadcastSender sender = sender();
    sender.setFromAddress("stale-or-tampered@other.test");

    fixture.transport.send(connection(), sender, command());

    SendEmailRequest request = captured(fixture.client);
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
  void simpleMessageContainsStandardOneClickHeaders() {
    Fixture fixture = fixture(true);
    BroadcastEmailCommand command =
        new BroadcastEmailCommand(
            "recipient@example.test",
            "Subject",
            "Text",
            "<p>Text</p>",
            null,
            Map.of(),
            "<id@example.test>",
            "corr-70",
            10L,
            60L,
            20L,
            70L,
            new BroadcastEmailComplianceMetadata(
                "https://app.example/unsubscribe?token=token",
                "https://api.example/unsubscribe/token"));

    fixture.transport.send(connection(), sender(), command);

    Map<String, String> headers =
        captured(fixture.client).content().simple().headers().stream()
            .collect(Collectors.toMap(header -> header.name(), header -> header.value()));
    assertThat(headers)
        .containsEntry("List-Unsubscribe", "<https://api.example/unsubscribe/token>")
        .containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
  }

  @Test
  void quotedDisplayNameIsSanitizedIntoValidFromHeader() throws Exception {
    Fixture fixture = fixture(true);
    BroadcastSender sender = sender();
    sender.setDisplayName("Acme \"Offers\"");

    fixture.transport.send(connection(), sender, command());

    String from = captured(fixture.client).fromEmailAddress();
    assertThat(from).doesNotContain("\"");
    InternetAddress[] parsed = InternetAddress.parse(from, true);
    assertThat(parsed).hasSize(1);
    assertThat(parsed[0].getAddress()).isEqualTo("offers@example.test");
  }

  @Test
  void displayNameControlsCannotInjectHeaders() throws Exception {
    for (String displayName :
        new String[] {"Acme\nBcc: victim@example.com", "Acme\rOffers", "Acme\r\nOffers"}) {
      Fixture fixture = fixture(true);
      BroadcastSender sender = sender();
      sender.setDisplayName(displayName);

      fixture.transport.send(connection(), sender, command());

      String from = captured(fixture.client).fromEmailAddress();
      assertThat(from).doesNotContain("\r", "\n");
      InternetAddress[] parsed = InternetAddress.parse(from, true);
      assertThat(parsed).hasSize(1);
      assertThat(parsed[0].getAddress()).isEqualTo("offers@example.test");
    }
  }

  @Test
  void blankDisplayNameUsesPlainMailbox() {
    Fixture fixture = fixture(true);
    BroadcastSender sender = sender();
    sender.setDisplayName("   ");

    fixture.transport.send(connection(), sender, command());

    assertThat(captured(fixture.client).fromEmailAddress()).isEqualTo("offers@example.test");
  }

  @Test
  void customerManagedRequestRemainsUnchanged() {
    Fixture fixture = fixture(false);

    fixture.transport.send(connection(), sender(), command());

    SendEmailRequest request = captured(fixture.client);
    assertThat(request.fromEmailAddress()).isEqualTo("sender@example.test");
    assertThat(request.tenantName()).isNull();
    assertThat(request.configurationSetName()).isNull();
  }

  private Fixture fixture(boolean managed) {
    SesSendContextResolver resolver = mock(SesSendContextResolver.class);
    SesV2Client client = mock(SesV2Client.class);
    when(resolver.resolve(any(), any()))
        .thenReturn(
            managed
                ? new SesSendContext(
                    client, true, "tenant-10", "cfg-10", 40L, "offers@example.test")
                : new SesSendContext(client, false, null, null, null, null));
    when(client.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("provider-1").build());
    return new Fixture(new AmazonSesBroadcastEmailTransport(resolver), client);
  }

  private SendEmailRequest captured(SesV2Client client) {
    ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(client).sendEmail(captor.capture());
    return captor.getValue();
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

  private record Fixture(AmazonSesBroadcastEmailTransport transport, SesV2Client client) {}
}
