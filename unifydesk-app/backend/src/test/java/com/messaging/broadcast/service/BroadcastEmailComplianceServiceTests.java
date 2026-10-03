package com.messaging.broadcast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.contacts.consent.RecipientActionProperties;
import com.messaging.contacts.consent.RecipientActionTokenService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BroadcastEmailComplianceServiceTests {

  @Test
  void addsExactlyOneVisibleTextAndHtmlUnsubscribeAction() {
    Fixture fixture = fixture();
    BroadcastRecipient recipient = recipient();

    var result =
        fixture.service.apply(
            new BroadcastTemplateRenderer.RenderedContent(
                "Subject", "Hello", "<p>Hello</p>", List.of(), false, Map.of()),
            recipient);

    assertThat(result.bodyText())
        .containsOnlyOnce("Unsubscribe from marketing emails:")
        .contains("https://app.unifydesk.in/unsubscribe?token=opaque-token");
    assertThat(result.bodyHtml())
        .containsOnlyOnce("data-unifydesk-unsubscribe=\"1\"")
        .contains("https://app.unifydesk.in/unsubscribe?token=opaque-token");
  }

  @Test
  void createsHtmlAlternativeForTextOnlyBroadcast() {
    Fixture fixture = fixture();

    var result =
        fixture.service.apply(
            new BroadcastTemplateRenderer.RenderedContent(
                "Subject", "A & B", null, List.of(), false, Map.of()),
            recipient());

    assertThat(result.bodyHtml()).contains("A &amp; B");
    assertThat(result.bodyHtml()).contains("Unsubscribe from marketing emails");
  }

  @Test
  void reservedMsg91VariableOverridesRecipientSuppliedValue() {
    Fixture fixture = fixture();

    var result =
        fixture.service.apply(
            new BroadcastTemplateRenderer.RenderedContent(
                "Subject",
                "Hello",
                "<p>Hello</p>",
                List.of(),
                false,
                Map.of("unsubscribe_url", "https://attacker.example")),
            recipient());

    assertThat(result.templateVariables().get("unsubscribe_url"))
        .isEqualTo("https://app.unifydesk.in/unsubscribe?token=opaque-token");
  }

  @Test
  void standardsMetadataUsesBackendOneClickEndpointNotFrontendPage() {
    Fixture fixture = fixture();

    var result =
        fixture.service.apply(
            new BroadcastTemplateRenderer.RenderedContent(
                "Subject", "Hello", "<p>Hello</p>", List.of(), false, Map.of()),
            recipient());

    assertThat(result.compliance().standardOneClickHeaders())
        .containsEntry(
            "List-Unsubscribe",
            "<https://api.unifydesk.in/api/v1/public/recipient-actions/email/unsubscribe/opaque-token>")
        .containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
  }

  @Test
  void rejectsNonHttpsPublicOrigins() {
    RecipientActionTokenService tokens = mock(RecipientActionTokenService.class);
    when(tokens.issue(10L, 20L, 30L)).thenReturn("opaque-token");
    RecipientActionProperties properties = new RecipientActionProperties();
    properties.setFrontendBaseUrl("http://app.unifydesk.in");
    properties.setPublicApiBaseUrl("https://api.unifydesk.in");
    BroadcastEmailComplianceService service =
        new BroadcastEmailComplianceService(tokens, properties);

    assertThatThrownBy(
            () ->
                service.apply(
                    new BroadcastTemplateRenderer.RenderedContent(
                        "Subject", "Hello", null, List.of(), false, Map.of()),
                    recipient()))
        .isInstanceOf(IllegalStateException.class);
  }

  private Fixture fixture() {
    RecipientActionTokenService tokens = mock(RecipientActionTokenService.class);
    when(tokens.issue(10L, 20L, 30L)).thenReturn("opaque-token");
    RecipientActionProperties properties = new RecipientActionProperties();
    properties.setFrontendBaseUrl("https://app.unifydesk.in");
    properties.setPublicApiBaseUrl("https://api.unifydesk.in");
    return new Fixture(new BroadcastEmailComplianceService(tokens, properties));
  }

  private BroadcastRecipient recipient() {
    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setCompanyId(10L);
    recipient.setContactId(20L);
    recipient.setContactIdentityId(30L);
    return recipient;
  }

  private record Fixture(BroadcastEmailComplianceService service) {}
}
