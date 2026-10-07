package com.messaging.channel.zoho;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.email.EmailSyncMode;
import com.messaging.channel.email.EmailSyncRequest;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoFolder;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoMessage;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ZohoMailMailboxService {

  private final ZohoMailApiClient apiClient;
  private final ZohoMailTokenService tokenService;
  private final ZohoMailMessageMapper mapper;
  private final ZohoMailOAuthProperties properties;

  public List<ZohoMailMessageSnapshot> history(
      ChannelConnection connection, EmailSyncRequest request) {
    ZohoMailStoredToken token = tokenService.tokenSet(connection);
    String folderId = folderId(token, request.folder());

    int requested =
        request.mode() == EmailSyncMode.LATEST_MESSAGES
            ? Math.min(properties.getSyncMaxMessages(), Math.max(1, request.messageCount()))
            : properties.getSyncMaxMessages();

    List<ZohoMailMessageSnapshot> result = new ArrayList<>();
    int pageSize = Math.min(200, Math.max(1, properties.getPollPageSize()));
    int start = 1;

    while (result.size() < requested) {
      List<ZohoMessage> page =
          apiClient.messages(
              token.mailApiBaseUrl(),
              token.accessToken(),
              token.accountId(),
              folderId,
              start,
              Math.min(pageSize, requested - result.size()));
      if (page.isEmpty()) break;

      for (ZohoMessage message : page) {
        String content =
            apiClient.content(
                token.mailApiBaseUrl(),
                token.accessToken(),
                token.accountId(),
                folderId,
                message.messageId());
        ZohoMailMessageSnapshot snapshot =
            mapper.map(
                message,
                content,
                request.folder(),
                attachments(token, folderId, message));

        if (request.mode() == EmailSyncMode.DATE_RANGE) {
          if (!within(snapshot.sentAt(), request.fromDate(), request.toDate())) continue;
        }
        result.add(snapshot);
        if (result.size() >= requested) break;
      }

      if (page.size() < pageSize) break;
      if (request.mode() == EmailSyncMode.DATE_RANGE
          && page.stream().map(this::providerInstant).filter(java.util.Objects::nonNull).min(Comparator.naturalOrder())
              .map(oldest -> oldest.isBefore(request.fromDate().atStartOfDay(ZoneOffset.UTC).toInstant()))
              .orElse(false)) {
        break;
      }
      start += page.size();
    }

    return List.copyOf(result);
  }

  public List<ZohoMailMessageSnapshot> incremental(
      ChannelConnection connection, EmailFolder folder, Instant since) {
    ZohoMailStoredToken token = tokenService.tokenSet(connection);
    String folderId = folderId(token, folder);
    List<ZohoMailMessageSnapshot> result = new ArrayList<>();
    int pageSize = Math.min(200, Math.max(1, properties.getPollPageSize()));
    int maxPages = Math.max(1, properties.getPollMaxPagesPerFolder());

    for (int page = 0; page < maxPages; page++) {
      int start = 1 + page * pageSize;
      List<ZohoMessage> messages =
          apiClient.messages(
              token.mailApiBaseUrl(),
              token.accessToken(),
              token.accountId(),
              folderId,
              start,
              pageSize);
      if (messages.isEmpty()) break;

      boolean reachedOld = false;
      for (ZohoMessage message : messages) {
        Instant at = providerInstant(message);
        if (at != null && at.isBefore(since)) {
          reachedOld = true;
          continue;
        }
        String content =
            apiClient.content(
                token.mailApiBaseUrl(),
                token.accessToken(),
                token.accountId(),
                folderId,
                message.messageId());
        ZohoMailMessageSnapshot snapshot =
            mapper.map(message, content, folder, attachments(token, folderId, message));
        if (!snapshot.sentAt().isBefore(since)) result.add(snapshot);
      }
      if (reachedOld || messages.size() < pageSize) break;
    }
    return List.copyOf(result);
  }

  private List<ZohoMailApiClient.ZohoAttachment> attachments(
      ZohoMailStoredToken token, String folderId, ZohoMessage message) {
    if (!hasAttachment(message.hasAttachment())) return List.of();
    return apiClient.attachmentInfo(
        token.mailApiBaseUrl(),
        token.accessToken(),
        token.accountId(),
        folderId,
        message.messageId());
  }

  private boolean hasAttachment(Object value) {
    if (value instanceof Boolean flag) return flag;
    if (value instanceof Number number) return number.intValue() != 0;
    if (value == null) return false;
    String normalized = value.toString().trim();
    return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
  }

  public String folderId(ZohoMailStoredToken token, EmailFolder folder) {
    List<ZohoFolder> folders =
        apiClient.folders(token.mailApiBaseUrl(), token.accessToken(), token.accountId());
    return folders.stream()
        .filter(item -> matches(item, folder))
        .map(ZohoFolder::folderId)
        .filter(value -> value != null && !value.isBlank())
        .findFirst()
        .orElseThrow(() -> new BadRequestException("Zoho Mail folder not found: " + folder.name()));
  }

  private boolean matches(ZohoFolder folder, EmailFolder expected) {
    String type = lower(folder.folderType());
    String name = lower(folder.folderName());
    String path = lower(folder.path());
    return expected == EmailFolder.INBOX
        ? "inbox".equals(type) || "inbox".equals(name) || "inbox".equals(path)
        : "sent".equals(type)
            || "sent".equals(name)
            || "sent".equals(path)
            || "sent items".equals(name);
  }

  private boolean within(Instant instant, LocalDate from, LocalDate to) {
    Instant lower = from.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant upper = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return !instant.isBefore(lower) && instant.isBefore(upper);
  }

  private Instant providerInstant(ZohoMessage message) {
    for (String value : List.of(message.receivedTime(), message.sentDateInGMT())) {
      if (value == null || value.isBlank()) continue;
      try {
        return Instant.ofEpochMilli(Long.parseLong(value.trim()));
      } catch (NumberFormatException ignored) {
        try {
          return Instant.parse(value.trim());
        } catch (RuntimeException ignoredAgain) {
          // Ignore malformed provider timestamp.
        }
      }
    }
    return null;
  }

  private String lower(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
