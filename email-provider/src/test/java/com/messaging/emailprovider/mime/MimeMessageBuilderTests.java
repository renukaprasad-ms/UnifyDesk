package com.messaging.emailprovider.mime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.messaging.emailprovider.mail.entity.EmailMessage;
import com.messaging.emailprovider.mail.entity.EmailRecipient;
import com.messaging.emailprovider.mail.enums.EmailRecipientType;
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

  private EmailRecipient recipient(Long id, String email, EmailRecipientType type) {
    EmailRecipient recipient = new EmailRecipient();
    recipient.setId(id);
    recipient.setRecipientAddress(email);
    recipient.setRecipientType(type);
    return recipient;
  }
}
