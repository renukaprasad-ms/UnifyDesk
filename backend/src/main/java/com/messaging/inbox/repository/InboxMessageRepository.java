package com.messaging.inbox.repository;

import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.InboxMessageDirection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {

  Optional<InboxMessage> findByConnectionIdAndExternalMessageId(
      Long connectionId, String externalMessageId);

  List<InboxMessage> findByConversationIdOrderBySentAtAscCreatedAtAsc(Long conversationId);

  long countByConversationIdAndDirectionAndReadFalse(
      Long conversationId, InboxMessageDirection direction);

  @Query(
      "select coalesce(sum(m.storageBytes), 0) from InboxMessage m "
          + "where m.conversation.companyId = :companyId")
  long sumStorageBytesByCompanyId(@Param("companyId") Long companyId);
}
