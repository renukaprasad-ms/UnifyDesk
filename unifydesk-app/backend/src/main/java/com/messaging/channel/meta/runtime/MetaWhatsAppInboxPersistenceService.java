package com.messaging.channel.meta.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.id.IdGenerator;
import com.messaging.inbox.dto.InboxEmailChangedEvent;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.entity.InboxMessageAttachment;
import com.messaging.inbox.enums.ConversationStatus;
import com.messaging.inbox.enums.InboxMessageDirection;
import com.messaging.inbox.enums.InboxMessageStatus;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageAttachmentRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.inbox.service.InboxMessagePostProcessor;
import com.messaging.media.entity.Media;
import com.messaging.realtime.service.RealtimeService;
import com.messaging.storage.enums.StorageUsageType;
import com.messaging.storage.service.MessageStorageMeter;
import com.messaging.storage.service.StorageQuotaService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class MetaWhatsAppInboxPersistenceService {
  private final ObjectMapper objectMapper;
  private final MetaWhatsAppInboundMessageMapper inboundMessageMapper;
  private final MetaWhatsAppTenantResolver tenantResolver;
  private final ChannelConnectionRepository connectionRepository;
  private final InboxConversationRepository conversationRepository;
  private final InboxMessageRepository messageRepository;
  private final InboxMessageAttachmentRepository attachmentRepository;
  private final MessageStorageMeter messageStorageMeter;
  private final StorageQuotaService storageQuotaService;
  private final List<InboxMessagePostProcessor> messagePostProcessors;
  private final RealtimeService realtimeService;
  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public InboundResult persistInbound(
      MetaWhatsAppWebhookEventStore.ClaimedEvent event) {
    JsonNode root = read(event.payloadJson());
    JsonNode message = root.path("message");
    String providerMessageId = text(message, "id");
    String customerKey = text(message, "from");
    String phoneNumberId = text(root, "phoneNumberId");
    if (providerMessageId == null || customerKey == null || phoneNumberId == null) {
      throw new MetaWhatsAppRuntimeException(
          "WHATSAPP_WEBHOOK_INVALID_MESSAGE", false, "Inbound message identifiers are missing");
    }

    var tenant = tenantResolver.resolveTrustedPhone(phoneNumberId);
    ChannelConnection connection =
        connectionRepository
            .findByIdAndCompanyId(tenant.connectionId(), tenant.companyId())
            .orElseThrow(
                () ->
                    new MetaWhatsAppRuntimeException(
                        "WHATSAPP_TENANT_MAPPING_CONFLICT", false, "Connection no longer exists"));

    MetaWhatsAppInboundMessageMapper.MappedMessage shape = inboundMessageMapper.map(message);
    InboxMessage existing =
        messageRepository
            .findByConnectionIdAndExternalMessageId(connection.getId(), providerMessageId)
            .orElse(null);
    if (existing != null) {
      return result(existing, connection, tenant, shape, false);
    }

    String externalConversationId = "wa:" + customerKey;
    long candidateConversationId = IdGenerator.nextId();
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        insert into inbox_conversations
          (id,company_id,connection_id,provider_type,external_conversation_id,
           last_message_preview,last_message_at,unread_count,status,created_at,updated_at)
        values (?,?,?,?,?,?,?,0,'OPEN',?,?)
        on conflict (connection_id,external_conversation_id) do nothing
        """,
        candidateConversationId,
        tenant.companyId(),
        connection.getId(),
        ProviderType.META_WHATSAPP.name(),
        externalConversationId,
        shape.preview(),
        Timestamp.from(event.providerEventAt()),
        Timestamp.from(now),
        Timestamp.from(now));

    InboxConversation conversation =
        conversationRepository
            .findByConnectionIdAndExternalConversationId(connection.getId(), externalConversationId)
            .orElseThrow(
                () ->
                    new MetaWhatsAppRuntimeException(
                        "WHATSAPP_INBOX_PERSIST_FAILED", true, "Conversation insert was not visible"));

    jdbcTemplate.update(
        """
        insert into inbox_participants
          (id,conversation_id,participant_type,address,display_name,created_at,updated_at)
        values (?,?,'PHONE_NUMBER',?,?,?,?)
        on conflict (conversation_id,participant_type,address)
        do update set display_name=coalesce(excluded.display_name,inbox_participants.display_name),
                      updated_at=excluded.updated_at
        """,
        IdGenerator.nextId(),
        conversation.getId(),
        customerKey,
        contactName(root),
        Timestamp.from(now),
        Timestamp.from(now));

    InboxMessage inboxMessage = new InboxMessage();
    inboxMessage.setConversation(conversation);
    inboxMessage.setConnection(connection);
    inboxMessage.setExternalMessageId(providerMessageId);
    inboxMessage.setProviderFolderKey("WHATSAPP");
    inboxMessage.setFromAddress(customerKey);
    inboxMessage.setToAddresses(phoneNumberId);
    inboxMessage.setDirection(InboxMessageDirection.INBOUND);
    inboxMessage.setStatus(InboxMessageStatus.RECEIVED);
    inboxMessage.setSnippet(shape.preview());
    inboxMessage.setBodyText(shape.bodyText());
    inboxMessage.setInReplyTo(shape.replyToProviderMessageId());
    inboxMessage.setSentAt(event.providerEventAt());
    inboxMessage.setRead(false);
    inboxMessage.setStorageBytes(
        messageStorageMeter.measure(
            providerMessageId, null, null, null, shape.preview(), shape.bodyText(), null));
    inboxMessage = messageRepository.saveAndFlush(inboxMessage);

    storageQuotaService.consume(
        tenant.companyId(),
        StorageUsageType.MESSAGE,
        "INBOX_MESSAGE",
        inboxMessage.getId().toString(),
        inboxMessage.getStorageBytes());

    jdbcTemplate.update(
        """
        insert into meta_whatsapp_message_runtime
          (id,company_id,connection_id,inbox_message_id,provider_message_id,direction,
           message_kind,detailed_status,status_rank,provider_status_at,created_at,updated_at)
        values (?, ?, ?, ?, ?, 'INBOUND', ?, 'ACCEPTED', 10, ?, ?, ?)
        on conflict (connection_id,provider_message_id) where provider_message_id is not null
        do nothing
        """,
        IdGenerator.nextId(),
        tenant.companyId(),
        connection.getId(),
        inboxMessage.getId(),
        providerMessageId,
        shape.kind(),
        Timestamp.from(event.providerEventAt()),
        Timestamp.from(now),
        Timestamp.from(now));

    jdbcTemplate.update(
        """
        insert into meta_whatsapp_customer_sessions
          (id,company_id,connection_id,customer_key,last_customer_message_at,updated_at)
        values (?,?,?,?,?,?)
        on conflict (connection_id,customer_key)
        do update set last_customer_message_at =
             greatest(meta_whatsapp_customer_sessions.last_customer_message_at,
                      excluded.last_customer_message_at),
                      updated_at=excluded.updated_at
        """,
        IdGenerator.nextId(),
        tenant.companyId(),
        connection.getId(),
        customerKey,
        Timestamp.from(event.providerEventAt()),
        Timestamp.from(now));

    conversation.setLastMessageAt(event.providerEventAt());
    conversation.setLastMessagePreview(shape.preview());
    long unread =
        messageRepository.countByConversationIdAndDirectionAndReadFalse(
            conversation.getId(), InboxMessageDirection.INBOUND);
    conversation.setUnreadCount((int) Math.min(Integer.MAX_VALUE, unread));
    conversationRepository.save(conversation);

    for (InboxMessagePostProcessor processor : messagePostProcessors) {
      processor.afterPersist(inboxMessage);
    }
    scheduleRealtime(connection, conversation.getId(), inboxMessage.getId());
    return result(inboxMessage, connection, tenant, shape, true);
  }

  @Transactional(readOnly = true)
  public boolean hasMediaLink(long inboxMessageId, String providerMediaId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from meta_whatsapp_message_media where message_id=? and provider_media_id=?",
            Integer.class,
            inboxMessageId,
            providerMediaId);
    return count != null && count > 0;
  }

  @Transactional
  public void linkMedia(
      long inboxMessageId,
      String providerMediaId,
      String fileName,
      String mimeType,
      Media media) {
    InboxMessage message =
        messageRepository
            .findById(inboxMessageId)
            .orElseThrow(
                () ->
                    new MetaWhatsAppRuntimeException(
                        "WHATSAPP_INBOX_PERSIST_FAILED", false, "Inbox message is missing"));
    InboxMessageAttachment attachment =
        attachmentRepository
            .findByMessageIdAndExternalAttachmentId(inboxMessageId, providerMediaId)
            .orElseGet(InboxMessageAttachment::new);
    if (attachment.getId() == null) {
      attachment.setMessage(message);
      attachment.setExternalAttachmentId(providerMediaId);
    }
    attachment.setFilename(fileName);
    attachment.setMimeType(mimeType);
    attachment.setSizeBytes(media.getSizeBytes());
    attachmentRepository.save(attachment);
    jdbcTemplate.update(
        """
        insert into meta_whatsapp_message_media
          (id,message_id,provider_media_id,media_id,direction,created_at)
        values (?,?,?,?,'INBOUND',now())
        on conflict (message_id,provider_media_id) do nothing
        """,
        IdGenerator.nextId(),
        inboxMessageId,
        providerMediaId,
        media.getId());
  }

  private InboundResult result(
      InboxMessage message,
      ChannelConnection connection,
      MetaWhatsAppTenantResolver.ResolvedTenant tenant,
      MetaWhatsAppInboundMessageMapper.MappedMessage shape,
      boolean created) {
    return new InboundResult(
        tenant.companyId(),
        connection.getId(),
        connection.getCreatedByUserId(),
        message.getId(),
        shape.providerMediaId(),
        shape.mimeType(),
        shape.fileName(),
        created);
  }

  private String contactName(JsonNode root) {
    JsonNode contacts = root.path("contacts");
    if (!contacts.isArray() || contacts.isEmpty()) return null;
    return text(contacts.get(0).path("profile"), "name");
  }

  private JsonNode read(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception exception) {
      throw new MetaWhatsAppRuntimeException(
          "WHATSAPP_WEBHOOK_INVALID_MESSAGE", false, "Stored webhook payload is invalid", exception);
    }
  }

  private String text(JsonNode node, String field) {
    if (node == null) return null;
    JsonNode value = node.get(field);
    return value == null || !value.isValueNode() || value.asText().isBlank()
        ? null
        : value.asText().trim();
  }

  private String compact(String value, int max) {
    if (value == null) return null;
    String normalized = value.replaceAll("\\s+", " ").trim();
    return normalized.length() <= max ? normalized : normalized.substring(0, max);
  }

  private void scheduleRealtime(ChannelConnection connection, long conversationId, long messageId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              realtimeService.sendToCompany(
                  connection.getCompanyId(),
                  "inbox.email.changed",
                  InboxEmailChangedEvent.delta(
                      connection.getId(),
                      ProviderType.META_WHATSAPP,
                      List.of(conversationId),
                      List.of(messageId),
                      List.of()));
            } catch (RuntimeException ignored) {
              // Realtime is advisory; durable Inbox state remains authoritative.
            }
          }
        });
  }

  public record InboundResult(
      long companyId,
      long connectionId,
      long ownerUserId,
      long inboxMessageId,
      String providerMediaId,
      String mimeType,
      String fileName,
      boolean created) {}
}
