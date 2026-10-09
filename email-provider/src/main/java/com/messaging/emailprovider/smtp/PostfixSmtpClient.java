package com.messaging.emailprovider.smtp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PostfixSmtpClient {

  private final String host;
  private final int port;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;
  private final String ehloHost;

  public PostfixSmtpClient(
      @Value("${app.smtp.host:127.0.0.1}") String host,
      @Value("${app.smtp.port:25}") int port,
      @Value("${app.smtp.connect-timeout-ms:5000}") int connectTimeoutMs,
      @Value("${app.smtp.read-timeout-ms:10000}") int readTimeoutMs,
      @Value("${app.smtp.ehlo-host:localhost}") String ehloHost) {
    validateCommandValue(ehloHost, "EHLO host");
    this.host = host;
    this.port = port;
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
    this.ehloHost = ehloHost;
  }

  public SmtpDeliveryResult handoff(
      String envelopeFrom, String recipient, byte[] rawMessage) {
    validateCommandValue(envelopeFrom, "Envelope sender");
    validateCommandValue(recipient, "Recipient");

    // A failed/lost response after DATA may mean Postfix accepted this mail.
    boolean acceptanceUncertain = false;
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
      socket.setSoTimeout(readTimeoutMs);

      BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
      OutputStream output = socket.getOutputStream();

      SmtpResponse greeting = readResponse(reader);
      if (greeting.code() != 220) {
        return classify(greeting);
      }

      SmtpResponse ehlo = command(output, reader, "EHLO " + ehloHost);
      if (ehlo.code() / 100 != 2) {
        return classify(ehlo);
      }

      SmtpResponse mailFrom = command(output, reader, "MAIL FROM:<" + envelopeFrom + ">");
      if (mailFrom.code() / 100 != 2) {
        return classify(mailFrom);
      }

      SmtpResponse rcptTo = command(output, reader, "RCPT TO:<" + recipient + ">");
      if (rcptTo.code() / 100 != 2) {
        return classify(rcptTo);
      }

      SmtpResponse data = command(output, reader, "DATA");
      if (data.code() != 354) {
        return classify(data);
      }

      acceptanceUncertain = true;
      writeMessage(output, rawMessage);
      SmtpResponse accepted = readResponse(reader);
      try {
        command(output, reader, "QUIT");
      } catch (Exception ignored) {
        // Delivery ownership has already transferred after the successful DATA response.
      }
      if (accepted.code() / 100 == 2) {
        return new SmtpDeliveryResult(
            SmtpDeliveryOutcome.HANDED_OFF,
            Integer.toString(accepted.code()),
            accepted.message());
      }
      return classify(accepted);
    } catch (IOException exception) {
      return new SmtpDeliveryResult(
          acceptanceUncertain ? SmtpDeliveryOutcome.UNKNOWN : SmtpDeliveryOutcome.DEFERRED,
          null, safeMessage(exception));
    }
  }

  private SmtpResponse command(OutputStream output, BufferedReader reader, String command)
      throws IOException {
    output.write((command + "\r\n").getBytes(StandardCharsets.US_ASCII));
    output.flush();
    return readResponse(reader);
  }

  private void writeMessage(OutputStream output, byte[] rawMessage) throws IOException {
    String raw = new String(rawMessage, StandardCharsets.ISO_8859_1);
    boolean lineStart = true;
    for (int index = 0; index < raw.length(); index++) {
      char character = raw.charAt(index);
      if (lineStart && character == '.') {
        output.write('.');
      }
      output.write(String.valueOf(character).getBytes(StandardCharsets.ISO_8859_1));
      lineStart = character == '\n';
    }

    if (!raw.endsWith("\r\n")) {
      output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
    }
    output.write(".\r\n".getBytes(StandardCharsets.US_ASCII));
    output.flush();
  }

  private SmtpResponse readResponse(BufferedReader reader) throws IOException {
    String first = reader.readLine();
    if (first == null || first.length() < 3) {
      throw new IOException("SMTP connection closed without a valid response");
    }

    int code;
    try {
      code = Integer.parseInt(first.substring(0, 3));
    } catch (NumberFormatException exception) {
      throw new IOException("Invalid SMTP response: " + first, exception);
    }

    StringBuilder message = new StringBuilder(first);
    String line = first;
    while (line.length() > 3 && line.charAt(3) == '-') {
      line = reader.readLine();
      if (line == null) {
        break;
      }
      message.append(" | ").append(line);
    }
    return new SmtpResponse(code, message.toString());
  }

  private SmtpDeliveryResult classify(SmtpResponse response) {
    SmtpDeliveryOutcome outcome =
        response.code() >= 400 && response.code() < 500
            ? SmtpDeliveryOutcome.DEFERRED
            : SmtpDeliveryOutcome.FAILED;
    return new SmtpDeliveryResult(
        outcome, Integer.toString(response.code()), response.message());
  }

  private void validateCommandValue(String value, String label) {
    if (value == null
        || value.isBlank()
        || value.indexOf('\r') >= 0
        || value.indexOf('\n') >= 0) {
      throw new IllegalArgumentException(label + " is invalid");
    }
  }

  private String safeMessage(Exception exception) {
    String message = exception.getMessage();
    return message == null ? exception.getClass().getSimpleName() : message;
  }

  private record SmtpResponse(int code, String message) {}
}
