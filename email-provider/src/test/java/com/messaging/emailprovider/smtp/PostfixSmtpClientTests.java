package com.messaging.emailprovider.smtp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class PostfixSmtpClientTests {

  @Test
  void preservesMessageAndDotStuffsData() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      CompletableFuture<String> received =
          CompletableFuture.supplyAsync(
              () -> {
                try (Socket socket = server.accept()) {
                  BufferedReader reader =
                      new BufferedReader(
                          new InputStreamReader(
                              socket.getInputStream(), StandardCharsets.ISO_8859_1));
                  OutputStream output = socket.getOutputStream();

                  write(output, "220 local.test ESMTP\r\n");
                  reader.readLine();
                  write(output, "250-local.test\r\n250 OK\r\n");
                  reader.readLine();
                  write(output, "250 2.1.0 OK\r\n");
                  reader.readLine();
                  write(output, "250 2.1.5 OK\r\n");
                  reader.readLine();
                  write(output, "354 End data\r\n");

                  StringBuilder data = new StringBuilder();
                  String line;
                  while ((line = reader.readLine()) != null && !line.equals(".")) {
                    data.append(line).append("\r\n");
                  }
                  write(output, "250 2.0.0 queued\r\n");
                  reader.readLine();
                  write(output, "221 Bye\r\n");
                  return data.toString();
                } catch (Exception exception) {
                  throw new RuntimeException(exception);
                }
              });

      PostfixSmtpClient client =
          new PostfixSmtpClient("127.0.0.1", server.getLocalPort(), 2000, 2000, "app.local");
      byte[] raw =
          ("From: sender@example.com\r\n"
                  + "To: user@example.net\r\n\r\n"
                  + "first\r\n"
                  + ".dot-line\r\n")
              .getBytes(StandardCharsets.ISO_8859_1);

      SmtpDeliveryResult result = client.handoff("sender@example.com", "user@example.net", raw);

      assertEquals(SmtpDeliveryOutcome.HANDED_OFF, result.outcome());
      String data = received.get();
      assertTrue(data.contains("first\r\n"));
      assertTrue(data.contains("..dot-line\r\n"));
    }
  }


  @Test
  void doesNotRetryWhenPostfixMayHaveAcceptedData() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      CompletableFuture<Void> peer =
          CompletableFuture.runAsync(
              () -> {
                try (Socket socket = server.accept()) {
                  BufferedReader reader =
                      new BufferedReader(
                          new InputStreamReader(
                              socket.getInputStream(), StandardCharsets.ISO_8859_1));
                  OutputStream output = socket.getOutputStream();
                  write(output, "220 local.test ESMTP\\r\\n");
                  reader.readLine();
                  write(output, "250 local.test\\r\\n");
                  reader.readLine();
                  write(output, "250 sender accepted\\r\\n");
                  reader.readLine();
                  write(output, "250 recipient accepted\\r\\n");
                  reader.readLine();
                  write(output, "354 send data\\r\\n");
                  while (true) {
                    String line = reader.readLine();
                    if (line == null || line.equals(".")) break;
                  }
                  // Socket closes without DATA acceptance confirmation.
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      PostfixSmtpClient client =
          new PostfixSmtpClient("127.0.0.1", server.getLocalPort(), 2000, 2000, "app.local");
      var result =
          client.handoff(
              "sender@example.com",
              "user@example.net",
              "From: sender@example.com\\r\\n\\r\\nHello\\r\\n".getBytes(StandardCharsets.ISO_8859_1));
      assertEquals(SmtpDeliveryOutcome.UNKNOWN, result.outcome());
      peer.get();
    }
  }

  private static void write(OutputStream output, String value) throws Exception {
    output.write(value.getBytes(StandardCharsets.US_ASCII));
    output.flush();
  }
}
