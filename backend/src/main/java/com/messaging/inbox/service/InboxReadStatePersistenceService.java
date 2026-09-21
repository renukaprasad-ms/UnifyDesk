package com.messaging.inbox.service;

import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.InboxMessageDirection;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InboxReadStatePersistenceService {

  private final InboxMessageRepository messageRepository;
  private final InboxConversationRepository conversationRepository;

  @Transactional
  public ApplyResult apply(Collection<Long> messageIds, boolean read) {
    if (messageIds == null || messageIds.isEmpty()) {
      return new ApplyResult(0, Set.of());
    }

    List<InboxMessage> messages = messageRepository.findAllById(messageIds);
    Set<Long> conversationIds = new LinkedHashSet<>();
    int changed = 0;

    for (InboxMessage message : messages) {
      if (message.isRead() == read) continue;
      message.setRead(read);
      conversationIds.add(message.getConversation().getId());
      changed++;
    }

    messageRepository.saveAll(messages);
    messageRepository.flush();

    for (Long conversationId : conversationIds) {
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

    return new ApplyResult(changed, conversationIds);
  }

  public record ApplyResult(int changedMessages, Set<Long> conversationIds) {}
}
