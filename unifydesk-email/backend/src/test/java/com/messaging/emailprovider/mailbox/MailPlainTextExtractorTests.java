package com.messaging.emailprovider.mailbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MailPlainTextExtractorTests {
  private final MailPlainTextExtractor extractor = new MailPlainTextExtractor();

  private MailPlainTextExtractor.PlainText parse(String mime) {
    return extractor.extract(new ByteArrayInputStream(mime.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void decodesQuotedPrintableUtf8Safely() {
    var result =
        parse(
            "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n\r\n"
                + "Hello =C3=A9quipe");
    assertTrue(result.available());
    assertEquals("Hello équipe", result.text());
    assertFalse(result.truncated());
  }

  @Test
  void selectsPlainTextPartButNeverHtml() {
    var result =
        parse(
            "MIME-Version: 1.0\r\n"
                + "Content-Type: multipart/alternative; boundary=\"abc\"\r\n\r\n"
                + "--abc\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n"
                + "Read this safe text.\r\n"
                + "--abc\r\nContent-Type: text/html\r\n\r\n"
                + "<script>bad()</script>\r\n--abc--\r\n");
    assertTrue(result.available());
    assertTrue(result.text().contains("Read this safe text."));
    assertFalse(result.text().contains("<script>"));
  }

  @Test
  void refusesHtmlOnlyMessage() {
    var result =
        parse(
            "Content-Type: text/html; charset=utf-8\r\n\r\n"
                + "<img src=\"https://example.com/track.gif\">");
    assertFalse(result.available());
    assertEquals("", result.text());
  }

  @Test
  void doesNotRenderNamedTextAttachment() {
    var result =
        parse(
            "MIME-Version: 1.0\r\n"
                + "Content-Type: multipart/mixed; boundary=\"m\"\r\n\r\n"
                + "--m\r\nContent-Type: text/plain; name=\"secrets.txt\"\r\n"
                + "Content-Disposition: attachment; filename=\"secrets.txt\"\r\n\r\n"
                + "ATTACHMENT_ONLY\r\n--m--\r\n");
    assertFalse(result.available());
  }

  @Test
  void ignoresPlainTextNestedInsideAnAttachedMultipart() {
    var result =
        parse(
            "MIME-Version: 1.0\r\n"
                + "Content-Type: multipart/mixed; boundary=\"outer\"\r\n\r\n"
                + "--outer\r\nContent-Type: multipart/alternative; boundary=\"inner\"\r\n"
                + "Content-Disposition: attachment; filename=\"attached-message.eml\"\r\n\r\n"
                + "--inner\r\nContent-Type: text/plain\r\n\r\n"
                + "PRIVATE_ATTACHMENT\r\n--inner--\r\n--outer--\r\n");
    assertFalse(result.available());
  }

  @Test
  void capsDecodedTextInMemory() {
    var result =
        parse(
            "Content-Type: text/plain; charset=utf-8\r\n\r\n"
                + "x".repeat(MailPlainTextExtractor.MAX_CHARS + 100));
    assertTrue(result.truncated());
    assertTrue(result.available());
    assertEquals(MailPlainTextExtractor.MAX_CHARS, result.text().length());
  }
}
