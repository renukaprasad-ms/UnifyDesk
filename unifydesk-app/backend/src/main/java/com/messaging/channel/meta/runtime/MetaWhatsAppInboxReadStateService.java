package com.messaging.channel.meta.runtime;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.inbox.dto.InboxReadStateResponse;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.InboxMessageDirection;
import com.messaging.inbox.enums.InboxMessageStatus;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.inbox.service.InboxReadStatePersistenceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaWhatsAppInboxReadStateService {
  private static final int BATCH = 250;

  private final InboxMessageRepository messageRepository;
  private final InboxConversationRepository conversationRepository;
  private final ChannelConnectionRepository connectionRepository;
  private final InboxReadStatePersistenceService persistenceService;
  private final AsyncJobService asyncJobService;
  private final MetaWhatsAppRealtimePublisher realtimePublisher;

  public InboxReadStateResponse setMessage(long companyId, long messageId, boolean read) {
    InboxMessage message =
        messageRepository
            .findByIdAndCompanyId(messageId, companyId)
            .orElseThrow(() -> new NotFoundException("Inbox message not found"));
    requireWhatsApp(message.getConnection().getId(), companyId);
    validateInbound(message);
    if (message.isRead() == read) return new InboxReadStateResponse(read, 0, 0);
    var applied = persistenceService.apply(List.of(messageId), read);
    publish(companyId, message.getConnection().getId(), applied);
    return new InboxReadStateResponse(
        read, applied.changedMessages(), applied.conversationIds().size());
  }

  public InboxReadStateResponse setConversation(
      long companyId, long conversationId, boolean read) {
    InboxConversation conversation =
        conversationRepository
            .findByIdAndCompanyId(conversationId, companyId)
            .orElseThrow(() -> new NotFoundException("Inbox conversation not found"));
    requireWhatsApp(conversation.getConnection().getId(), companyId);
    List<Long> ids =
        messageRepository
            .findByConversationIdAndStatusNotOrderBySentAtAscCreatedAtAsc(
                conversationId, InboxMessageStatus.DELETED)
            .stream()
            .filter(message -> message.getDirection() == InboxMessageDirection.INBOUND)
            .filter(message -> "WHATSAPP".equals(message.getProviderFolderKey()))
            .filter(message -> message.isRead() != read)
            .map(InboxMessage::getId)
            .toList();
    var applied = persistenceService.apply(ids, read);
    publish(companyId, conversation.getConnection().getId(), applied);
    return new InboxReadStateResponse(
        read, applied.changedMessages(), applied.conversationIds().size());
  }

  public AsyncJobResponse markAllRead(long companyId, long userId, long connectionId) {
    requireWhatsApp(connectionId, companyId);
    String resourceId = connectionId + ":WHATSAPP";
    AsyncJobResponse job =
        asyncJobService.create(
            companyId,
            userId,
            AsyncJobType.INBOX_READ_SYNC,
            resourceId,
            "{\"connectionId\":\"" + connectionId + "\",\"folder\":\"WHATSAPP\"}",
            "Mark all WhatsApp messages as read");

    long total =
        messageRepository.countUnreadByScope(
            companyId,
            connectionId,
            ChannelConnectionStatus.ACTIVE,
            "WHATSAPP",
            InboxMessageDirection.INBOUND,
            InboxMessageStatus.DELETED);
    asyncJobService.startOrResume(Long.valueOf(job.jobId()), total, "Marking WhatsApp messages as read");
    long processed = 0;
    while (true) {
      List<InboxMessage> batch =
          messageRepository.findUnreadByScope(
              companyId,
              connectionId,
              ChannelConnectionStatus.ACTIVE,
              "WHATSAPP",
              InboxMessageDirection.INBOUND,
              InboxMessageStatus.DELETED,
              PageRequest.of(0, BATCH));
      if (batch.isEmpty()) break;
      var applied =
          persistenceService.apply(batch.stream().map(InboxMessage::getId).toList(), true);
      processed += applied.changedMessages();
      publish(companyId, connectionId, applied);
      asyncJobService.progress(
          Long.valueOf(job.jobId()), processed, total, "Marked " + processed + " WhatsApp messages as read");
    }
    return asyncJobService.complete(Long.valueOf(job.jobId()), "WhatsApp inbox marked as read");
  }

  private void validateInbound(InboxMessage message) {
    if (message.getStatus() == InboxMessageStatus.DELETED
        || message.getDirection() != InboxMessageDirection.INBOUND
        || !"WHATSAPP".equals(message.getProviderFolderKey())) {
      throw new BadRequestException("Read state can only be changed for inbound WhatsApp messages");
    }
  }

  private void requireWhatsApp(long connectionId, long companyId) {
    var connection =
        connectionRepository
            .findByIdAndCompanyId(connectionId, companyId)
            .orElseThrow(() -> new NotFoundException("WhatsApp connection not found"));
    if (connection.getProviderType() != ProviderType.META_WHATSAPP
        || connection.getStatus() != ChannelConnectionStatus.ACTIVE) {
      throw new NotFoundException("WhatsApp connection not found");
    }
  }

  private void publish(
      long companyId,
      long connectionId,
      InboxReadStatePersistenceService.ApplyResult applied) {
    if (applied.changedMessages() <= 0) return;
    realtimePublisher.readStateChanged(
        companyId, connectionId, applied.conversationIds(), applied.messageIds());
  }
}
