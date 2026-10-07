package com.messaging.channel.zoho;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.email.EmailSyncMode;
import com.messaging.channel.email.EmailSyncRequest;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoMessageSummary;
import com.messaging.inbox.dto.InboxEmailChangedEvent;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.realtime.service.RealtimeService;
import jakarta.mail.Address;
import jakarta.mail.internet.InternetAddress;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class ZohoMailMailboxSyncService {

  private final ZohoMailTokenService tokenService;
  private final ZohoMailApiClient apiClient;
  private final ZohoMailInboxPersistenceService persistenceService;
  private final InboxMessageRepository messageRepository;
  private final ZohoMailOAuthProperties properties;
  private final RealtimeService realtimeService;

  public SyncResult sync(
      ChannelConnection connection,
      EmailSyncRequest request,
      ProgressListener progressListener) {
    ZohoMailStoredToken session = tokenService.session(connection);
    EmailFolder folder = request.folder();
    String folderId = folderId(session, folder);

    int maxMessages =
        request.mode() == EmailSyncMode.LATEST_MESSAGES
            ? Math.min(request.messageCount(), properties.getSyncMaxMessages())
            : properties.getSyncMaxMessages();

    Instant from = null;
    Instant to = null;
    if (request.mode() == EmailSyncMode.DATE_RANGE) {
      from = request.fromDate().atStartOfDay(ZoneOffset.UTC).toInstant();
      to = request.toDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    return syncFolder(
        connection,
        session,
        folder,
        folderId,
        from,
        to,
        maxMessages,
        progressListener);
  }

  public SyncResult incremental(
      ChannelConnection connection,
      EmailFolder folder,
      Instant since,
      int maxMessages) {
    ZohoMailStoredToken session = tokenService.session(connection);
    return syncFolder(
        connection,
        session,
        folder,
        folderId(session, folder),
        since,
        null,
        Math.max(1, maxMessages),
        null);
  }

  private SyncResult syncFolder(
      ChannelConnection connection,
      ZohoMailStoredToken session,
      EmailFolder folder,
      String folderId,
      Instant from,
      Instant to,
      int maxMessages,
      ProgressListener progressListener) {
    int start = 1;
    int scanned = 0;
    int created = 0;
    int updated = 0;
    boolean stop = false;
    Set<Long> conversationIds = new LinkedHashSet<>();
    Set<Long> messageIds = new LinkedHashSet<>();

    int pageSize = Math.max(1, Math.min(properties.getSyncPageSize(), 200));
    while (!stop && scanned < maxMessages) {
      int requested = Math.min(pageSize, maxMessages - scanned);
      List<ZohoMessageSummary> page =
          apiClient.listMessages(
              session.accessToken(),
              session.mailBaseUrl(),
              session.accountId(),
              folderId,
              start,
              requested);
      if (page.isEmpty()) break;

      List<ZohoMessageSummary> selected = new ArrayList<>();
      for (ZohoMessageSummary summary : page) {
        Instant sentAt = messageInstant(summary, folder);
        if (to != null && !sentAt.isBefore(to)) continue;
        if (from != null && sentAt.isBefore(from)) {
          stop = true;
          continue;
        }
        selected.add(summary);
        scanned++;
        if (scanned >= maxMessages) break;
      }

      if (!selected.isEmpty()) {
        List<ZohoMailMessageSnapshot> snapshots =
            hydrate(connection, session, folder, selected);
        var persisted = persistenceService.persist(connection, snapshots);
        created += persisted.created();
        updated += persisted.updated();
        conversationIds.addAll(persisted.conversationIds());
        messageIds.addAll(persisted.messageIds());
        if (progressListener != null) {
          progressListener.onProgress(scanned, created + updated);
        }
      }

      if (page.size() < requested) break;
      start += page.size();
    }

    if (!conversationIds.isEmpty() || !messageIds.isEmpty()) {
      realtimeService.sendToCompany(
          connection.getCompanyId(),
          "inbox.email.changed",
          InboxEmailChangedEvent.boundedDelta(
              connection.getId(),
              ProviderType.ZOHO_MAIL,
              conversationIds,
              messageIds,
              Set.of()));
    }

    return new SyncResult(scanned, created, updated, conversationIds, messageIds);
  }

  private List<ZohoMailMessageSnapshot> hydrate(
      ChannelConnection connection,
      ZohoMailStoredToken session,
      EmailFolder folder,
      List<ZohoMessageSummary> summaries) {
    Set<String> providerIds =
        summaries.stream()
            .map(ZohoMessageSummary::messageId)
            .filter(this::hasText)
            .collect(java.util.stream.Collectors.toSet());
    Set<String> existing =
        messageRepository
            .findByConnectionIdAndExternalMessageIdIn(connection.getId(), providerIds)
            .stream()
            .map(item -> item.getExternalMessageId())
            .collect(java.util.stream.Collectors.toSet());

    List<ZohoMailMessageSnapshot> snapshots = new ArrayList<>(summaries.size());
    for (ZohoMessageSummary summary : summaries) {
      if (!hasText(summary.messageId())) continue;

      boolean isNew = !existing.contains(summary.messageId());
      Map<String, List<String>> headers =
          isNew
              ? apiClient.headers(
                  session.accessToken(),
                  session.mailBaseUrl(),
                  session.accountId(),
                  summary.folderId(),
                  summary.messageId())
              : Map.of();
      String body =
          isNew
              ? apiClient.content(
                  session.accessToken(),
                  session.mailBaseUrl(),
                  session.accountId(),
                  summary.folderId(),
                  summary.messageId())
              : null;
      var attachments =
          isNew && hasAttachment(summary.hasAttachment())
              ? apiClient.attachmentInfo(
                  session.accessToken(),
                  session.mailBaseUrl(),
                  session.accountId(),
                  summary.folderId(),
                  summary.messageId())
              : List.<ZohoMailApiClient.ZohoAttachmentInfo>of();

      String fromHeader = firstHeader(headers, "From");
      AddressValue from =
          hasText(fromHeader)
              ? firstAddress(fromHeader)
              : new AddressValue(
                  normalizeAddress(summary.fromAddress()),
                  trimToNull(summary.sender()));
      String replyTo = firstAddressValue(firstHeader(headers, "Reply-To"));
      List<String> to =
          hasText(firstHeader(headers, "To"))
              ? addresses(firstHeader(headers, "To"))
              : addresses(summary.toAddress());
      List<String> cc =
          hasText(firstHeader(headers, "Cc"))
              ? addresses(firstHeader(headers, "Cc"))
              : addresses(summary.ccAddress());
      List<String> bcc =
          hasText(firstHeader(headers, "Bcc"))
              ? addresses(firstHeader(headers, "Bcc"))
              : addresses(summary.bccAddress());

      snapshots.add(
          new ZohoMailMessageSnapshot(
              summary.messageId(),
              hasText(summary.threadId()) ? summary.threadId() : summary.messageId(),
              summary.folderId(),
              folder,
              trimToNull(firstHeader(headers, "Message-Id")),
              trimToNull(firstHeader(headers, "In-Reply-To")),
              trimToNull(firstHeader(headers, "References")),
              from.address(),
              from.displayName(),
              replyTo,
              to,
              cc,
              bcc,
              trimToNull(summary.subject()),
              trimToNull(HtmlUtils.htmlUnescape(nullToEmpty(summary.summary()))),
              body,
              messageInstant(summary, folder),
              isRead(summary.status()),
              attachments));
    }
    return snapshots;
  }

  private String folderId(ZohoMailStoredToken session, EmailFolder folder) {
    return switch (folder) {
      case INBOX -> session.inboxFolderId();
      case SENT -> session.sentFolderId();
      case ALL -> throw new IllegalArgumentException("Zoho sync requires a concrete folder");
    };
  }

  private Instant messageInstant(ZohoMessageSummary summary, EmailFolder folder) {
    String primary = folder == EmailFolder.SENT ? summary.sentDateInGMT() : summary.receivedTime();
    String secondary = folder == EmailFolder.SENT ? summary.receivedTime() : summary.sentDateInGMT();
    Instant parsed = millis(primary);
    return parsed != null ? parsed : (millis(secondary) != null ? millis(secondary) : Instant.now());
  }

  private Instant millis(String value) {
    if (!hasText(value)) return null;
    try {
      return Instant.ofEpochMilli(Long.parseLong(value.trim()));
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private boolean isRead(String status) {
    if (!hasText(status)) return false;
    String normalized = status.trim().toLowerCase(Locale.ROOT);
    return "1".equals(normalized) || "read".equals(normalized) || "true".equals(normalized);
  }

  private boolean hasAttachment(String value) {
    if (!hasText(value)) return false;
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return !"0".equals(normalized) && !"false".equals(normalized);
  }

  private String firstHeader(Map<String, List<String>> headers, String name) {
    if (headers == null || headers.isEmpty()) return null;
    return headers.entrySet().stream()
        .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
        .flatMap(entry -> entry.getValue() == null ? java.util.stream.Stream.empty() : entry.getValue().stream())
        .filter(this::hasText)
        .findFirst()
        .map(String::trim)
        .orElse(null);
  }

  private String firstAddressValue(String value) {
    AddressValue parsed = firstAddress(value);
    return parsed.address();
  }

  private AddressValue firstAddress(String value) {
    if (!hasText(value)) return new AddressValue(null, null);
    try {
      Address[] parsed = InternetAddress.parse(HtmlUtils.htmlUnescape(value), false);
      for (Address address : parsed) {
        if (address instanceof InternetAddress internetAddress
            && hasText(internetAddress.getAddress())) {
          return new AddressValue(
              normalizeAddress(internetAddress.getAddress()),
              trimToNull(internetAddress.getPersonal()));
        }
      }
    } catch (Exception ignored) {
      String normalized = normalizeAddress(value);
      return new AddressValue(normalized, null);
    }
    return new AddressValue(null, null);
  }

  private List<String> addresses(String value) {
    if (!hasText(value) || "Not Provided".equalsIgnoreCase(value.trim())) return List.of();
    try {
      Address[] parsed = InternetAddress.parse(HtmlUtils.htmlUnescape(value), false);
      List<String> result = new ArrayList<>();
      for (Address address : parsed) {
        if (address instanceof InternetAddress internetAddress
            && hasText(internetAddress.getAddress())) {
          String normalized = normalizeAddress(internetAddress.getAddress());
          if (normalized != null && !result.contains(normalized)) result.add(normalized);
        }
      }
      return List.copyOf(result);
    } catch (Exception ignored) {
      String normalized = normalizeAddress(value);
      return normalized == null ? List.of() : List.of(normalized);
    }
  }

  private String normalizeAddress(String value) {
    return hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
  }

  private String trimToNull(String value) {
    return hasText(value) ? value.trim() : null;
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public interface ProgressListener {
    void onProgress(int scanned, int changed);
  }

  public record SyncResult(
      int scanned,
      int created,
      int updated,
      Set<Long> conversationIds,
      Set<Long> messageIds) {}

  private record AddressValue(String address, String displayName) {}
}
