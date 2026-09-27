package com.messaging.notification.platform.delivery.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import org.junit.jupiter.api.Test;

class NotificationEmailRendererTests {

  @Test
  void welcomeEscapesWorkspaceNameAndStripsHeaderControls() {
    NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
    NotificationEmailRenderer renderer = new NotificationEmailRenderer(properties);

    var email =
        renderer.companyWelcome(
            "Renuka", "<script>alert(1)</script>\r\nBcc: attacker@example.com");

    assertThat(email.subject()).doesNotContain("\r", "\n");
    assertThat(email.htmlBody()).doesNotContain("<script>");
    assertThat(email.htmlBody()).contains("&lt;script&gt;");
    assertThat(email.textBody()).contains("https://app.unifydesk.in/workspace");
  }

  @Test
  void digestContainsOnlyAggregateMetadata() {
    NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
    NotificationEmailRenderer renderer = new NotificationEmailRenderer(properties);

    var email = renderer.inboxDigest("Acme", 12);

    assertThat(email.subject()).contains("Acme");
    assertThat(email.textBody()).contains("12 unread inbox items");
    assertThat(email.textBody()).contains("/inbox/email");
    assertThat(email.textBody()).contains("does not include message content");
  }
}
