package com.messaging.inbox.repository;

import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.InboxMessageDirection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {

  Optional<InboxMessage> findByConnectionIdAndExternalMessageId(
      Long connectionId, String externalMessageId);

  Optional<InboxMessage> findByConnectionIdAndInternetMessageId(
      Long connectionId, String internetMessageId);

  @Query(
      "select m from InboxMessage m "
          + "where m.id = :messageId and m.conversation.companyId = :companyId")
  Optional<InboxMessage> findByIdAndCompanyId(
      @Param("messageId") Long messageId, @Param("companyId") Long companyId);

  List<InboxMessage> findByConnectionIdAndExternalMessageIdIn(
      Long connectionId, Collection<String> externalMessageIds);

  @Query(
      "select m.externalMessageId from InboxMessage m "
          + "where m.connection.id = :connectionId and m.externalMessageId in :externalMessageIds")
  Set<String> findExistingExternalMessageIds(
      @Param("connectionId") Long connectionId,
      @Param("externalMessageIds") List<String> externalMessageIds);

  List<InboxMessage> findByConversationIdOrderBySentAtAscCreatedAtAsc(Long conversationId);

  List<InboxMessage> findByConversationIdAndStatusNotOrderBySentAtAscCreatedAtAsc(
      Long conversationId, com.messaging.inbox.enums.InboxMessageStatus status);

  Optional<InboxMessage> findFirstByConversationIdAndStatusNotOrderBySentAtDescCreatedAtDesc(
      Long conversationId, com.messaging.inbox.enums.InboxMessageStatus status);

  List<InboxMessage>
      findByConnectionIdAndProviderFolderKeyIsNotNullAndSentAtGreaterThanEqualAndStatusNot(
          Long connectionId,
          java.time.Instant sentAt,
          com.messaging.inbox.enums.InboxMessageStatus status);

  long countByConversationIdAndDirectionAndReadFalse(
      Long conversationId, InboxMessageDirection direction);

  @Query(
      """
      select count(m)
      from InboxMessage m
      where m.conversation.companyId = :companyId
        and m.connection.id = :connectionId
        and m.connection.status = :connectionStatus
        and m.providerFolderKey = :folderKey
        and m.direction = :direction
        and m.read = false
        and m.status <> :excludedStatus
      """)
  long countUnreadByScope(
      @Param("companyId") Long companyId,
      @Param("connectionId") Long connectionId,
      @Param("connectionStatus") com.messaging.channel.enums.ChannelConnectionStatus connectionStatus,
      @Param("folderKey") String folderKey,
      @Param("direction") InboxMessageDirection direction,
      @Param("excludedStatus") com.messaging.inbox.enums.InboxMessageStatus excludedStatus);

  @Query(
      """
      select m
      from InboxMessage m
      where m.conversation.companyId = :companyId
        and m.connection.id = :connectionId
        and m.connection.status = :connectionStatus
        and m.providerFolderKey = :folderKey
        and m.direction = :direction
        and m.read = false
        and m.status <> :excludedStatus
      order by m.id
      """)
  List<InboxMessage> findUnreadByScope(
      @Param("companyId") Long companyId,
      @Param("connectionId") Long connectionId,
      @Param("connectionStatus") com.messaging.channel.enums.ChannelConnectionStatus connectionStatus,
      @Param("folderKey") String folderKey,
      @Param("direction") InboxMessageDirection direction,
      @Param("excludedStatus") com.messaging.inbox.enums.InboxMessageStatus excludedStatus,
      Pageable pageable);

  @Query(
      "select coalesce(sum(m.storageBytes), 0) from InboxMessage m "
          + "where m.conversation.companyId = :companyId")
  long sumStorageBytesByCompanyId(@Param("companyId") Long companyId);
}
