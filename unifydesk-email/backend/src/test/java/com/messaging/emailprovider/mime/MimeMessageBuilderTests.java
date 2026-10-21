package com.messaging.emailprovider.mime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.messaging.emailprovider.mail.entity.EmailMessage;
import com.messaging.emailprovider.mail.entity.EmailRecipient;
import com.messaging.emailprovider.mail.enums.EmailRecipientType;
import com.messaging.emailprovider.product.unsubscribe.UnsubscribeTokenService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class MimeMessageBuilderTests {

  private final MimeMessageBuilder builder = new MimeMessageBuilder();

  @Test
  void buildsMultipartMessageWithoutBccHeader() {
    EmailMessage message = new EmailMessage();
    message.setId(101L);
    message.setFromAddress("sender@example.com");
    message.setSubject("Hello");
    message.setTextBody("Plain");
    message.setHtmlBody("<b>HTML</b>");

    EmailRecipient to = recipient(201L, "to@example.net", EmailRecipientType.TO);
    EmailRecipient cc = recipient(202L, "cc@example.net", EmailRecipientType.CC);
    EmailRecipient bcc = recipient(203L, "bcc@example.net", EmailRecipientType.BCC);

    String raw =
        new String(
            builder.build(message, bcc, List.of(to, cc, bcc), "example.com"),
            StandardCharsets.ISO_8859_1);

    assertTrue(raw.contains("To: to@example.net\r\n"));
    assertTrue(raw.contains("Cc: cc@example.net\r\n"));
    assertFalse(raw.contains("Bcc:"));
    assertTrue(raw.contains("Content-Type: multipart/alternative;"));
    assertTrue(raw.contains("Message-ID: <101.203@example.com>"));
  }

  @Test
  void marketingMessagesIncludeSignedOneClickUnsubscribeAndRecipientSpecificId() {
    EmailMessage message = new EmailMessage();
    message.setId(101L);
    message.setFromAddress("sender@example.com");
    message.setSubject("Welcome");
    message.setTextBody("Hello");
    message.setMarketing(true);
    EmailRecipient recipient = recipient(22L, "a@example.net", EmailRecipientType.BCC);
    var signer =
        new UnsubscribeTokenService(
            "long-testing-only-signature-key-of-32-plus-characters",
            "https://api.email.unifydesk.in");
    String source =
        new String(
            new MimeMessageBuilder(signer)
                .build(message, recipient, List.of(recipient), "example.com"),
            StandardCharsets.ISO_8859_1);
    assertTrue(source.contains("List-Unsubscribe-Post: List-Unsubscribe=One-Click\r\n"));
    assertTrue(source.contains("List-Unsubscribe: <https://api.email.unifydesk.in/"));
    assertTrue(source.contains("Message-ID: <101.22@example.com>"));
    assertTrue(source.contains("To: a@example.net"));
  }

  private EmailRecipient recipient(Long id, String email, EmailRecipientType type) {
    EmailRecipient recipient = new EmailRecipient();
    recipient.setId(id);
    recipient.setRecipientAddress(email);
    recipient.setRecipientType(type);
    return recipient;
  }
}
