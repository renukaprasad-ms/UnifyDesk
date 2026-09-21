package com.messaging.inbox.service;

import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
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
import com.messaging.realtime.service.RealtimeService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InboxReadStateService {

  private static final int BULK_BATCH_SIZE = 250;

  private final InboxMessageRepository messageRepository;
  private final InboxConversationRepository conversationRepository;
  private final ChannelConnectionRepository connectionRepository;
  private final InboxProviderReadStateService providerReadStateService;
  private final InboxReadStatePersistenceService persistenceService;
  private final AsyncJobService asyncJobService;
  private final RealtimeService realtimeService;

  public InboxReadStateResponse setMessageReadState(
      Long companyId, Long messageId, boolean read) {
    InboxMessage message =
        messageRepository
            .findByIdAndCompanyId(messageId, companyId)
            .orElseThrow(() -> new NotFoundException("Inbox message not found"));
    ChannelConnection connection =
        requireMutable(connectionFor(companyId, message.getConnection().getId()));

    if (message.getStatus() == InboxMessageStatus.DELETED
        || message.getProviderFolderKey() == null) {
      throw new BadRequestException("This message is no longer available in the provider mailbox");
    }
    if (message.isRead() == read) {
      return new InboxReadStateResponse(read, 0, 0);
    }

    providerReadStateService.update(connection, List.of(message), read, false);
    var applied = persistenceService.apply(List.of(message.getId()), read);
    publishChange(connection, applied.changedMessages(), read);
    return new InboxReadStateResponse(
        read, applied.changedMessages(), applied.conversationIds().size());
  }

  public InboxReadStateResponse setConversationReadState(
      Long companyId, Long conversationId, boolean read) {
    InboxConversation conversation =
        conversationRepository
            .findByIdAndCompanyId(conversationId, companyId)
            .orElseThrow(() -> new NotFoundException("Inbox conversation not found"));
    ChannelConnection connection =
        requireMutable(connectionFor(companyId, conversation.getConnection().getId()));

    List<InboxMessage> candidates =
        messageRepository
            .findByConversationIdAndStatusNotOrderBySentAtAscCreatedAtAsc(
                conversationId, InboxMessageStatus.DELETED)
            .stream()
            .filter(message -> message.getDirection() == InboxMessageDirection.INBOUND)
            .filter(
                message ->
                    EmailFolder.INBOX.providerKey().equals(message.getProviderFolderKey()))
            .filter(message -> message.isRead() != read)
            .toList();

    if (candidates.isEmpty()) {
      return new InboxReadStateResponse(read, 0, 0);
    }

    providerReadStateService.update(connection, candidates, read, false);
    var applied =
        persistenceService.apply(
            candidates.stream().map(InboxMessage::getId).toList(), read);
    publishChange(connection, applied.changedMessages(), read);
    return new InboxReadStateResponse(
        read, applied.changedMessages(), applied.conversationIds().size());
  }

  public void processMarkAllRead(
      Long jobId, Long companyId, Long connectionId, EmailFolder folder) {
    EmailFolder resolvedFolder = folder == null ? EmailFolder.INBOX : folder;
    if (resolvedFolder != EmailFolder.INBOX) {
      throw new BadRequestException("Mark all as read is only available for the inbox");
    }

    ChannelConnection connection = requireMutable(connectionFor(companyId, connectionId));
    long total = unreadCount(companyId, connectionId, resolvedFolder);
    asyncJobService.startOrResume(jobId, total, "Marking inbox messages as read");

    if (total == 0) {
      asyncJobService.complete(jobId, "No unread messages");
      return;
    }

    while (true) {
      List<InboxMessage> batch =
          messageRepository.findUnreadByScope(
              companyId,
              connectionId,
              ChannelConnectionStatus.ACTIVE,
              resolvedFolder.providerKey(),
              InboxMessageDirection.INBOUND,
              InboxMessageStatus.DELETED,
              PageRequest.of(0, BULK_BATCH_SIZE));
      if (batch.isEmpty()) break;

      providerReadStateService.update(connection, batch, true, true);
      var applied =
          persistenceService.apply(batch.stream().map(InboxMessage::getId).toList(), true);
      publishChange(connection, applied.changedMessages(), true);

      long remaining = unreadCount(companyId, connectionId, resolvedFolder);
      long completed = Math.max(0, total - remaining);
      asyncJobService.progress(
          jobId,
          completed,
          total,
          "Marked " + completed + " of " + total + " messages as read");
    }

    asyncJobService.complete(jobId, "Inbox marked as read");
  }

  private long unreadCount(Long companyId, Long connectionId, EmailFolder folder) {
    return messageRepository.countUnreadByScope(
        companyId,
        connectionId,
        ChannelConnectionStatus.ACTIVE,
        folder.providerKey(),
        InboxMessageDirection.INBOUND,
        InboxMessageStatus.DELETED);
  }

  private ChannelConnection connectionFor(Long companyId, Long connectionId) {
    return connectionRepository
        .findByIdAndCompanyId(connectionId, companyId)
        .orElseThrow(() -> new NotFoundException("Email connection not found"));
  }

  private ChannelConnection requireMutable(ChannelConnection connection) {
    if (connection.getChannelType() != ChannelType.EMAIL) {
      throw new BadRequestException("Connection is not an email channel");
    }
    if (connection.getStatus() != ChannelConnectionStatus.ACTIVE) {
      throw new BadRequestException("Email connection is not active");
    }
    return connection;
  }

  private void publishChange(ChannelConnection connection, int changed, boolean read) {
    if (changed <= 0) return;
    realtimeService.sendToCompany(
        connection.getCompanyId(),
        "inbox.email.changed",
        Map.of(
            "connectionId", connection.getId().toString(),
            "provider", connection.getProviderType().name(),
            "readStateChanged", changed,
            "read", read));
  }
}
