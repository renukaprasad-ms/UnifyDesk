package com.messaging.emailprovider.mailbox.inbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MailHeaderSummaryTests {
  private MailHeaderSummary.Summary parse(String raw) throws Exception {
    return MailHeaderSummary.read(
        new ByteArrayInputStream(raw.getBytes(StandardCharsets.ISO_8859_1)));
  }

  @Test
  void extractsBoundedHeadersWithoutReadingBody() throws Exception {
    var result =
        parse(
            "From: Alice <alice@example.com>\r\n"
                + "Subject: Quarterly\r\n results\r\n"
                + "Message-ID: <a.1@example.com>\r\n\r\nbody");
    assertEquals("Alice <alice@example.com>", result.from());
    assertEquals("Quarterly results", result.subject());
    assertEquals("<a.1@example.com>", result.messageId());
  }

  @Test
  void missingHeadersAreNull() throws Exception {
    var result = parse("MIME-Version: 1.0\r\n\r\nHello");
    assertNull(result.from());
    assertNull(result.subject());
  }

  @Test
  void refusesBadOrUnboundedHeaders() {
    assertThrows(Exception.class, () -> parse("From: x@example.com\n\nbody"));
    assertThrows(Exception.class, () -> parse("Subject: " + "A".repeat(1000) + "\r\n\r\nbody"));
    assertThrows(Exception.class, () -> parse("X-Test: " + "a".repeat(32768) + "\r\n\r\nbody"));
  }
}
