package com.messaging.inbox.repository;

import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.InboxMessageDirection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {

  Optional<InboxMessage> findByConnectionIdAndExternalMessageId(
      Long connectionId, String externalMessageId);

  @Query(
      "select m.externalMessageId from InboxMessage m "
          + "where m.connection.id = :connectionId and m.externalMessageId in :externalMessageIds")
  Set<String> findExistingExternalMessageIds(
      @Param("connectionId") Long connectionId,
      @Param("externalMessageIds") List<String> externalMessageIds);

  List<InboxMessage> findByConversationIdOrderBySentAtAscCreatedAtAsc(Long conversationId);

  long countByConversationIdAndDirectionAndReadFalse(
      Long conversationId, InboxMessageDirection direction);

  @Query(
      "select coalesce(sum(m.storageBytes), 0) from InboxMessage m "
          + "where m.conversation.companyId = :companyId")
  long sumStorageBytesByCompanyId(@Param("companyId") Long companyId);
}
