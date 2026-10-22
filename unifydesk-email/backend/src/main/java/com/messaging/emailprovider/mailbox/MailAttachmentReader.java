package com.messaging.emailprovider.mailbox;

import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.mime4j.stream.RawFieldParser;
import org.apache.james.mime4j.stream.RecursionMode;
import org.springframework.stereotype.Component;

/** Bounded leaf-attachment decoding. MIME HTML and filenames are never interpreted as UI markup. */
@Component
public class MailAttachmentReader {
  private static final long MAX_BYTES = 10485760;
  private static final MimeConfig CONFIG =
      MimeConfig.custom()
          .setMaxLineLen(16384)
          .setMaxHeaderLen(65536)
          .setMaxHeaderCount(128)
          .setMaxTotalHeaderCount(512)
          .setMaxTotalHeaderLen(262144)
          .setMaxPartCount(128)
          .setMaxNestingDepth(12)
          .setMaxContentLen(MAX_BYTES)
          .build();

  public record Attachment(int part, String filename, String contentType, long sizeBytes) {}

  public List<Attachment> list(InputStream mime) {
    return visit(mime, null, null);
  }

  public void download(InputStream mime, int part, OutputStream output) {
    if (part < 1 || part > 128) throw new NotFoundException("Attachment not found");
    visit(mime, part, output);
  }

  private List<Attachment> visit(InputStream mime, Integer selected, OutputStream output) {
    List<Attachment> attachments = new ArrayList<>();
    MimeTokenStream parser = new MimeTokenStream(CONFIG);
    parser.setRecursionMode(RecursionMode.M_NO_RECURSE);
    String filename = null;
    boolean attached = false;
    int part = 0;
    long decoded = 0;
    try {
      parser.parse(mime);
      for (EntityState state = parser.getState();
          state != EntityState.T_END_OF_STREAM;
          state = parser.next()) {
        if (state == EntityState.T_START_HEADER) {
          filename = null;
          attached = false;
        } else if (state == EntityState.T_FIELD) {
          var field = parser.getField();
          if (field == null) continue;
          if ("Content-Disposition".equalsIgnoreCase(field.getName())) {
            var disposition =
                RawFieldParser.DEFAULT.parseRawBody(new RawField(field.getName(), field.getBody()));
            attached |= "attachment".equalsIgnoreCase(disposition.getValue());
            String named = parameter(disposition, "filename");
            if (named != null) filename = named;
          } else if ("Content-Type".equalsIgnoreCase(field.getName()) && filename == null) {
            filename =
                parameter(
                    RawFieldParser.DEFAULT.parseRawBody(
                        new RawField(field.getName(), field.getBody())),
                    "name");
          }
        } else if (state == EntityState.T_BODY) {
          part++;
          if (part > 128) throw new ServiceUnavailableException("Too many MIME parts");
          if (!attached && filename == null) continue;
          long bytes = 0;
          byte[] buffer = new byte[8192];
          InputStream body = parser.getDecodedInputStream();
          int count;
          while ((count = body.read(buffer)) != -1) {
            bytes += count;
            decoded += count;
            if (decoded > MAX_BYTES)
              throw new ServiceUnavailableException("Attachment size limit exceeded");
            if (selected != null && selected == part) output.write(buffer, 0, count);
          }
          attachments.add(
              new Attachment(
                  part,
                  safeFilename(filename, part),
                  parser.getBodyDescriptor().getMimeType(),
                  bytes));
          if (selected != null && selected == part) return attachments;
        }
      }
      if (selected != null) throw new NotFoundException("Attachment not found");
      return List.copyOf(attachments);
    } catch (NotFoundException | ServiceUnavailableException rejected) {
      throw rejected;
    } catch (Exception failure) {
      throw new ServiceUnavailableException("Cannot safely decode attachments", failure);
    }
  }

  static String safeFilename(String name, int part) {
    if (name == null || name.isBlank()) return "attachment-" + part + ".bin";
    try {
      name = DecoderUtil.decodeEncodedWords(name, DecodeMonitor.SILENT);
    } catch (Exception ignored) {
      /* use literal name */
    }
    name = name.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\\p{Cf}]", "_");
    name = name.replaceAll("[^\\p{L}\\p{N}._ ()-]", "_").trim();
    if (name.isEmpty() || name.equals(".") || name.equals(".."))
      return "attachment-" + part + ".bin";
    return name.substring(0, Math.min(160, name.length()));
  }

  private static String parameter(org.apache.james.mime4j.stream.RawBody body, String name) {
    return body.getParams().stream()
        .filter(param -> name.equalsIgnoreCase(param.getName()))
        .map(org.apache.james.mime4j.stream.NameValuePair::getValue)
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(null);
  }
}
