package com.messaging.inbox.repository;

import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.inbox.entity.InboxConversation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
      "select c.connection.providerType from InboxConversation c "
          + "where c.id = :conversationId and c.companyId = :companyId")
  Optional<ProviderType> findProviderTypeByIdAndCompanyId(
      @Param("conversationId") Long conversationId, @Param("companyId") Long companyId);

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

  @Query(
      value =
          """
          select distinct c
          from InboxConversation c
          where c.companyId = :companyId
            and c.connection.id = :connectionId
            and c.connection.status = :connectionStatus
            and exists (
              select fm.id from InboxMessage fm
              where fm.conversation.id = c.id
                and fm.providerFolderKey = :folderKey
                and fm.status <> :excludedStatus
            )
            and (
              lower(coalesce(c.subject, '')) like :term
              or lower(coalesce(c.lastMessagePreview, '')) like :term
              or exists (
                select p.id from InboxParticipant p
                where p.conversation.id = c.id
                  and (
                    lower(coalesce(p.address, '')) like :term
                    or lower(coalesce(p.displayName, '')) like :term
                  )
              )
              or exists (
                select sm.id from InboxMessage sm
                where sm.conversation.id = c.id
                  and sm.status <> :excludedStatus
                  and (
                    lower(coalesce(sm.subject, '')) like :term
                    or lower(coalesce(sm.snippet, '')) like :term
                    or lower(coalesce(sm.bodyText, '')) like :term
                    or lower(coalesce(sm.fromAddress, '')) like :term
                    or lower(coalesce(sm.toAddresses, '')) like :term
                    or lower(coalesce(sm.ccAddresses, '')) like :term
                    or lower(coalesce(sm.bccAddresses, '')) like :term
                    or exists (
                      select a.id from InboxMessageAttachment a
                      where a.message.id = sm.id
                        and lower(coalesce(a.filename, '')) like :term
                    )
                  )
              )
            )
          order by c.lastMessageAt desc, c.createdAt desc
          """,
      countQuery =
          """
          select count(distinct c.id)
          from InboxConversation c
          where c.companyId = :companyId
            and c.connection.id = :connectionId
            and c.connection.status = :connectionStatus
            and exists (
              select fm.id from InboxMessage fm
              where fm.conversation.id = c.id
                and fm.providerFolderKey = :folderKey
                and fm.status <> :excludedStatus
            )
            and (
              lower(coalesce(c.subject, '')) like :term
              or lower(coalesce(c.lastMessagePreview, '')) like :term
              or exists (
                select p.id from InboxParticipant p
                where p.conversation.id = c.id
                  and (
                    lower(coalesce(p.address, '')) like :term
                    or lower(coalesce(p.displayName, '')) like :term
                  )
              )
              or exists (
                select sm.id from InboxMessage sm
                where sm.conversation.id = c.id
                  and sm.status <> :excludedStatus
                  and (
                    lower(coalesce(sm.subject, '')) like :term
                    or lower(coalesce(sm.snippet, '')) like :term
                    or lower(coalesce(sm.bodyText, '')) like :term
                    or lower(coalesce(sm.fromAddress, '')) like :term
                    or lower(coalesce(sm.toAddresses, '')) like :term
                    or lower(coalesce(sm.ccAddresses, '')) like :term
                    or lower(coalesce(sm.bccAddresses, '')) like :term
                    or exists (
                      select a.id from InboxMessageAttachment a
                      where a.message.id = sm.id
                        and lower(coalesce(a.filename, '')) like :term
                    )
                  )
              )
            )
          """)
  Page<InboxConversation> searchByFolder(
      @Param("companyId") Long companyId,
      @Param("connectionId") Long connectionId,
      @Param("connectionStatus") ChannelConnectionStatus connectionStatus,
      @Param("folderKey") String folderKey,
      @Param("excludedStatus") com.messaging.inbox.enums.InboxMessageStatus excludedStatus,
      @Param("term") String term,
      Pageable pageable);

  @Query(
      value =
          """
          select c
          from InboxConversation c
          where c.companyId = :companyId
            and c.connection.status = :connectionStatus
            and (
              (:emailAllowed = true
                and c.connection.channelType = :emailChannel
                and exists (
                  select em.id from InboxMessage em
                  where em.conversation.id = c.id and em.providerFolderKey = :emailFolderKey
                ))
              or
              (:whatsappAllowed = true
                and c.providerType = :whatsappProvider
                and exists (
                  select wm.id from InboxMessage wm
                  where wm.conversation.id = c.id and wm.providerFolderKey = 'WHATSAPP'
                ))
            )
          order by c.lastMessageAt desc, c.createdAt desc
          """,
      countQuery =
          """
          select count(c)
          from InboxConversation c
          where c.companyId = :companyId
            and c.connection.status = :connectionStatus
            and (
              (:emailAllowed = true
                and c.connection.channelType = :emailChannel
                and exists (
                  select em.id from InboxMessage em
                  where em.conversation.id = c.id and em.providerFolderKey = :emailFolderKey
                ))
              or
              (:whatsappAllowed = true
                and c.providerType = :whatsappProvider
                and exists (
                  select wm.id from InboxMessage wm
                  where wm.conversation.id = c.id and wm.providerFolderKey = 'WHATSAPP'
                ))
            )
          """)
  Page<InboxConversation> findByPermittedChannels(
      @Param("companyId") Long companyId,
      @Param("connectionStatus") ChannelConnectionStatus connectionStatus,
      @Param("emailAllowed") boolean emailAllowed,
      @Param("emailChannel") com.messaging.channel.enums.ChannelType emailChannel,
      @Param("emailFolderKey") String emailFolderKey,
      @Param("whatsappAllowed") boolean whatsappAllowed,
      @Param("whatsappProvider") com.messaging.channel.enums.ProviderType whatsappProvider,
      Pageable pageable);
}
