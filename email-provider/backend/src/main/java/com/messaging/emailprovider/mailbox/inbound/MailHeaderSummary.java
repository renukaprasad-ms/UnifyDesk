package com.messaging.emailprovider.mailbox.inbound;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Bounded metadata extraction. This is not a full MIME renderer or HTML sanitizer. */
public final class MailHeaderSummary {
  private static final int MAX_HEADER_BYTES = 32768;
  private MailHeaderSummary() {}

  public record Summary(String from, String subject, String messageId) {}

  public static Summary read(InputStream input) throws IOException {
    StringBuilder all = new StringBuilder(4096);
    int byteCount = 0;
    int next;
    while ((next = input.read()) != -1) {
      if (++byteCount > MAX_HEADER_BYTES) throw new IOException("Oversized mail headers");
      all.append((char) (next & 0xff));
      if (all.length() >= 4 && all.substring(all.length() - 4).equals("\r\n\r\n")) break;
    }
    if (!all.toString().endsWith("\r\n\r\n")) throw new IOException("Malformed MIME header block");
    StringBuilder from = new StringBuilder();
    StringBuilder subject = new StringBuilder();
    StringBuilder messageId = new StringBuilder();
    String current = "";
    for (String line : all.toString().split("\r\n")) {
      if (line.isEmpty()) break;
      if (line.length() > 998) throw new IOException("Header line too long");
      if (line.startsWith(" ") || line.startsWith("\t")) {
        append(current, from, subject, messageId, " " + line.trim());
        continue;
      }
      int colon = line.indexOf(':');
      if (colon < 1) throw new IOException("Invalid MIME header");
      current = line.substring(0, colon).toLowerCase(Locale.ROOT);
      append(current, from, subject, messageId, line.substring(colon + 1).trim());
    }
    return new Summary(trim(from, 320), trim(subject, 998), trim(messageId, 998));
  }

  private static void append(String key, StringBuilder from, StringBuilder subject,
      StringBuilder id, String value) throws IOException {
    StringBuilder destination = switch (key) {
      case "from" -> from;
      case "subject" -> subject;
      case "message-id" -> id;
      default -> null;
    };
    if (destination == null) return;
    if (destination.length() + value.length() > 998) {
      throw new IOException("Header metadata too large");
    }
    destination.append(value);
  }

  private static String trim(StringBuilder value, int max) {
    if (value.isEmpty()) return null;
    return value.substring(0, Math.min(max, value.length()));
  }
}
