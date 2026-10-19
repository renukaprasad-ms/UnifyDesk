package com.messaging.channel.imapsmtp;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.email.EmailSentMessageHandler;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.imapsmtp.ImapSmtpMailClient.MailboxMessage;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.entity.InboxParticipant;
import com.messaging.inbox.enums.ConversationStatus;
import com.messaging.inbox.enums.InboxMessageDirection;
import com.messaging.inbox.enums.InboxMessageStatus;
import com.messaging.inbox.enums.InboxParticipantType;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.inbox.repository.InboxParticipantRepository;
import com.messaging.inbox.service.InboxMessagePostProcessor;
import com.messaging.storage.enums.StorageUsageType;
import com.messaging.storage.service.MessageStorageMeter;
import com.messaging.storage.service.StorageQuotaService;
import com.messaging.storage.service.StorageQuotaService.StorageConsumption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class ImapSmtpInboxPersistenceService {

  private final InboxConversationRepository conversationRepository;
  private final InboxMessageRepository messageRepository;
  private final InboxParticipantRepository participantRepository;
  private final MessageStorageMeter messageStorageMeter;
  private final StorageQuotaService storageQuotaService;
  private final List<EmailSentMessageHandler> sentMessageHandlers;
  private final List<InboxMessagePostProcessor> messagePostProcessors;

  @Transactional
  public PersistResult persist(ChannelConnection connection, List<MailboxMessage> snapshots) {
    if (snapshots == null || snapshots.isEmpty()) {
      return new PersistResult(0, 0, Set.of(), Set.of());
    }

    int created = 0;
    int updated = 0;
    Set<Long> touchedConversations = new LinkedHashSet<>();
    Set<Long> touchedMessages = new LinkedHashSet<>();
    List<StorageConsumption> storage = new ArrayList<>();

    for (MailboxMessage snapshot : snapshots) {
      if (snapshot == null || !hasText(snapshot.externalMessageId())) continue;
      if (snapshot.folder() == EmailFolder.SENT
          && hasText(snapshot.broadcastCorrelationId())
          && handleBroadcastSentMessage(connection, snapshot)) {
        continue;
      }

      InboxMessage message =
          messageRepository
              .findByConnectionIdAndExternalMessageId(
                  connection.getId(), snapshot.externalMessageId())
              .orElseGet(
                  () ->
                      hasText(snapshot.internetMessageId())
                          ? messageRepository
                              .findByConnectionIdAndInternetMessageId(
                                  connection.getId(), snapshot.internetMessageId())
                              .orElse(null)
                          : null);

      InboxConversation conversation =
          message == null
              ? conversationRepository
                  .findByConnectionIdAndExternalConversationId(
                      connection.getId(), snapshot.threadKey())
                  .orElse(null)
              : message.getConversation();

      boolean newConversation = conversation == null;
      if (newConversation) {
        conversation = new InboxConversation();
        conversation.setCompanyId(connection.getCompanyId());
        conversation.setConnection(connection);
        conversation.setProviderType(ProviderType.IMAP_SMTP);
        conversation.setExternalConversationId(snapshot.threadKey());
        conversation.setStatus(ConversationStatus.OPEN);
      }

      String subject = trimToNull(snapshot.subject());
      String text = trimToNull(snapshot.bodyText());
      String html = trimToNull(snapshot.bodyHtml());
      if (text == null && html != null) {
        text = htmlToText(html);
      }
      String preview = preview(text != null ? text : html);

      if (subject != null) {
        conversation.setSubject(subject);
      }
      if (conversation.getLastMessageAt() == null
          || snapshot.sentAt().isAfter(conversation.getLastMessageAt())) {
        conversation.setLastMessageAt(snapshot.sentAt());
        conversation.setLastMessagePreview(preview);
      }
      conversation = conversationRepository.save(conversation);

      boolean newMessage = message == null;
      if (newMessage) {
        message = new InboxMessage();
        message.setConversation(conversation);
        message.setConnection(connection);
        message.setExternalMessageId(snapshot.externalMessageId());
      }

      message.setInternetMessageId(trimToNull(snapshot.internetMessageId()));
      message.setProviderHistoryId(Long.toString(snapshot.uid()));
      message.setProviderFolderKey(snapshot.folder().providerKey());
      message.setFromAddress(normalizeAddress(snapshot.from()));
      message.setReplyToAddress(normalizeAddress(snapshot.replyTo()));
      message.setToAddresses(csv(snapshot.to()));
      message.setCcAddresses(csv(snapshot.cc()));
      message.setBccAddresses(csv(snapshot.bcc()));
      message.setInReplyTo(trimToNull(snapshot.inReplyTo()));
      message.setReferencesHeader(trimToNull(snapshot.references()));
      message.setDirection(
          snapshot.folder() == EmailFolder.SENT
              ? InboxMessageDirection.OUTBOUND
              : InboxMessageDirection.INBOUND);
      message.setStatus(
          snapshot.folder() == EmailFolder.SENT
              ? InboxMessageStatus.SENT
              : InboxMessageStatus.RECEIVED);
      message.setSubject(subject);
      message.setSnippet(preview);
      message.setBodyText(text);
      message.setBodyHtml(html);
      message.setSentAt(snapshot.sentAt());
      message.setRead(snapshot.folder() == EmailFolder.SENT || !snapshot.unread());

      boolean accountStorage = newMessage || message.getStorageBytes() == 0;
      if (accountStorage) {
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

      message = messageRepository.save(message);
      if (message.getDirection() == InboxMessageDirection.INBOUND) {
        for (InboxMessagePostProcessor processor : messagePostProcessors) {
          processor.afterPersist(message);
        }
      }
      touchedMessages.add(message.getId());
      if (accountStorage) {
        storage.add(
            new StorageConsumption(
                "INBOX_MESSAGE", message.getId().toString(), message.getStorageBytes()));
      }

      upsertParticipants(conversation, snapshot);
      touchedConversations.add(conversation.getId());
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
                conversation.setUnreadCount((int) Math.min(unread, Integer.MAX_VALUE));
                conversationRepository.save(conversation);
              });
    }

    return new PersistResult(created, updated, touchedConversations, touchedMessages);
  }

  private boolean handleBroadcastSentMessage(
      ChannelConnection connection, MailboxMessage snapshot) {
    for (EmailSentMessageHandler handler : sentMessageHandlers) {
      if (handler.handle(
          connection,
          ProviderType.IMAP_SMTP,
          snapshot.broadcastCorrelationId(),
          snapshot.externalMessageId(),
          snapshot.internetMessageId(),
          snapshot.sentAt())) {
        return true;
      }
    }
    return false;
  }

  private void upsertParticipants(InboxConversation conversation, MailboxMessage snapshot) {
    Set<String> addresses = new LinkedHashSet<>();
    addAddress(addresses, snapshot.from());
    addAddress(addresses, snapshot.replyTo());
    if (snapshot.to() != null) snapshot.to().forEach(value -> addAddress(addresses, value));
    if (snapshot.cc() != null) snapshot.cc().forEach(value -> addAddress(addresses, value));
    if (snapshot.bcc() != null) snapshot.bcc().forEach(value -> addAddress(addresses, value));

    for (String address : addresses) {
      InboxParticipant participant =
          participantRepository
              .findByConversationIdAndParticipantTypeAndAddressIgnoreCase(
                  conversation.getId(), InboxParticipantType.EMAIL_ADDRESS, address)
              .orElseGet(InboxParticipant::new);
      if (participant.getId() == null) {
        participant.setConversation(conversation);
        participant.setParticipantType(InboxParticipantType.EMAIL_ADDRESS);
        participant.setAddress(address);
      }
      participantRepository.save(participant);
    }
  }

  private void addAddress(Set<String> values, String value) {
    String normalized = normalizeAddress(value);
    if (normalized != null) values.add(normalized);
  }

  private String normalizeAddress(String value) {
    return hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
  }

  private String csv(List<String> values) {
    if (values == null || values.isEmpty()) return null;
    return String.join(",", values);
  }

  private String preview(String value) {
    String normalized = trimToNull(value);
    if (normalized == null) return null;
    normalized = normalized.replaceAll("\\s+", " ");
    return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
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
