package com.messaging.channel.gmail;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.gmail.GmailApiClient.GmailBody;
import com.messaging.channel.gmail.GmailApiClient.GmailHeader;
import com.messaging.channel.gmail.GmailApiClient.GmailMessage;
import com.messaging.channel.gmail.GmailApiClient.GmailPayload;
import com.messaging.channel.gmail.dto.GmailSyncResult;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.inbox.entity.ChannelSyncState;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.entity.InboxMessageAttachment;
import com.messaging.inbox.entity.InboxParticipant;
import com.messaging.inbox.enums.ConversationStatus;
import com.messaging.inbox.enums.InboxMessageDirection;
import com.messaging.inbox.enums.InboxMessageStatus;
import com.messaging.inbox.enums.InboxParticipantType;
import com.messaging.inbox.repository.ChannelSyncStateRepository;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageAttachmentRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.inbox.repository.InboxParticipantRepository;
import com.messaging.storage.enums.StorageUsageType;
import com.messaging.storage.service.MessageStorageMeter;
import com.messaging.storage.service.StorageQuotaService;
import jakarta.mail.Address;
import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class GmailInboxPersistenceService {

  private final InboxConversationRepository conversationRepository;
  private final InboxMessageRepository messageRepository;
  private final InboxParticipantRepository participantRepository;
  private final InboxMessageAttachmentRepository attachmentRepository;
  private final ChannelSyncStateRepository syncStateRepository;
  private final ChannelConnectionRepository connectionRepository;
  private final MessageStorageMeter messageStorageMeter;
  private final StorageQuotaService storageQuotaService;

  @Transactional
  public GmailSyncResult persistFullSync(
      ChannelConnection connection, List<GmailMessage> messages, String historyId) {
    Instant syncedAt = Instant.now();
    int createdMessages = 0;
    Set<Long> touchedConversationIds = new HashSet<>();

    for (GmailMessage gmailMessage : messages) {
      if (gmailMessage == null
          || !hasText(gmailMessage.id())
          || !hasText(gmailMessage.threadId())) {
        continue;
      }

      InboxConversation conversation =
          conversationRepository
              .findByConnectionIdAndExternalConversationId(
                  connection.getId(), gmailMessage.threadId())
              .orElseGet(InboxConversation::new);

      if (conversation.getId() == null) {
        conversation.setCompanyId(connection.getCompanyId());
        conversation.setConnection(connection);
        conversation.setProviderType(ProviderType.GMAIL);
        conversation.setExternalConversationId(gmailMessage.threadId());
        conversation.setStatus(ConversationStatus.OPEN);
      }

      String subject = header(gmailMessage.payload(), "Subject");
      Instant sentAt = sentAt(gmailMessage);
      String preview = trimToNull(gmailMessage.snippet());

      if (hasText(subject)) {
        conversation.setSubject(subject);
      }
      if (conversation.getLastMessageAt() == null
          || sentAt.isAfter(conversation.getLastMessageAt())) {
        conversation.setLastMessageAt(sentAt);
        conversation.setLastMessagePreview(preview);
      }

      conversation = conversationRepository.saveAndFlush(conversation);

      InboxMessage message =
          messageRepository
              .findByConnectionIdAndExternalMessageId(connection.getId(), gmailMessage.id())
              .orElseGet(InboxMessage::new);
      boolean created = message.getId() == null;

      if (created) {
        message.setConversation(conversation);
        message.setConnection(connection);
        message.setExternalMessageId(gmailMessage.id());
      }

      InboxMessageDirection direction = direction(gmailMessage.labelIds());
      message.setInternetMessageId(header(gmailMessage.payload(), "Message-ID"));
      message.setProviderHistoryId(gmailMessage.historyId());
      message.setDirection(direction);
      message.setStatus(
          direction == InboxMessageDirection.OUTBOUND
              ? InboxMessageStatus.SENT
              : InboxMessageStatus.RECEIVED);
      message.setSubject(subject);
      message.setSnippet(preview);
      message.setBodyText(bodyText(gmailMessage.payload(), preview));
      message.setBodyHtml(bodyHtml(gmailMessage.payload()));
      message.setSentAt(sentAt);
      message.setRead(!containsLabel(gmailMessage.labelIds(), "UNREAD"));

      boolean needsStorageAccounting = created || message.getStorageBytes() == 0;
      if (needsStorageAccounting) {
        message.setStorageBytes(
            messageStorageMeter.measure(
                message.getExternalMessageId(),
                message.getInternetMessageId(),
                message.getProviderHistoryId(),
                message.getSubject(),
                message.getSnippet(),
                message.getBodyText(),
                message.getBodyHtml()));
      }

      message = messageRepository.saveAndFlush(message);

      if (needsStorageAccounting) {
        storageQuotaService.consume(
            connection.getCompanyId(),
            StorageUsageType.MESSAGE,
            "INBOX_MESSAGE",
            message.getId().toString(),
            message.getStorageBytes());
      }

      if (created) {
        createdMessages++;
      }

      upsertParticipants(conversation, gmailMessage.payload());
      upsertAttachments(message, gmailMessage.payload());
      touchedConversationIds.add(conversation.getId());
    }

    for (Long conversationId : touchedConversationIds) {
      conversationRepository
          .findById(conversationId)
          .ifPresent(
              conversation -> {
                long unread =
                    messageRepository.countByConversationIdAndDirectionAndReadFalse(
                        conversationId, InboxMessageDirection.INBOUND);
                conversation.setUnreadCount((int) Math.min(unread, Integer.MAX_VALUE));
                conversationRepository.save(conversation);
              });
    }

    ChannelSyncState syncState =
        syncStateRepository
            .findByConnectionIdAndProviderType(connection.getId(), ProviderType.GMAIL)
            .orElseGet(ChannelSyncState::new);
    if (syncState.getId() == null) {
      syncState.setConnection(connection);
      syncState.setProviderType(ProviderType.GMAIL);
    }
    syncState.setCursorValue(historyId);
    syncState.setLastFullSyncAt(syncedAt);
    syncStateRepository.save(syncState);

    connection.setLastSyncAt(syncedAt);
    connectionRepository.save(connection);

    return new GmailSyncResult(
        connection.getId().toString(),
        messages.size(),
        createdMessages,
        touchedConversationIds.size(),
        historyId,
        syncedAt);
  }

  private void upsertParticipants(InboxConversation conversation, GmailPayload payload) {
    for (String headerName : List.of("From", "To", "Cc", "Bcc")) {
      String value = header(payload, headerName);
      if (!hasText(value)) {
        continue;
      }

      try {
        Address[] addresses = InternetAddress.parse(value, false);
        for (Address address : addresses) {
          if (!(address instanceof InternetAddress internetAddress)
              || !hasText(internetAddress.getAddress())) {
            continue;
          }

          String normalizedAddress = internetAddress.getAddress().trim().toLowerCase(Locale.ROOT);
          InboxParticipant participant =
              participantRepository
                  .findByConversationIdAndParticipantTypeAndAddressIgnoreCase(
                      conversation.getId(), InboxParticipantType.EMAIL_ADDRESS, normalizedAddress)
                  .orElseGet(InboxParticipant::new);

          if (participant.getId() == null) {
            participant.setConversation(conversation);
            participant.setParticipantType(InboxParticipantType.EMAIL_ADDRESS);
            participant.setAddress(normalizedAddress);
          }
          if (hasText(internetAddress.getPersonal())) {
            participant.setDisplayName(internetAddress.getPersonal());
          }

          participantRepository.save(participant);
        }
      } catch (Exception ignored) {
        // A malformed participant header should not fail the entire mailbox sync.
      }
    }
  }

  private void upsertAttachments(InboxMessage message, GmailPayload payload) {
    List<AttachmentMetadata> attachments = new ArrayList<>();
    collectAttachments(payload, attachments);

    for (AttachmentMetadata metadata : attachments) {
      InboxMessageAttachment attachment =
          attachmentRepository
              .findByMessageIdAndExternalAttachmentId(
                  message.getId(), metadata.externalAttachmentId())
              .orElseGet(InboxMessageAttachment::new);

      if (attachment.getId() == null) {
        attachment.setMessage(message);
        attachment.setExternalAttachmentId(metadata.externalAttachmentId());
      }
      attachment.setFilename(metadata.filename());
      attachment.setMimeType(metadata.mimeType());
      attachment.setSizeBytes(metadata.sizeBytes());
      attachmentRepository.save(attachment);
    }
  }

  private void collectAttachments(GmailPayload payload, List<AttachmentMetadata> output) {
    if (payload == null) {
      return;
    }

    GmailBody body = payload.body();
    if (body != null && hasText(payload.filename()) && hasText(body.attachmentId())) {
      output.add(
          new AttachmentMetadata(
              body.attachmentId(),
              payload.filename(),
              payload.mimeType(),
              body.size() == null ? null : body.size().longValue()));
    }

    if (payload.parts() != null) {
      payload.parts().forEach(part -> collectAttachments(part, output));
    }
  }

  private String bodyText(GmailPayload payload, String fallback) {
    String plain = findBody(payload, "text/plain");
    if (hasText(plain) && !looksLikeHtml(plain)) {
      return plain.trim();
    }

    String html = bodyHtml(payload);
    return hasText(html) ? htmlToText(html) : fallback;
  }

  private String bodyHtml(GmailPayload payload) {
    String html = findBody(payload, "text/html");
    if (hasText(html)) {
      return html.trim();
    }

    String plain = findBody(payload, "text/plain");
    return hasText(plain) && looksLikeHtml(plain) ? plain.trim() : null;
  }

  private boolean looksLikeHtml(String value) {
    String lower = value.toLowerCase(Locale.ROOT).trim();
    return lower.startsWith("<!doctype html")
        || lower.startsWith("<html")
        || lower.contains("<body")
        || lower.contains("<table")
        || lower.contains("<div")
        || lower.contains("<p>")
        || lower.contains("<br");
  }

  private String htmlToText(String html) {
    String withoutNonContent = html.replaceAll("(?is)<(script|style|head)[^>]*>.*?</\\1>", " ");
    String withLineBreaks =
        withoutNonContent
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</(p|div|tr|li|h[1-6])\\s*>", "\n");
    String withoutTags = withLineBreaks.replaceAll("(?s)<[^>]+>", " ");
    String decoded = HtmlUtils.htmlUnescape(withoutTags);

    return decoded
        .replace('\u00A0', ' ')
        .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
        .replaceAll(" *\\n *", "\n")
        .replaceAll("\\n{3,}", "\n\n")
        .trim();
  }

  private String findBody(GmailPayload payload, String mimeType) {
    if (payload == null) {
      return null;
    }

    if (mimeType.equalsIgnoreCase(payload.mimeType())
        && payload.body() != null
        && hasText(payload.body().data())) {
      try {
        byte[] bytes = Base64.getUrlDecoder().decode(payload.body().data());
        return new String(bytes, StandardCharsets.UTF_8);
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }

    if (payload.parts() != null) {
      for (GmailPayload part : payload.parts()) {
        String result = findBody(part, mimeType);
        if (hasText(result)) {
          return result;
        }
      }
    }

    return null;
  }

  private String header(GmailPayload payload, String name) {
    if (payload == null || payload.headers() == null) {
      return null;
    }

    return payload.headers().stream()
        .filter(header -> header != null && name.equalsIgnoreCase(header.name()))
        .map(GmailHeader::value)
        .filter(this::hasText)
        .findFirst()
        .map(String::trim)
        .orElse(null);
  }

  private Instant sentAt(GmailMessage message) {
    if (hasText(message.internalDate())) {
      try {
        return Instant.ofEpochMilli(Long.parseLong(message.internalDate()));
      } catch (NumberFormatException ignored) {
        // Fall through to current time for malformed provider data.
      }
    }
    return Instant.now();
  }

  private InboxMessageDirection direction(List<String> labels) {
    return containsLabel(labels, "SENT")
        ? InboxMessageDirection.OUTBOUND
        : InboxMessageDirection.INBOUND;
  }

  private boolean containsLabel(List<String> labels, String label) {
    return labels != null && labels.stream().anyMatch(label::equalsIgnoreCase);
  }

  private String trimToNull(String value) {
    return hasText(value) ? value.trim() : null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private record AttachmentMetadata(
      String externalAttachmentId, String filename, String mimeType, Long sizeBytes) {}
}
