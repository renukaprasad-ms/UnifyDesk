package com.messaging.emailprovider.mailbox.inbound;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Streams bounded raw MIME to a persistent local volume; never to an emptyDir. */
@Component
@ConditionalOnExpression("${app.mail.inbound.enabled:false} || ${app.mail.outbound.enabled:false}")
public class MailSpoolStore {
  private static final int BUFFER_BYTES = 8192;
  private final Path root;
  private final long maxBytes;

  public record Stored(String key, long bytes, String sha256) {}

  public MailSpoolStore(
      @Value("${app.mail.inbound.spool-path:}") String rootPath,
      @Value("${app.mail.inbound.max-bytes:10485760}") long maxBytes) {
    if (rootPath == null || rootPath.isBlank() || maxBytes <= 0 || maxBytes > 10485760L) {
      throw new IllegalStateException("Persistent mail spool path and safe size limit required");
    }
    this.root = Path.of(rootPath).toAbsolutePath().normalize();
    this.maxBytes = maxBytes;
    if (!Files.isDirectory(root) || Files.isSymbolicLink(root) || !Files.isWritable(root)) {
      throw new IllegalStateException(
          "Mail spool must be an existing writable persistent directory");
    }
  }

  public Stored write(InputStream mime) {
    if (mime == null) throw new BadRequestException("MIME body is required");
    Path temporary = null;
    try {
      // Shared volume headroom: do not fill the mail node with quarantined input.
      if (Files.getFileStore(root).getUsableSpace() < maxBytes * 2 + 64L * 1024 * 1024) {
        throw new ServiceUnavailableException("Insufficient inbound spool free space");
      }
      temporary = Files.createTempFile(root, "receiving-", ".part");
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      long count = 0;
      try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE);
          OutputStream output = Channels.newOutputStream(channel)) {
        byte[] buffer = new byte[BUFFER_BYTES];
        int read;
        while ((read = mime.read(buffer)) != -1) {
          count += read;
          if (count > maxBytes) throw new BadRequestException("MIME size limit exceeded");
          output.write(buffer, 0, read);
          digest.update(buffer, 0, read);
        }
        if (count == 0) throw new BadRequestException("Empty MIME message");
        output.flush();
        channel.force(true);
      }
      String key = UUID.randomUUID() + ".eml";
      Files.move(temporary, root.resolve(key), StandardCopyOption.ATOMIC_MOVE);
      temporary = null;
      try (FileChannel directory = FileChannel.open(root, StandardOpenOption.READ)) {
        directory.force(true);
      }
      return new Stored(key, count, HexFormat.of().formatHex(digest.digest()));
    } catch (BadRequestException invalid) {
      throw invalid;
    } catch (IOException | NoSuchAlgorithmException failure) {
      throw new ServiceUnavailableException("Durable inbound mail storage unavailable", failure);
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // Reconcile orphaned partial files using an operator-only maintenance task.
        }
      }
    }
  }

  /** Only immutable, server-generated object keys may be opened or streamed. */
  public InputStream open(String key) {
    if (key == null || !key.matches("[0-9a-f-]{36}\\.eml")) {
      throw new IllegalArgumentException("Invalid MIME spool key");
    }
    Path file = root.resolve(key);
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
      throw new ServiceUnavailableException("MIME spool object is missing or unsafe");
    }
    try {
      return Channels.newInputStream(
          FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
    } catch (IOException problem) {
      throw new ServiceUnavailableException("MIME spool object unavailable", problem);
    }
  }

  /** Only server-generated immutable object keys can be deleted. */
  public void purge(Stored stored) {
    delete(stored);
    try (FileChannel directory = FileChannel.open(root, StandardOpenOption.READ)) {
      directory.force(true);
    } catch (IOException failure) {
      throw new ServiceUnavailableException(
          "Unable to durably remove retained mail object", failure);
    }
  }

  /** Only server-generated immutable object keys can be deleted. */
  public void delete(Stored stored) {
    if (stored == null || !stored.key().matches("[0-9a-f-]{36}\\.eml")) {
      throw new IllegalArgumentException("Invalid spool key");
    }
    try {
      Files.deleteIfExists(root.resolve(stored.key()));
    } catch (IOException failure) {
      throw new ServiceUnavailableException("Unable to discard duplicate spool object", failure);
    }
  }
}
