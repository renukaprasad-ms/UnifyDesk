package com.messaging.inbox.repository;

import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.InboxMessageDirection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {

  Optional<InboxMessage> findByConnectionIdAndExternalMessageId(
      Long connectionId, String externalMessageId);

  List<InboxMessage> findByConversationIdOrderBySentAtAscCreatedAtAsc(Long conversationId);

  long countByConversationIdAndDirectionAndReadFalse(
      Long conversationId, InboxMessageDirection direction);
}
