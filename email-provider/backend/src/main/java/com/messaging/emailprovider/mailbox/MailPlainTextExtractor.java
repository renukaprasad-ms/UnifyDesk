package com.messaging.emailprovider.mailbox;

import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Locale;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.apache.james.mime4j.stream.RecursionMode;
import org.springframework.stereotype.Component;

/**
 * Conservative RFC 5322 reader. Only text/plain body parts can enter the UI.
 * Scriptable HTML, remote images and attached text files are NEVER rendered.
 * The parser does not materialize the message or attachments in memory.
 */
@Component
public class MailPlainTextExtractor {
  static final int MAX_CHARS = 65536;
  static final int MAX_PARTS = 128;
  static final int MAX_FIELDS = 512;

  public record PlainText(String text, boolean truncated, boolean available) {}

  public PlainText extract(InputStream raw) {
    if (raw == null) throw new ServiceUnavailableException("Mail object unavailable");
    MimeTokenStream parser = new MimeTokenStream();
    parser.setRecursionMode(RecursionMode.M_NO_RECURSE);
    int parts = 0;
    int fields = 0;
    boolean attachment = false;
    try {
      parser.parse(raw);
      for (EntityState state = parser.getState();
           state != EntityState.T_END_OF_STREAM;
           state = parser.next()) {
        switch (state) {
          case T_START_HEADER -> attachment = false;
          case T_FIELD -> {
            if (++fields > MAX_FIELDS) {
              throw new ServiceUnavailableException("Mail exceeds safe header count");
            }
            Field field = parser.getField();
            if (field == null) break;
            String name = field.getName().toLowerCase(Locale.ROOT);
            String value = field.getBody() == null ? ""
                : field.getBody().toLowerCase(Locale.ROOT);
            // Treat any named/dispositioned file as an attachment, not a body.
            if (("content-disposition".equals(name)
                && (value.contains("attachment") || value.contains("filename")))
                || ("content-type".equals(name) && value.contains("name="))) {
              attachment = true;
            }
          }
          case T_START_BODYPART -> {
            if (++parts > MAX_PARTS) {
              throw new ServiceUnavailableException("Mail contains too many parts");
            }
          }
          case T_BODY -> {
            if (++parts > MAX_PARTS) {
              throw new ServiceUnavailableException("Mail contains too many parts");
            }
            if (!attachment
                && "text/plain".equalsIgnoreCase(parser.getBodyDescriptor().getMimeType())) {
              return readBody(parser.getReader());
            }
          }
          default -> {
            // No parsing or interpretation of executable/HTML content.
          }
        }
      }
      return new PlainText("", false, false);
    } catch (ServiceUnavailableException rejected) {
      throw rejected;
    } catch (Exception failure) {
      throw new ServiceUnavailableException("Cannot safely decode this message", failure);
    }
  }

  private PlainText readBody(Reader reader) throws IOException {
    StringBuilder plain = new StringBuilder(2048);
    char[] chunk = new char[4096];
    int n;
    while ((n = reader.read(chunk)) != -1) {
      int remaining = MAX_CHARS - plain.length();
      if (remaining <= 0) return new PlainText(plain.toString(), true, true);
      plain.append(chunk, 0, Math.min(n, remaining));
      if (n > remaining) return new PlainText(plain.toString(), true, true);
    }
    return new PlainText(plain.toString(), false, true);
  }
}
