package com.messaging.emailprovider.mailbox.outbound;

import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only, bounded, advisory orphan inventory for one persistent mail volume.
 *
 * <p>NO deletion or automatic cleanup: ambiguous DB commits or a copy in flight can make a valid
 * object appear orphaned. Human review and encrypted backups are prerequisites to any future
 * explicit retention workflow.
 */
@Service
@ConditionalOnProperty(prefix = "app.mail.ops", name = "enabled", havingValue = "true")
public class MailOrphanInventoryService {
  private static final int MAX_INSPECT = 2000;
  private static final int MAX_CANDIDATES = 100;
  private static final int MIN_AGE_HOURS = 72;

  private final JdbcTemplate jdbc;
  private final Path root;

  public record Candidate(String objectKey, long sizeBytes, String modifiedAt) {}

  public record Inventory(
      long inspected, boolean truncated, long freeDiskBytes, List<Candidate> oldUnreferenced) {}

  public MailOrphanInventoryService(
      JdbcTemplate jdbc, @Value("${app.mail.inbound.spool-path:}") String path) {
    if (path == null || path.isBlank()) {
      throw new IllegalStateException(
          "Persistent mail spool path is required for recovery inventory");
    }
    this.jdbc = jdbc;
    root = Path.of(path).toAbsolutePath().normalize();
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
      throw new IllegalStateException("Unsafe mail spool directory");
    }
  }

  public Inventory inventory() {
    List<Candidate> candidates = new ArrayList<>();
    long inspected = 0;
    boolean truncated = false;
    Instant now = Instant.now();
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
      for (Path file : entries) {
        if (inspected >= MAX_INSPECT || candidates.size() >= MAX_CANDIDATES) {
          truncated = true;
          break;
        }
        inspected++;
        String key = file.getFileName().toString();
        if (!eligibleName(key)) continue;
        BasicFileAttributes attrs =
            Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isRegularFile()) continue;
        Instant modified = attrs.lastModifiedTime().toInstant();
        if (!oldEnough(modified, now)) continue;
        if (isReferenced(key)) continue;
        candidates.add(new Candidate(key, attrs.size(), modified.toString()));
      }
      return new Inventory(
          inspected, truncated, Files.getFileStore(root).getUsableSpace(), List.copyOf(candidates));
    } catch (IOException failure) {
      throw new ServiceUnavailableException("Mail storage inventory unavailable", failure);
    }
  }

  private boolean isReferenced(String key) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
        SELECT
          EXISTS(SELECT 1 FROM email_mail_inbound_jobs WHERE mime_object_key=?)
          OR EXISTS(SELECT 1 FROM email_mail_outbound_archives WHERE mime_object_key=?)
          OR EXISTS(SELECT 1 FROM email_mailbox_messages WHERE mime_object_key=?)
        """,
            Boolean.class,
            key,
            key,
            key));
  }

  static boolean eligibleName(String key) {
    // Filenames are immutable, generated UUIDs; never inspect symlinks or paths.
    return key != null
        && key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.eml");
  }

  static boolean oldEnough(Instant modified, Instant now) {
    return modified != null
        && now != null
        && !modified.isAfter(now.minus(MIN_AGE_HOURS, ChronoUnit.HOURS));
  }
}
