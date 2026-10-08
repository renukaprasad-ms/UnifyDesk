package com.messaging.channel.zoho;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoAttachment;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoMessage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZohoMailMessageMapperTests {

  private final ZohoMailMessageMapper mapper = new ZohoMailMessageMapper();

  @Test
  void mapsAddressesThreadTimestampReadStateAndAttachments() {
    ZohoMessage message =
        new ZohoMessage(
            "1700000000001",
            "1700000000099",
            "90001",
            "Subject",
            "Preview",
            "Sender Name",
            "Sender Name <sender@example.com>",
            "Alice <alice@example.com>, bob@example.com",
            "cc@example.com",
            "",
            "1780000000000",
            "1780000001000",
            "read",
            true);

    var result =
        mapper.map(
            message,
            "<p>Hello</p>",
            EmailFolder.INBOX,
            List.of(
                new ZohoAttachment(
                    "55", "invoice.pdf", 1234L, null, Boolean.FALSE)));

    assertThat(result.messageId()).isEqualTo("1700000000001");
    assertThat(result.threadId()).isEqualTo("1700000000099");
    assertThat(result.fromAddress()).isEqualTo("sender@example.com");
    assertThat(result.fromDisplayName()).isEqualTo("Sender Name");
    assertThat(result.to()).containsExactly("alice@example.com", "bob@example.com");
    assertThat(result.cc()).containsExactly("cc@example.com");
    assertThat(result.read()).isTrue();
    assertThat(result.sentAt()).isEqualTo(Instant.ofEpochMilli(1780000001000L));
    assertThat(result.attachments()).hasSize(1);
    assertThat(result.attachments().getFirst().attachmentId()).isEqualTo("55");
  }

  @Test
  void fallsBackToMessageIdWhenThreadIdIsMissing() {
    ZohoMessage message =
        new ZohoMessage(
            "101",
            null,
            "90001",
            null,
            null,
            null,
            "sender@example.com",
            null,
            null,
            null,
            null,
            null,
            null,
            false);

    var result = mapper.map(message, null, EmailFolder.SENT, List.of());

    assertThat(result.threadId()).isEqualTo("101");
    assertThat(result.folder()).isEqualTo(EmailFolder.SENT);
  }
}
