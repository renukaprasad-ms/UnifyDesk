package com.messaging.channel.microsoft;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.microsoft.MicrosoftApiClient.*;
import com.messaging.inbox.entity.*;
import com.messaging.inbox.enums.*;
import com.messaging.inbox.repository.*;
import com.messaging.realtime.service.RealtimeService;
import com.messaging.storage.enums.StorageUsageType;
import com.messaging.storage.service.MessageStorageMeter;
import com.messaging.storage.service.StorageAccountingService;
import com.messaging.storage.service.StorageQuotaService;
import com.messaging.storage.service.StorageQuotaService.StorageConsumption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class MicrosoftInboxPersistenceService {

  private final InboxConversationRepository conversationRepository;
  private final InboxMessageRepository messageRepository;
  private final InboxParticipantRepository participantRepository;
  private final InboxMessageAttachmentRepository attachmentRepository;
  private final MessageStorageMeter messageStorageMeter;
  private final StorageQuotaService storageQuotaService;
  private final StorageAccountingService storageAccountingService;
  private final RealtimeService realtimeService;

  @Transactional
  public SyncResult persistPage(
      ChannelConnection connection, String folderKey, List<GraphMessage> graphMessages) {
    List<GraphMessage> messages =
        graphMessages == null
            ? List.of()
            : graphMessages.stream().filter(Objects::nonNull).toList();

    Set<String> conversationIds = new HashSet<>();
    Set<String> messageIds = new HashSet<>();
    for (GraphMessage message : messages) {
      if (!message.removedMessage() && hasText(message.id())) {
        messageIds.add(message.id());
        conversationIds.add(
            hasText(message.conversationId()) ? message.conversationId() : message.id());
      }
    }

    Map<String, InboxConversation> conversations = new HashMap<>();
    if (!conversationIds.isEmpty()) {
      for (InboxConversation conversation :
          conversationRepository.findByConnectionIdAndExternalConversationIdIn(
              connection.getId(), conversationIds)) {
        conversations.put(conversation.getExternalConversationId(), conversation);
      }
    }

    Map<String, InboxMessage> existingMessages = new HashMap<>();
    if (!messageIds.isEmpty()) {
      for (InboxMessage message :
          messageRepository.findByConnectionIdAndExternalMessageIdIn(
              connection.getId(), messageIds)) {
        existingMessages.put(message.getExternalMessageId(), message);
      }
    }

    int created = 0;
    int updated = 0;
    int deleted = 0;
    Set<Long> touchedConversationIds = new HashSet<>();
    List<StorageConsumption> consumptions = new ArrayList<>();

    for (GraphMessage graphMessage : messages) {
      if (!hasText(graphMessage.id())) continue;

      if (graphMessage.removedMessage()) {
        InboxMessage existing =
            messageRepository
                .findByConnectionIdAndExternalMessageId(connection.getId(), graphMessage.id())
                .orElse(null);
        if (existing != null
            && (existing.getProviderFolderKey() == null
                || normalizedFolderKey.equalsIgnoreCase(existing.getProviderFolderKey()))) {
          Long conversationId = existing.getConversation().getId();
          if (existing.getStorageBytes() > 0) {
            storageAccountingService.releaseUsage(
                connection.getCompanyId(),
                StorageUsageType.MESSAGE,
                "INBOX_MESSAGE",
                existing.getId().toString(),
                existing.getStorageBytes());
          }
          messageRepository.delete(existing);
          touchedConversationIds.add(conversationId);
          deleted++;
        }
        continue;
      }

      String externalConversationId =
          hasText(graphMessage.conversationId()) ? graphMessage.conversationId() : graphMessage.id();
      InboxConversation conversation = conversations.get(externalConversationId);
      boolean conversationCreated = conversation == null;
      if (conversationCreated) {
        conversation = new InboxConversation();
        conversation.setCompanyId(connection.getCompanyId());
        conversation.setConnection(connection);
        conversation.setProviderType(ProviderType.MICROSOFT);
        conversation.setExternalConversationId(externalConversationId);
        conversation.setStatus(ConversationStatus.OPEN);
      }

      Instant sentAt = sentAt(graphMessage);
      String subject = trimToNull(graphMessage.subject());
      String preview = trimToNull(graphMessage.bodyPreview());
      if (hasText(subject)) conversation.setSubject(subject);
      if (conversation.getLastMessageAt() == null
          || sentAt.isAfter(conversation.getLastMessageAt())) {
        conversation.setLastMessageAt(sentAt);
        conversation.setLastMessagePreview(preview);
      }
      conversation = conversationRepository.save(conversation);
      conversations.put(externalConversationId, conversation);

      InboxMessage message = existingMessages.get(graphMessage.id());
      boolean messageCreated = message == null;
      if (messageCreated) {
        message = new InboxMessage();
        message.setConversation(conversation);
        message.setConnection(connection);
        message.setExternalMessageId(graphMessage.id());
      }

      InboxMessageDirection direction =
          normalizedFolder == EmailFolder.SENT
              ? InboxMessageDirection.OUTBOUND
              : InboxMessageDirection.INBOUND;
      String html = bodyHtml(graphMessage.body());
      String text = bodyText(graphMessage.body(), preview);

      message.setConversation(conversation);
      message.setInternetMessageId(trimToNull(graphMessage.internetMessageId()));
      message.setProviderHistoryId(null);
      message.setProviderFolderKey(normalizedFolderKey);
      message.setFromAddress(address(graphMessage.from()));
      message.setReplyToAddress(firstAddress(graphMessage.replyTo()));
      message.setToAddresses(addressesCsv(graphMessage.toRecipients()));
      message.setCcAddresses(addressesCsv(graphMessage.ccRecipients()));
      message.setBccAddresses(addressesCsv(graphMessage.bccRecipients()));
      message.setInReplyTo(header(graphMessage.internetMessageHeaders(), "In-Reply-To"));
      message.setReferencesHeader(header(graphMessage.internetMessageHeaders(), "References"));
      message.setDirection(direction);
      message.setStatus(
          direction == InboxMessageDirection.OUTBOUND
              ? InboxMessageStatus.SENT
              : InboxMessageStatus.RECEIVED);
      message.setSubject(subject);
      message.setSnippet(preview);
      message.setBodyText(text);
      message.setBodyHtml(html);
      message.setSentAt(sentAt);
      message.setRead(Boolean.TRUE.equals(graphMessage.isRead()));

      boolean accountStorage = messageCreated || message.getStorageBytes() == 0;
      if (accountStorage) {
        message.setStorageBytes(
            messageStorageMeter.measure(
                message.getExternalMessageId(),
                message.getInternetMessageId(),
                null,
                message.getSubject(),
                message.getSnippet(),
                message.getBodyText(),
                message.getBodyHtml()));
      }

      message = messageRepository.save(message);
      existingMessages.put(graphMessage.id(), message);

      if (accountStorage && message.getStorageBytes() > 0) {
        consumptions.add(
            new StorageConsumption(
                "INBOX_MESSAGE", message.getId().toString(), message.getStorageBytes()));
      }

      upsertParticipants(conversation, graphMessage);
      upsertAttachments(message, graphMessage.attachments());
      touchedConversationIds.add(conversation.getId());

      if (messageCreated) created++;
      else updated++;
    }

    storageQuotaService.consumeBatch(
        connection.getCompanyId(), StorageUsageType.MESSAGE, consumptions);
    messageRepository.flush();

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

    if (created > 0 || updated > 0 || deleted > 0) {
      realtimeService.sendToCompany(
          connection.getCompanyId(),
          "inbox.email.changed",
          Map.of(
              "connectionId", connection.getId().toString(),
              "provider", ProviderType.MICROSOFT.name(),
              "created", created,
              "updated", updated,
              "deleted", deleted));
    }

    return new SyncResult(created, updated, deleted, touchedConversationIds.size());
  }

  private void upsertParticipants(InboxConversation conversation, GraphMessage message) {
    LinkedHashMap<String, GraphEmailAddress> addresses = new LinkedHashMap<>();
    collect(addresses, message.from() == null ? List.of() : List.of(message.from()));
    collect(addresses, message.replyTo());
    collect(addresses, message.toRecipients());
    collect(addresses, message.ccRecipients());
    collect(addresses, message.bccRecipients());

    for (GraphEmailAddress email : addresses.values()) {
      String normalized = email.address().trim().toLowerCase(Locale.ROOT);
      InboxParticipant participant =
          participantRepository
              .findByConversationIdAndParticipantTypeAndAddressIgnoreCase(
                  conversation.getId(), InboxParticipantType.EMAIL_ADDRESS, normalized)
              .orElseGet(InboxParticipant::new);
      if (participant.getId() == null) {
        participant.setConversation(conversation);
        participant.setParticipantType(InboxParticipantType.EMAIL_ADDRESS);
        participant.setAddress(normalized);
      }
      if (hasText(email.name())) participant.setDisplayName(email.name().trim());
      participantRepository.save(participant);
    }
  }

  private void collect(
      Map<String, GraphEmailAddress> target, List<GraphRecipient> recipients) {
    if (recipients == null) return;
    for (GraphRecipient recipient : recipients) {
      if (recipient == null
          || recipient.emailAddress() == null
          || !hasText(recipient.emailAddress().address())) continue;
      String key = recipient.emailAddress().address().trim().toLowerCase(Locale.ROOT);
      target.putIfAbsent(key, recipient.emailAddress());
    }
  }

  private void upsertAttachments(InboxMessage message, List<GraphAttachment> attachments) {
    if (attachments == null) return;
    for (GraphAttachment graphAttachment : attachments) {
      if (graphAttachment == null || !hasText(graphAttachment.id())) continue;
      InboxMessageAttachment attachment =
          attachmentRepository
              .findByMessageIdAndExternalAttachmentId(message.getId(), graphAttachment.id())
              .orElseGet(InboxMessageAttachment::new);
      if (attachment.getId() == null) {
        attachment.setMessage(message);
        attachment.setExternalAttachmentId(graphAttachment.id());
      }
      attachment.setFilename(trimToNull(graphAttachment.name()));
      attachment.setMimeType(trimToNull(graphAttachment.contentType()));
      attachment.setSizeBytes(graphAttachment.size());
      attachmentRepository.save(attachment);
    }
  }

  private Instant sentAt(GraphMessage message) {
    String value = hasText(message.sentDateTime()) ? message.sentDateTime() : message.receivedDateTime();
    if (hasText(value)) {
      try {
        return OffsetDateTime.parse(value).toInstant();
      } catch (RuntimeException ignored) {
        // Provider data is allowed to be malformed without stopping the mailbox.
      }
    }
    return Instant.now();
  }

  private String bodyHtml(GraphBody body) {
    return body != null
            && "html".equalsIgnoreCase(body.contentType())
            && hasText(body.content())
        ? body.content().trim()
        : null;
  }

  private String bodyText(GraphBody body, String fallback) {
    if (body == null || !hasText(body.content())) return fallback;
    if (!"html".equalsIgnoreCase(body.contentType())) return body.content().trim();

    String withoutNonContent =
        body.content().replaceAll("(?is)<(script|style|head)[^>]*>.*?</\\1>", " ");
    String withBreaks =
        withoutNonContent
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</(p|div|tr|li|h[1-6])\\s*>", "\n");
    return HtmlUtils.htmlUnescape(withBreaks.replaceAll("(?s)<[^>]+>", " "))
        .replace('\u00A0', ' ')
        .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
        .replaceAll(" *\\n *", "\n")
        .replaceAll("\\n{3,}", "\n\n")
        .trim();
  }

  private String header(List<GraphHeader> headers, String name) {
    if (headers == null) return null;
    return headers.stream()
        .filter(Objects::nonNull)
        .filter(item -> name.equalsIgnoreCase(item.name()))
        .map(GraphHeader::value)
        .filter(this::hasText)
        .findFirst()
        .map(String::trim)
        .orElse(null);
  }

  private String firstAddress(List<GraphRecipient> recipients) {
    if (recipients == null) return null;
    return recipients.stream().map(this::address).filter(this::hasText).findFirst().orElse(null);
  }

  private String addressesCsv(List<GraphRecipient> recipients) {
    if (recipients == null) return null;
    List<String> values =
        recipients.stream().map(this::address).filter(this::hasText).distinct().toList();
    return values.isEmpty() ? null : String.join(",", values);
  }

  private String address(GraphRecipient recipient) {
    if (recipient == null
        || recipient.emailAddress() == null
        || !hasText(recipient.emailAddress().address())) return null;
    return recipient.emailAddress().address().trim().toLowerCase(Locale.ROOT);
  }

  private String trimToNull(String value) {
    return hasText(value) ? value.trim() : null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public record SyncResult(int created, int updated, int deleted, int conversationsTouched) {}
}
