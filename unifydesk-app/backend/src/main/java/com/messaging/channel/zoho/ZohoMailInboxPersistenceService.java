package com.messaging.channel.zoho;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.entity.InboxMessageAttachment;
import com.messaging.inbox.entity.InboxParticipant;
import com.messaging.inbox.enums.ConversationStatus;
import com.messaging.inbox.enums.InboxMessageDirection;
import com.messaging.inbox.enums.InboxMessageStatus;
import com.messaging.inbox.enums.InboxParticipantType;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageAttachmentRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.inbox.repository.InboxParticipantRepository;
import com.messaging.inbox.service.InboxMessagePostProcessor;
import com.messaging.storage.enums.StorageUsageType;
import com.messaging.storage.service.MessageStorageMeter;
import com.messaging.storage.service.StorageQuotaService;
import com.messaging.storage.service.StorageQuotaService.StorageConsumption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class ZohoMailInboxPersistenceService {

  private final InboxConversationRepository conversationRepository;
  private final InboxMessageRepository messageRepository;
  private final InboxParticipantRepository participantRepository;
  private final InboxMessageAttachmentRepository attachmentRepository;
  private final MessageStorageMeter messageStorageMeter;
  private final StorageQuotaService storageQuotaService;
  private final List<InboxMessagePostProcessor> messagePostProcessors;

  @Transactional
  public PersistResult persist(
      ChannelConnection connection, List<ZohoMailMessageSnapshot> snapshots) {
    List<ZohoMailMessageSnapshot> valid =
        snapshots == null
            ? List.of()
            : snapshots.stream()
                .filter(Objects::nonNull)
                .filter(item -> hasText(item.messageId()))
                .toList();
    if (valid.isEmpty()) {
      return new PersistResult(0, 0, Set.of(), Set.of());
    }

    Set<String> messageIds = new HashSet<>();
    Set<String> threadIds = new HashSet<>();
    for (ZohoMailMessageSnapshot snapshot : valid) {
      messageIds.add(snapshot.messageId());
      threadIds.add(
          hasText(snapshot.threadId()) ? snapshot.threadId() : snapshot.messageId());
    }

    Map<String, InboxMessage> existingMessages = new HashMap<>();
    for (InboxMessage message :
        messageRepository.findByConnectionIdAndExternalMessageIdIn(
            connection.getId(), messageIds)) {
      existingMessages.put(message.getExternalMessageId(), message);
    }

    Map<String, InboxConversation> conversations = new HashMap<>();
    for (InboxConversation conversation :
        conversationRepository.findByConnectionIdAndExternalConversationIdIn(
            connection.getId(), threadIds)) {
      conversations.put(conversation.getExternalConversationId(), conversation);
    }

    int created = 0;
    int updated = 0;
    Set<Long> touchedConversationIds = new LinkedHashSet<>();
    Set<Long> touchedMessageIds = new LinkedHashSet<>();
    List<StorageConsumption> storage = new ArrayList<>();

    for (ZohoMailMessageSnapshot snapshot : valid) {
      String externalConversationId =
          hasText(snapshot.threadId()) ? snapshot.threadId() : snapshot.messageId();

      InboxMessage message = existingMessages.get(snapshot.messageId());
      InboxConversation conversation =
          message != null ? message.getConversation() : conversations.get(externalConversationId);

      if (conversation == null) {
        conversation = new InboxConversation();
        conversation.setCompanyId(connection.getCompanyId());
        conversation.setConnection(connection);
        conversation.setProviderType(ProviderType.ZOHO_MAIL);
        conversation.setExternalConversationId(externalConversationId);
        conversation.setStatus(ConversationStatus.OPEN);
      }

      String subject = trimToNull(snapshot.subject());
      String preview = trimToNull(snapshot.summary());
      Instant sentAt = snapshot.sentAt() == null ? Instant.now() : snapshot.sentAt();
      if (subject != null) conversation.setSubject(subject);
      if (conversation.getLastMessageAt() == null
          || sentAt.isAfter(conversation.getLastMessageAt())) {
        conversation.setLastMessageAt(sentAt);
        conversation.setLastMessagePreview(preview);
      }
      conversation = conversationRepository.save(conversation);
      conversations.put(externalConversationId, conversation);

      boolean messageCreated = message == null;
      if (messageCreated) {
        message = new InboxMessage();
        message.setConversation(conversation);
        message.setConnection(connection);
        message.setExternalMessageId(snapshot.messageId());
      }

      InboxMessageDirection direction =
          snapshot.folder() == EmailFolder.SENT
              ? InboxMessageDirection.OUTBOUND
              : InboxMessageDirection.INBOUND;

      message.setConversation(conversation);
      message.setInternetMessageId(trimToNull(snapshot.internetMessageId()));
      message.setProviderHistoryId(null);
      message.setProviderFolderKey(snapshot.folder().providerKey());
      message.setFromAddress(normalizeAddress(snapshot.fromAddress()));
      message.setReplyToAddress(normalizeAddress(snapshot.replyToAddress()));
      message.setToAddresses(csv(snapshot.to()));
      message.setCcAddresses(csv(snapshot.cc()));
      message.setBccAddresses(csv(snapshot.bcc()));
      message.setInReplyTo(trimToNull(snapshot.inReplyTo()));
      message.setReferencesHeader(trimToNull(snapshot.references()));
      message.setDirection(direction);
      message.setStatus(
          direction == InboxMessageDirection.OUTBOUND
              ? InboxMessageStatus.SENT
              : InboxMessageStatus.RECEIVED);
      message.setSubject(subject);
      message.setSnippet(preview);

      if (snapshot.bodyHtml() != null) {
        String html = trimToNull(snapshot.bodyHtml());
        message.setBodyHtml(html);
        message.setBodyText(html == null ? preview : htmlToText(html));
      } else if (messageCreated) {
        message.setBodyText(preview);
      }

      message.setSentAt(sentAt);
      message.setRead(direction == InboxMessageDirection.OUTBOUND || snapshot.read());

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
      existingMessages.put(snapshot.messageId(), message);
      touchedMessageIds.add(message.getId());

      if (accountStorage && message.getStorageBytes() > 0) {
        storage.add(
            new StorageConsumption(
                "INBOX_MESSAGE", message.getId().toString(), message.getStorageBytes()));
      }

      upsertParticipants(conversation, snapshot);
      upsertAttachments(message, snapshot.attachments());
      touchedConversationIds.add(conversation.getId());

      if (messageCreated) {
        created++;
        if (direction == InboxMessageDirection.INBOUND) {
          for (InboxMessagePostProcessor processor : messagePostProcessors) {
            processor.afterPersist(message);
          }
        }
      } else {
        updated++;
      }
    }

    storageQuotaService.consumeBatch(
        connection.getCompanyId(), StorageUsageType.MESSAGE, storage);
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

    return new PersistResult(
        created,
        updated,
        Set.copyOf(touchedConversationIds),
        Set.copyOf(touchedMessageIds));
  }

  private void upsertParticipants(
      InboxConversation conversation, ZohoMailMessageSnapshot snapshot) {
    LinkedHashMap<String, String> addresses = new LinkedHashMap<>();
    addAddress(addresses, snapshot.fromAddress(), snapshot.fromDisplayName());
    addAddress(addresses, snapshot.replyToAddress(), null);
    addAddresses(addresses, snapshot.to());
    addAddresses(addresses, snapshot.cc());
    addAddresses(addresses, snapshot.bcc());

    for (Map.Entry<String, String> entry : addresses.entrySet()) {
      InboxParticipant participant =
          participantRepository
              .findByConversationIdAndParticipantTypeAndAddressIgnoreCase(
                  conversation.getId(), InboxParticipantType.EMAIL_ADDRESS, entry.getKey())
              .orElseGet(InboxParticipant::new);
      if (participant.getId() == null) {
        participant.setConversation(conversation);
        participant.setParticipantType(InboxParticipantType.EMAIL_ADDRESS);
        participant.setAddress(entry.getKey());
      }
      if (hasText(entry.getValue())) participant.setDisplayName(entry.getValue().trim());
      participantRepository.save(participant);
    }
  }

  private void upsertAttachments(
      InboxMessage message,
      List<ZohoMailApiClient.ZohoAttachmentInfo> attachments) {
    if (attachments == null) return;
    for (var item : attachments) {
      if (item == null || !hasText(item.attachmentId())) continue;
      InboxMessageAttachment attachment =
          attachmentRepository
              .findByMessageIdAndExternalAttachmentId(message.getId(), item.attachmentId())
              .orElseGet(InboxMessageAttachment::new);
      if (attachment.getId() == null) {
        attachment.setMessage(message);
        attachment.setExternalAttachmentId(item.attachmentId());
      }
      attachment.setFilename(trimToNull(item.attachmentName()));
      attachment.setMimeType(null);
      attachment.setSizeBytes(item.attachmentSize());
      attachmentRepository.save(attachment);
    }
  }

  private void addAddresses(Map<String, String> target, List<String> values) {
    if (values == null) return;
    values.forEach(value -> addAddress(target, value, null));
  }

  private void addAddress(Map<String, String> target, String address, String displayName) {
    String normalized = normalizeAddress(address);
    if (normalized != null) {
      target.putIfAbsent(normalized, trimToNull(displayName));
    }
  }

  private String normalizeAddress(String value) {
    return hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
  }

  private String csv(List<String> values) {
    if (values == null || values.isEmpty()) return null;
    List<String> normalized =
        values.stream().map(this::normalizeAddress).filter(Objects::nonNull).distinct().toList();
    return normalized.isEmpty() ? null : String.join(",", normalized);
  }

  private String htmlToText(String html) {
    String withoutNonContent = html.replaceAll("(?is)<(script|style|head)[^>]*>.*?</\\1>", " ");
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

  private String trimToNull(String value) {
    return hasText(value) ? value.trim() : null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public record PersistResult(
      int created, int updated, Set<Long> conversationIds, Set<Long> messageIds) {}
}
