package com.messaging.inbox.service;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.inbox.dto.InboxAttachmentResponse;
import com.messaging.inbox.dto.InboxConversationDetailResponse;
import com.messaging.inbox.dto.InboxConversationResponse;
import com.messaging.inbox.dto.InboxMessageResponse;
import com.messaging.inbox.dto.InboxPageResponse;
import com.messaging.inbox.dto.InboxParticipantResponse;
import com.messaging.inbox.dto.InboxRealtimeSnapshotRequest;
import com.messaging.inbox.dto.InboxRealtimeSnapshotResponse;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.InboxMessageStatus;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageAttachmentRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.inbox.repository.InboxParticipantRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InboxQueryService {

  private final InboxConversationRepository conversationRepository;
  private final InboxParticipantRepository participantRepository;
  private final InboxMessageRepository messageRepository;
  private final InboxMessageAttachmentRepository attachmentRepository;
  private final ChannelConnectionRepository connectionRepository;
  private final JdbcTemplate jdbcTemplate;
  private final InboxParticipantContactLinkService contactLinkService;

  @Transactional(readOnly = true)
  public InboxPageResponse<InboxConversationResponse> listConversations(
      Long companyId, Long connectionId, EmailFolder folder, int page, int size) {
    return listConversations(companyId, connectionId, folder, page, size, true, true);
  }

  @Transactional(readOnly = true)
  public InboxPageResponse<InboxConversationResponse> listConversations(
      Long companyId,
      Long connectionId,
      EmailFolder folder,
      int page,
      int size,
      boolean emailAllowed,
      boolean whatsappAllowed) {
    PageRequest pageable = PageRequest.of(page, size);
    EmailFolder resolvedFolder = folder == null ? EmailFolder.INBOX : folder;
    Page<InboxConversation> result;
    if (connectionId != null) {
      String providerFolder = folderKey(companyId, connectionId, resolvedFolder);
      result =
          conversationRepository.findByFolder(
              companyId,
              connectionId,
              ChannelConnectionStatus.ACTIVE,
              providerFolder,
              pageable);
    } else if (!emailAllowed && !whatsappAllowed) {
      result = Page.empty(pageable);
    } else {
      result =
          conversationRepository.findByPermittedChannels(
              companyId,
              ChannelConnectionStatus.ACTIVE,
              emailAllowed,
              ChannelType.EMAIL,
              resolvedFolder.providerKey(),
              whatsappAllowed,
              ProviderType.META_WHATSAPP,
              pageable);
    }

    List<InboxConversationResponse> items = conversations(result.getContent());
    return new InboxPageResponse<>(
        items,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public InboxPageResponse<InboxConversationResponse> searchConversations(
      Long companyId,
      Long connectionId,
      EmailFolder folder,
      String query,
      int page,
      int size) {
    if (query == null || query.isBlank()) {
      return listConversations(companyId, connectionId, folder, page, size, true, false);
    }
    if (connectionId == null) {
      throw new BadRequestException("connectionId is required for inbox search");
    }

    EmailFolder resolvedFolder = folder == null ? EmailFolder.INBOX : folder;
    String providerFolder = folderKey(companyId, connectionId, resolvedFolder);
    String normalized = query.trim().toLowerCase(java.util.Locale.ROOT);
    if (normalized.length() > 200) {
      throw new BadRequestException("Search query is too long");
    }
    String term = "%" + normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";

    Page<InboxConversation> result =
        conversationRepository.searchByFolder(
            companyId,
            connectionId,
            ChannelConnectionStatus.ACTIVE,
            providerFolder,
            InboxMessageStatus.DELETED,
            term,
            PageRequest.of(page, size));

    List<InboxConversationResponse> items = conversations(result.getContent());
    return new InboxPageResponse<>(
        items,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public InboxConversationResponse getConversation(Long companyId, Long conversationId) {
    InboxConversation conversation = requireVisibleConversation(companyId, conversationId);
    return conversations(List.of(conversation)).getFirst();
  }

  @Transactional(readOnly = true)
  public InboxConversationDetailResponse getConversationDetail(
      Long companyId, Long conversationId) {
    InboxConversation conversation = requireVisibleConversation(companyId, conversationId);
    List<InboxMessage> conversationMessages =
        messageRepository.findByConversationIdAndStatusNotOrderBySentAtAscCreatedAtAsc(
            conversation.getId(), InboxMessageStatus.DELETED);
    return new InboxConversationDetailResponse(
        conversations(List.of(conversation)).getFirst(),
        messages(conversationMessages));
  }

  @Transactional(readOnly = true)
  public List<InboxMessageResponse> listMessages(Long companyId, Long conversationId) {
    InboxConversation conversation = requireVisibleConversation(companyId, conversationId);
    List<InboxMessage> messages =
        messageRepository.findByConversationIdAndStatusNotOrderBySentAtAscCreatedAtAsc(
            conversation.getId(), InboxMessageStatus.DELETED);
    return messages(messages);
  }

  @Transactional(readOnly = true)
  public InboxRealtimeSnapshotResponse realtimeSnapshot(
      Long companyId, InboxRealtimeSnapshotRequest request) {
    Long connectionId = parseId(request.connectionId(), "connectionId");
    EmailFolder folder = request.folder() == null ? EmailFolder.INBOX : request.folder();
    String providerFolderKey = folderKey(companyId, connectionId, folder);
    LinkedHashSet<Long> requestedConversationIds =
        parseIds(request.conversationIds(), "conversationIds");
    LinkedHashSet<Long> requestedMessageIds = parseIds(request.messageIds(), "messageIds");
    Long detailConversationId =
        request.detailConversationId() == null || request.detailConversationId().isBlank()
            ? null
            : parseId(request.detailConversationId(), "detailConversationId");

    List<InboxConversation> foundConversations =
        requestedConversationIds.isEmpty()
            ? List.of()
            : conversationRepository.findVisibleByIdsAndFolder(
                companyId,
                connectionId,
                ChannelConnectionStatus.ACTIVE,
                requestedConversationIds,
                providerFolderKey,
                InboxMessageStatus.DELETED);

    List<InboxMessage> foundMessages =
        requestedMessageIds.isEmpty()
            ? List.of()
            : messageRepository.findVisibleByIds(
                companyId,
                connectionId,
                ChannelConnectionStatus.ACTIVE,
                requestedMessageIds,
                detailConversationId,
                InboxMessageStatus.DELETED);

    Set<Long> foundConversationIds =
        foundConversations.stream()
            .map(InboxConversation::getId)
            .collect(java.util.stream.Collectors.toSet());
    Set<Long> foundMessageIds =
        foundMessages.stream()
            .map(InboxMessage::getId)
            .collect(java.util.stream.Collectors.toSet());

    return new InboxRealtimeSnapshotResponse(
        conversations(foundConversations),
        messages(foundMessages),
        missing(requestedConversationIds, foundConversationIds),
        detailConversationId == null
            ? List.of()
            : missing(requestedMessageIds, foundMessageIds));
  }

  private String folderKey(Long companyId, Long connectionId, EmailFolder folder) {
    var connection =
        connectionRepository
            .findByIdAndCompanyId(connectionId, companyId)
            .orElseThrow(() -> new NotFoundException("Inbox connection not found"));
    if (connection.getStatus() != ChannelConnectionStatus.ACTIVE) {
      throw new NotFoundException("Inbox connection not found");
    }
    if (connection.getProviderType() == ProviderType.META_WHATSAPP) {
      return "WHATSAPP";
    }
    if (connection.getChannelType() != ChannelType.EMAIL) {
      throw new NotFoundException("Inbox connection not found");
    }
    return (folder == null ? EmailFolder.INBOX : folder).providerKey();
  }

  private InboxConversation requireVisibleConversation(Long companyId, Long conversationId) {
    return conversationRepository
        .findByIdAndCompanyId(conversationId, companyId)
        .filter(
            conversation ->
                conversation.getConnection().getStatus() == ChannelConnectionStatus.ACTIVE)
        .orElseThrow(() -> new NotFoundException("Inbox conversation not found"));
  }

  private List<InboxConversationResponse> conversations(List<InboxConversation> conversations) {
    if (conversations.isEmpty()) return List.of();

    List<Long> conversationIds = conversations.stream().map(InboxConversation::getId).toList();
    long companyId = conversations.getFirst().getCompanyId();
    Map<InboxParticipantContactLinkService.LinkKey, InboxParticipantContactLinkService.Link>
        contactLinks = contactLinkService.loadForConversations(companyId, conversationIds);
    Map<Long, List<InboxParticipantResponse>> participants = new HashMap<>();

    participantRepository
        .findByConversationIdInOrderByAddressAsc(conversationIds)
        .forEach(
            participant ->
                participants
                    .computeIfAbsent(participant.getConversation().getId(), ignored -> new ArrayList<>())
                    .add(
                        participantResponse(
                            participant.getConversation().getId(),
                            participant.getAddress(),
                            participant.getDisplayName(),
                            participant.getParticipantType(),
                            contactLinks)));

    return conversations.stream()
        .map(
            conversation ->
                new InboxConversationResponse(
                    conversation.getId().toString(),
                    conversation.getConnection().getId().toString(),
                    conversation.getExternalConversationId(),
                    conversation.getProviderType(),
                    conversation.getSubject(),
                    conversation.getLastMessagePreview(),
                    conversation.getLastMessageAt(),
                    conversation.getUnreadCount(),
                    conversation.getStatus(),
                    List.copyOf(participants.getOrDefault(conversation.getId(), List.of()))))
        .toList();
  }

  private InboxParticipantResponse participantResponse(
      long conversationId,
      String address,
      String displayName,
      com.messaging.inbox.enums.InboxParticipantType participantType,
      Map<InboxParticipantContactLinkService.LinkKey, InboxParticipantContactLinkService.Link>
          contactLinks) {
    InboxParticipantContactLinkService.Link link =
        contactLinkService.find(contactLinks, conversationId, participantType, address);
    return new InboxParticipantResponse(
        address,
        displayName,
        participantType,
        link == null || link.contactId() == null ? null : Long.toString(link.contactId()),
        link == null || link.contactIdentityId() == null
            ? null
            : Long.toString(link.contactIdentityId()),
        link == null ? null : link.resolution().name());
  }

  private List<InboxMessageResponse> messages(List<InboxMessage> messages) {
    if (messages.isEmpty()) return List.of();

    List<Long> messageIds = messages.stream().map(InboxMessage::getId).toList();
    List<Long> whatsappMessageIds =
        messages.stream()
            .filter(
                message ->
                    message.getConnection() != null
                        && message.getConnection().getProviderType() == ProviderType.META_WHATSAPP)
            .map(InboxMessage::getId)
            .toList();
    Map<Long, List<InboxAttachmentResponse>> attachments = new HashMap<>();
    Map<Long, String> whatsappStatuses = whatsappStatuses(whatsappMessageIds);

    attachmentRepository
        .findByMessageIdInOrderByFilenameAsc(messageIds)
        .forEach(
            attachment ->
                attachments
                    .computeIfAbsent(attachment.getMessage().getId(), ignored -> new ArrayList<>())
                    .add(
                        new InboxAttachmentResponse(
                            attachment.getId().toString(),
                            attachment.getExternalAttachmentId(),
                            attachment.getFilename(),
                            attachment.getMimeType(),
                            attachment.getSizeBytes())));

    return messages.stream()
        .map(
            message ->
                new InboxMessageResponse(
                    message.getId().toString(),
                    message.getConversation().getId().toString(),
                    message.getExternalMessageId(),
                    message.getInternetMessageId(),
                    message.getProviderFolderKey(),
                    message.getDirection(),
                    message.getStatus(),
                    message.getSubject(),
                    message.getSnippet(),
                    message.getBodyText(),
                    message.getBodyHtml(),
                    message.getFromAddress(),
                    message.getReplyToAddress(),
                    addresses(message.getToAddresses()),
                    addresses(message.getCcAddresses()),
                    addresses(message.getBccAddresses()),
                    message.getInReplyTo(),
                    message.getReferencesHeader(),
                    message.getSentAt(),
                    message.isRead(),
                    whatsappStatuses.get(message.getId()),
                    List.copyOf(attachments.getOrDefault(message.getId(), List.of()))))
        .toList();
  }

  private Map<Long, String> whatsappStatuses(List<Long> messageIds) {
    if (messageIds.isEmpty()) return Map.of();
    String placeholders =
        String.join(",", java.util.Collections.nCopies(messageIds.size(), "?"));
    List<WhatsAppStatusRow> rows =
        jdbcTemplate.query(
            """
            select inbox_message_id,detailed_status
            from meta_whatsapp_message_runtime
            where direction='OUTBOUND'
              and inbox_message_id in (%s)
            """
                .formatted(placeholders),
            (rs, rowNum) ->
                new WhatsAppStatusRow(
                    rs.getLong("inbox_message_id"), rs.getString("detailed_status")),
            messageIds.toArray());
    Map<Long, String> result = new HashMap<>();
    for (WhatsAppStatusRow row : rows) {
      result.put(row.messageId(), row.status());
    }
    return result;
  }

  private record WhatsAppStatusRow(long messageId, String status) {}

  private LinkedHashSet<Long> parseIds(List<String> values, String field) {
    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    if (values == null) return ids;
    for (String value : values) {
      ids.add(parseId(value, field));
    }
    return ids;
  }

  private Long parseId(String value, String field) {
    try {
      if (value == null || value.isBlank()) throw new NumberFormatException();
      return Long.valueOf(value);
    } catch (NumberFormatException exception) {
      throw new BadRequestException("Invalid " + field);
    }
  }

  private List<String> missing(Set<Long> requested, Set<Long> found) {
    if (requested.isEmpty()) return List.of();
    return requested.stream().filter(id -> !found.contains(id)).map(String::valueOf).toList();
  }

  private List<String> addresses(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(address -> !address.isBlank())
        .toList();
  }
}
