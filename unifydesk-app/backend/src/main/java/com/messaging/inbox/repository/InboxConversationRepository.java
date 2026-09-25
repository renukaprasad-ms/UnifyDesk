package com.messaging.inbox.repository;

import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.inbox.entity.InboxConversation;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboxConversationRepository extends JpaRepository<InboxConversation, Long> {

  Optional<InboxConversation> findByConnectionIdAndExternalConversationId(
      Long connectionId, String externalConversationId);

  List<InboxConversation> findByConnectionIdAndExternalConversationIdIn(
      Long connectionId, Collection<String> externalConversationIds);

  Optional<InboxConversation> findByIdAndCompanyId(Long id, Long companyId);

  @Query(
      """
      select distinct c
      from InboxConversation c
      where c.companyId = :companyId
        and c.connection.id = :connectionId
        and c.connection.status = :connectionStatus
        and c.id in :conversationIds
        and exists (
          select m.id
          from InboxMessage m
          where m.conversation.id = c.id
            and m.providerFolderKey = :folderKey
            and m.status <> :excludedStatus
        )
      """)
  List<InboxConversation> findVisibleByIdsAndFolder(
      @Param("companyId") Long companyId,
      @Param("connectionId") Long connectionId,
      @Param("connectionStatus") ChannelConnectionStatus connectionStatus,
      @Param("conversationIds") Set<Long> conversationIds,
      @Param("folderKey") String folderKey,
      @Param("excludedStatus") com.messaging.inbox.enums.InboxMessageStatus excludedStatus);

  Page<InboxConversation> findByCompanyIdOrderByLastMessageAtDescCreatedAtDesc(
      Long companyId, Pageable pageable);

  Page<InboxConversation> findByCompanyIdAndConnectionIdOrderByLastMessageAtDescCreatedAtDesc(
      Long companyId, Long connectionId, Pageable pageable);

  @Query(
      value =
          """
          select c
          from InboxConversation c
          where c.companyId = :companyId
            and c.connection.status = :connectionStatus
            and (:connectionId is null or c.connection.id = :connectionId)
            and exists (
              select m.id
              from InboxMessage m
              where m.conversation.id = c.id
                and m.providerFolderKey = :folderKey
            )
          order by c.lastMessageAt desc, c.createdAt desc
          """,
      countQuery =
          """
          select count(c)
          from InboxConversation c
          where c.companyId = :companyId
            and c.connection.status = :connectionStatus
            and (:connectionId is null or c.connection.id = :connectionId)
            and exists (
              select m.id
              from InboxMessage m
              where m.conversation.id = c.id
                and m.providerFolderKey = :folderKey
            )
          """)
  Page<InboxConversation> findByFolder(
      @Param("companyId") Long companyId,
      @Param("connectionId") Long connectionId,
      @Param("connectionStatus") ChannelConnectionStatus connectionStatus,
      @Param("folderKey") String folderKey,
      Pageable pageable);
}
