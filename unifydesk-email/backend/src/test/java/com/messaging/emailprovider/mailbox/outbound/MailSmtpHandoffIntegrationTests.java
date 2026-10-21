package com.messaging.emailprovider.mailbox.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.messaging.emailprovider.smtp.PostfixSmtpClient;
import com.messaging.emailprovider.smtp.SmtpDeliveryOutcome;
import com.messaging.emailprovider.smtp.SmtpDeliveryResult;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Tests the actual SMTP client with a local in-process fake MTA. */
class MailSmtpHandoffIntegrationTests {
  @Test
  void acceptedDataIsReportedAsPostfixHandoffOnly() throws Exception {
    assertEquals(SmtpDeliveryOutcome.HANDED_OFF, exchange("ACCEPT").outcome());
  }

  @Test
  void responseLostAfterDataMustNotBeRetriedAutomatically() throws Exception {
    assertEquals(SmtpDeliveryOutcome.UNKNOWN, exchange("DROP_AFTER_DATA").outcome());
  }

  @Test
  void temporaryRcptRejectionIsExplicitlyDeferred() throws Exception {
    assertEquals(SmtpDeliveryOutcome.DEFERRED, exchange("DEFER_RCPT").outcome());
  }

  private static SmtpDeliveryResult exchange(String mode) throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(4000);
      CompletableFuture<Void> responder =
          CompletableFuture.runAsync(
              () -> {
                try (var socket = server.accept()) {
                  socket.setSoTimeout(4000);
                  var in =
                      new BufferedReader(
                          new InputStreamReader(
                              socket.getInputStream(), StandardCharsets.US_ASCII));
                  var out = socket.getOutputStream();
                  reply(out, "220 mail.local ESMTP");
                  assertTrue(in.readLine().startsWith("EHLO "));
                  reply(out, "250 mail.local");
                  assertTrue(in.readLine().startsWith("MAIL FROM:<"));
                  reply(out, "250 OK");
                  assertTrue(in.readLine().startsWith("RCPT TO:<"));
                  if ("DEFER_RCPT".equals(mode)) {
                    reply(out, "450 4.2.0 try later");
                    return;
                  }
                  reply(out, "250 OK");
                  assertEquals("DATA", in.readLine());
                  reply(out, "354 End data with <CRLF>.<CRLF>");
                  while (!".".equals(in.readLine())) {
                    // Consume the DATA block. Test messages are tiny and bounded.
                  }
                  if ("DROP_AFTER_DATA".equals(mode)) return;
                  reply(out, "250 2.0.0 queued");
                  assertEquals("QUIT", in.readLine());
                  reply(out, "221 Bye");
                } catch (Exception failure) {
                  throw new IllegalStateException(failure);
                }
              });
      var client =
          new PostfixSmtpClient("127.0.0.1", server.getLocalPort(), 1500, 2000, "mail.local");
      var result =
          client.handoff(
              "ud+1.2.abcdefabcdefabcd@bounces.example.com",
              "recipient@example.com",
              "From: support@example.com\r\nTo: recipient@example.com\r\n\r\nhello\r\n"
                  .getBytes(StandardCharsets.US_ASCII));
      responder.get(5, TimeUnit.SECONDS);
      return result;
    }
  }

  private static void reply(java.io.OutputStream out, String response) throws Exception {
    out.write((response + "\r\n").getBytes(StandardCharsets.US_ASCII));
    out.flush();
  }
}
