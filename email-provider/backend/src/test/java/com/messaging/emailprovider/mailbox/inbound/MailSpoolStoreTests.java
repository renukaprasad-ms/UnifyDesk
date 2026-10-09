package com.messaging.emailprovider.mailbox.inbound;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.messaging.emailprovider.common.exception.BadRequestException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MailSpoolStoreTests {
  @TempDir Path persistent;

  @Test
  void streamsMIMEToDurableObjectAndDeletesDuplicates() throws Exception {
    MailSpoolStore store = new MailSpoolStore(persistent.toString(), 4096);
    byte[] mime = "From: a@example.com\r\nTo: b@example.com\r\n\r\nhello"
        .getBytes(StandardCharsets.US_ASCII);
    MailSpoolStore.Stored file = store.write(new ByteArrayInputStream(mime));
    assertEquals(mime.length, file.bytes());
    assertEquals(64, file.sha256().length());
    assertArrayEquals(mime, Files.readAllBytes(persistent.resolve(file.key())));
    store.delete(file);
    assertTrue(Files.notExists(persistent.resolve(file.key())));
  }

  @Test
  void rejectsOversizedAndEmptyStreamsWithoutLeavingPartialFiles() throws Exception {
    MailSpoolStore store = new MailSpoolStore(persistent.toString(), 4);
    assertThrows(BadRequestException.class,
        () -> store.write(new ByteArrayInputStream(new byte[5])));
    assertThrows(BadRequestException.class,
        () -> store.write(new ByteArrayInputStream(new byte[0])));
    try (var files = Files.list(persistent)) {
      assertEquals(0, files.count());
    }
  }
}
