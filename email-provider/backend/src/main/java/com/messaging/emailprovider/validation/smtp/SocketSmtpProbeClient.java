package com.messaging.emailprovider.validation.smtp;

import com.messaging.emailprovider.validation.enums.SmtpProbeStatus;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SocketSmtpProbeClient implements SmtpProbeClient {

  private final int connectTimeoutMs;
  private final int readTimeoutMs;
  private final String ehloHost;
  private final String mailFrom;

  public SocketSmtpProbeClient(
      @Value("${app.validation.smtp.connect-timeout-ms:4000}") int connectTimeoutMs,
      @Value("${app.validation.smtp.read-timeout-ms:6000}") int readTimeoutMs,
      @Value("${app.validation.smtp.ehlo-host:validator.local}") String ehloHost,
      @Value("${app.validation.smtp.mail-from:}") String mailFrom) {
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
    this.ehloHost = ehloHost;
    this.mailFrom = mailFrom;
  }

  @Override
  public SmtpProbeResult probe(List<String> mxHosts, String recipient) {
    SmtpProbeResult last =
        new SmtpProbeResult(SmtpProbeStatus.UNAVAILABLE, null, "No reachable MX host");
    for (String host : mxHosts) {
      try {
        return probeHost(host, recipient);
      } catch (IOException | NumberFormatException exception) {
        last =
            new SmtpProbeResult(
                SmtpProbeStatus.UNAVAILABLE, null, truncate(exception.getMessage()));
      }
    }
    return last;
  }

  private SmtpProbeResult probeHost(String host, String recipient) throws IOException {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, 25), connectTimeoutMs);
      socket.setSoTimeout(readTimeoutMs);

      BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
      BufferedWriter writer =
          new BufferedWriter(
              new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));

      SmtpReply greeting = readReply(reader);
      if (greeting.code() >= 400) {
        return map(greeting);
      }

      write(writer, "EHLO " + ehloHost);
      SmtpReply ehlo = readReply(reader);
      if (ehlo.code() >= 400) {
        write(writer, "HELO " + ehloHost);
        SmtpReply helo = readReply(reader);
        if (helo.code() >= 400) {
          return map(helo);
        }
      }

      write(writer, "MAIL FROM:<" + mailFrom + ">");
      SmtpReply mail = readReply(reader);
      if (mail.code() >= 400) {
        return map(mail);
      }

      write(writer, "RCPT TO:<" + recipient + ">");
      SmtpReply rcpt = readReply(reader);
      try {
        write(writer, "QUIT");
      } catch (IOException ignored) {
        // RCPT result is sufficient; QUIT is best effort.
      }
      return map(rcpt);
    }
  }

  private SmtpReply readReply(BufferedReader reader) throws IOException {
    String first = reader.readLine();
    if (first == null || first.length() < 3) {
      throw new IOException("SMTP server closed connection");
    }
    int code = Integer.parseInt(first.substring(0, 3));
    StringBuilder message = new StringBuilder(first);
    while (first.length() > 3 && first.charAt(3) == '-') {
      String line = reader.readLine();
      if (line == null) {
        break;
      }
      message.append(' ').append(line);
      if (line.startsWith(code + " ")) {
        break;
      }
    }
    return new SmtpReply(code, truncate(message.toString()));
  }

  private void write(BufferedWriter writer, String command) throws IOException {
    writer.write(command);
    writer.write("\r\n");
    writer.flush();
  }

  private SmtpProbeResult map(SmtpReply reply) {
    SmtpProbeStatus status;
    if (reply.code() >= 200 && reply.code() < 300) {
      status = SmtpProbeStatus.ACCEPTED;
    } else if (reply.code() >= 500) {
      status = SmtpProbeStatus.REJECTED;
    } else if (reply.code() >= 400) {
      status = SmtpProbeStatus.TEMPORARY;
    } else {
      status = SmtpProbeStatus.UNAVAILABLE;
    }
    return new SmtpProbeResult(status, reply.code(), reply.message());
  }

  private String truncate(String value) {
    if (value == null) {
      return null;
    }
    return value.length() <= 1000 ? value : value.substring(0, 1000);
  }

  private record SmtpReply(int code, String message) {}
}
