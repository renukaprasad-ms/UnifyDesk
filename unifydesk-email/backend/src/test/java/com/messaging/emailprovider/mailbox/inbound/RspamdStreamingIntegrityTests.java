package com.messaging.emailprovider.mailbox.inbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RspamdStreamingIntegrityTests {
  @Test
  void acceptsOnlyIntegrityMatchingMessages() throws Exception {
    byte[] raw = "From: test@example.com\r\n\r\nsafe".getBytes(StandardCharsets.US_ASCII);
    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
    AtomicInteger requests = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/checkv2",
        exchange -> {
          requests.incrementAndGet();
          try {
            exchange.getRequestBody().readAllBytes();
            byte[] payload =
                "{\"action\":\"no action\",\"score\":0.2,\"required_score\":6}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
          } finally {
            exchange.close();
          }
        });
    server.start();
    try {
      RspamdSpamScanner scanner =
          new RspamdSpamScanner(
              "http://127.0.0.1:" + server.getAddress().getPort() + "/checkv2",
              JsonMapper.builder().build());
      assertEquals(
          RspamdSpamScanner.Decision.INBOX,
          scanner.scan(new ByteArrayInputStream(raw), raw.length, digest));
      assertEquals(1, requests.get());
      ServiceUnavailableException failure =
          assertThrows(
              ServiceUnavailableException.class,
              () -> scanner.scan(new ByteArrayInputStream(raw), raw.length, "0".repeat(64)));
      // Disconnecting after the integrity failure can abort the HTTP request before the
      // server sees it. Assert the integrity rejection rather than server scheduling.
      assertEquals("MIME changed after antivirus scan", failure.getMessage());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void refusesExternalRspamdEndpoint() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new RspamdSpamScanner(
                "http://203.0.113.1:11333/checkv2", JsonMapper.builder().build()));
  }
}
