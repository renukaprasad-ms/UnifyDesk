package com.messaging.emailprovider.mailbox;

import static org.junit.jupiter.api.Assertions.*;

import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MailAttachmentReaderTests {
  private final MailAttachmentReader reader = new MailAttachmentReader();

  static final String MIME =
      """
      From: sender@example.test
      MIME-Version: 1.0
      Content-Type: multipart/mixed; boundary=files

      --files
      Content-Type: text/plain; charset=UTF-8

      Message body
      --files
      Content-Type: application/octet-stream; name="report.txt"
      Content-Disposition: attachment; filename="../../report.txt"
      Content-Transfer-Encoding: base64

      aGVsbG8=
      --files
      Content-Type: text/html
      Content-Disposition: inline; filename="untrusted.html"
      Content-Transfer-Encoding: quoted-printable

      <script>alert(1)</script>
      --files--
      """
          .replace("\n", "\r\n");

  @Test
  void listsAndDecodesOnlyExplicitLeafAttachments() {
    var files = reader.list(input(MIME));
    assertEquals(2, files.size());
    assertEquals(2, files.get(0).part());
    assertEquals("report.txt", files.get(0).filename());
    assertEquals(5, files.get(0).sizeBytes());
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    reader.download(input(MIME), 2, bytes);
    assertEquals("hello", bytes.toString(StandardCharsets.UTF_8));
    bytes.reset();
    reader.download(input(MIME), 3, bytes);
    assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("<script>"));
    // Bytes are downloadable; callers must always force attachment/octet-stream.
    assertThrows(
        NotFoundException.class,
        () -> reader.download(input(MIME), 1, new ByteArrayOutputStream()));
    assertThrows(
        NotFoundException.class,
        () -> reader.download(input(MIME), 128, new ByteArrayOutputStream()));
  }

  @Test
  void boundsParserAndSanitizesUntrustedNames() {
    assertEquals("evil__.html", MailAttachmentReader.safeFilename("../evil\r\n.html", 1));
    assertEquals("attachment-1.bin", MailAttachmentReader.safeFilename("..", 1));
    assertEquals("hello.txt", MailAttachmentReader.safeFilename("=?UTF-8?B?aGVsbG8udHh0?=", 1));
    StringBuilder mime = new StringBuilder("Content-Type: multipart/mixed; boundary=x\r\n\r\n");
    for (int i = 0; i < 140; i++) mime.append("--x\r\nContent-Type: text/plain\r\n\r\nx\r\n");
    mime.append("--x--\r\n");
    assertThrows(ServiceUnavailableException.class, () -> reader.list(input(mime.toString())));
  }

  private static ByteArrayInputStream input(String value) {
    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
  }
}
