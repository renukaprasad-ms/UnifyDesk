package com.messaging.channel.zoho;

import com.messaging.channel.email.EmailSentMessageHandler;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class ZohoMailInboxPersistenceService {

  private final InboxConversationRepository conversationRepository;
  private final InboxMessageRepository messageRepository;
  private final InboxMessageAttachmentRepository attachmentRepository;
  private final InboxParticipantRepository participantRepository;
  private final MessageStorageMeter messageStorageMeter;
  private final StorageQuotaService storageQuotaService;
  private final List<EmailSentMessageHandler> sentMessageHandlers;
  private final List<InboxMessagePostProcessor> messagePostProcessors;

  @Transactional
  public PersistResult persist(
      ChannelConnection connection, List<ZohoMailMessageSnapshot> snapshots) {
    if (snapshots == null || snapshots.isEmpty()) {
      return new PersistResult(0, 0, Set.of(), Set.of());
    }

    int created = 0;
    int updated = 0;
    Set<Long> touchedConversations = new LinkedHashSet<>();
    Set<Long> touchedMessages = new LinkedHashSet<>();
    List<StorageConsumption> storage = new ArrayList<>();

    for (ZohoMailMessageSnapshot snapshot : snapshots) {
      if (snapshot == null || !hasText(snapshot.messageId())) continue;

      InboxMessage message =
          messageRepository
              .findByConnectionIdAndExternalMessageId(connection.getId(), snapshot.messageId())
              .orElse(null);

      InboxConversation conversation =
          message == null
              ? conversationRepository
                  .findByConnectionIdAndExternalConversationId(
                      connection.getId(), snapshot.threadId())
                  .orElse(null)
              : message.getConversation();

      if (conversation == null) {
        conversation = new InboxConversation();
        conversation.setCompanyId(connection.getCompanyId());
        conversation.setConnection(connection);
        conversation.setProviderType(ProviderType.ZOHO_MAIL);
        conversation.setExternalConversationId(snapshot.threadId());
        conversation.setStatus(ConversationStatus.OPEN);
      }

      if (hasText(snapshot.subject())) conversation.setSubject(snapshot.subject());
      if (conversation.getLastMessageAt() == null
          || snapshot.sentAt().isAfter(conversation.getLastMessageAt())) {
        conversation.setLastMessageAt(snapshot.sentAt());
        conversation.setLastMessagePreview(
            hasText(snapshot.summary()) ? snapshot.summary() : htmlToText(snapshot.bodyHtml()));
      }
      // The Inbox contact-link post-processor writes via JDBC in this transaction.
      // Flush the conversation first so its FK parent exists before that JDBC insert.
      conversation = conversationRepository.saveAndFlush(conversation);

      boolean newMessage = message == null;
      if (newMessage) {
        message = new InboxMessage();
        message.setConnection(connection);
        message.setConversation(conversation);
        message.setExternalMessageId(snapshot.messageId());
      }

      boolean outbound =
          snapshot.folder() == com.messaging.channel.email.EmailFolder.SENT;
      message.setInternetMessageId(snapshot.mailId());
      message.setProviderHistoryId(null);
      message.setProviderFolderKey(snapshot.folder().providerKey());
      message.setFromAddress(snapshot.fromAddress());
      message.setReplyToAddress(snapshot.fromAddress());
      message.setToAddresses(csv(snapshot.to()));
      message.setCcAddresses(csv(snapshot.cc()));
      message.setBccAddresses(csv(snapshot.bcc()));
      message.setDirection(outbound ? InboxMessageDirection.OUTBOUND : InboxMessageDirection.INBOUND);
      message.setStatus(outbound ? InboxMessageStatus.SENT : InboxMessageStatus.RECEIVED);
      message.setSubject(snapshot.subject());
      message.setSnippet(snapshot.summary());
      message.setBodyHtml(snapshot.bodyHtml());
      message.setBodyText(htmlToText(snapshot.bodyHtml()));
      message.setSentAt(snapshot.sentAt());
      message.setRead(outbound || snapshot.read());

      boolean accountStorage = newMessage || message.getStorageBytes() == 0;
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
      if (message.getDirection() == InboxMessageDirection.INBOUND) {
        for (InboxMessagePostProcessor processor : messagePostProcessors) {
          processor.afterPersist(message);
        }
      }

      if (accountStorage && message.getStorageBytes() > 0) {
        storage.add(
            new StorageConsumption(
                "INBOX_MESSAGE", message.getId().toString(), message.getStorageBytes()));
      }

      upsertParticipants(conversation, snapshot);
      upsertAttachments(message, snapshot);
      touchedConversations.add(conversation.getId());
      touchedMessages.add(message.getId());
      if (newMessage) created++;
      else updated++;
    }

    storageQuotaService.consumeBatch(connection.getCompanyId(), StorageUsageType.MESSAGE, storage);
    messageRepository.flush();

    for (Long conversationId : touchedConversations) {
      conversationRepository
          .findById(conversationId)
          .ifPresent(
              conversation -> {
                long unread =
                    messageRepository.countByConversationIdAndDirectionAndReadFalse(
                        conversationId, InboxMessageDirection.INBOUND);
                conversation.setUnreadCount((int) Math.min(Integer.MAX_VALUE, unread));
                conversationRepository.save(conversation);
              });
    }

    return new PersistResult(created, updated, Set.copyOf(touchedConversations), Set.copyOf(touchedMessages));
  }

  private void upsertAttachments(
      InboxMessage message, ZohoMailMessageSnapshot snapshot) {
    if (snapshot.attachments() == null || snapshot.attachments().isEmpty()) return;

    for (ZohoMailAttachmentSnapshot source : snapshot.attachments()) {
      if (source == null || source.inline() || !hasText(source.attachmentId())) continue;
      String externalId =
          "zoho:" + snapshot.folderId() + ":" + source.attachmentId().trim();

      InboxMessageAttachment attachment =
          attachmentRepository
              .findByMessageIdAndExternalAttachmentId(message.getId(), externalId)
              .orElseGet(InboxMessageAttachment::new);
      if (attachment.getId() == null) {
        attachment.setMessage(message);
        attachment.setExternalAttachmentId(externalId);
      }
      attachment.setFilename(source.filename());
      attachment.setSizeBytes(source.sizeBytes());
      attachment.setMimeType(
          hasText(source.filename())
              ? MediaTypeFactory.getMediaType(source.filename())
                  .map(MediaType::toString)
                  .orElse(null)
              : null);
      attachmentRepository.save(attachment);
    }
  }

  private void upsertParticipants(
      InboxConversation conversation, ZohoMailMessageSnapshot snapshot) {
    LinkedHashMap<String, String> participants = new LinkedHashMap<>();
    add(participants, snapshot.fromAddress(), snapshot.fromDisplayName());
    snapshot.to().forEach(value -> add(participants, value, null));
    snapshot.cc().forEach(value -> add(participants, value, null));
    snapshot.bcc().forEach(value -> add(participants, value, null));

    for (var entry : participants.entrySet()) {
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
      if (hasText(entry.getValue())) participant.setDisplayName(entry.getValue());
      participantRepository.save(participant);
    }
  }

  private void add(LinkedHashMap<String, String> values, String address, String name) {
    if (!hasText(address)) return;
    values.putIfAbsent(address.trim().toLowerCase(Locale.ROOT), name);
  }

  private String csv(List<String> values) {
    return values == null || values.isEmpty() ? null : String.join(",", values);
  }

  private String htmlToText(String html) {
    if (!hasText(html)) return null;
    String withoutNonContent = html.replaceAll("(?is)<(script|style|head)[^>]*>.*?</\\1>", " ");
    String withBreaks =
        withoutNonContent
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</(p|div|tr|li|h[1-6])\\s*>", "\n");
    String value =
        HtmlUtils.htmlUnescape(withBreaks.replaceAll("(?s)<[^>]+>", " "))
            .replace('\u00A0', ' ')
            .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
            .replaceAll(" *\\n *", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    return value.isBlank() ? null : value;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public record PersistResult(
      int created, int updated, Set<Long> conversationIds, Set<Long> messageIds) {}
}
