package com.messaging.inbox.service;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.common.exception.NotFoundException;
import com.messaging.inbox.dto.InboxAttachmentResponse;
import com.messaging.inbox.dto.InboxConversationResponse;
import com.messaging.inbox.dto.InboxMessageResponse;
import com.messaging.inbox.dto.InboxPageResponse;
import com.messaging.inbox.dto.InboxParticipantResponse;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.InboxMessageStatus;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageAttachmentRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.inbox.repository.InboxParticipantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InboxQueryService {

  private final InboxConversationRepository conversationRepository;
  private final InboxParticipantRepository participantRepository;
  private final InboxMessageRepository messageRepository;
  private final InboxMessageAttachmentRepository attachmentRepository;

  @Transactional(readOnly = true)
  public InboxPageResponse<InboxConversationResponse> listConversations(
      Long companyId, Long connectionId, EmailFolder folder, int page, int size) {
    PageRequest pageable = PageRequest.of(page, size);
    EmailFolder resolvedFolder = folder == null ? EmailFolder.INBOX : folder;
    Page<InboxConversation> result =
        conversationRepository.findByFolder(
            companyId,
            connectionId,
            ChannelConnectionStatus.ACTIVE,
            resolvedFolder.providerKey(),
            pageable);

    List<InboxConversationResponse> items =
        result.getContent().stream().map(this::conversation).toList();

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
    return conversation(conversation);
  }

  @Transactional(readOnly = true)
  public List<InboxMessageResponse> listMessages(Long companyId, Long conversationId) {
    InboxConversation conversation = requireVisibleConversation(companyId, conversationId);

    return messageRepository
        .findByConversationIdAndStatusNotOrderBySentAtAscCreatedAtAsc(
            conversation.getId(), InboxMessageStatus.DELETED)
        .stream()
        .map(this::message)
        .toList();
  }

  private InboxConversation requireVisibleConversation(Long companyId, Long conversationId) {
    return conversationRepository
        .findByIdAndCompanyId(conversationId, companyId)
        .filter(
            conversation ->
                conversation.getConnection().getStatus() == ChannelConnectionStatus.ACTIVE)
        .orElseThrow(() -> new NotFoundException("Inbox conversation not found"));
  }

  private InboxConversationResponse conversation(InboxConversation conversation) {
    List<InboxParticipantResponse> participants =
        participantRepository.findByConversationIdOrderByAddressAsc(conversation.getId()).stream()
            .map(
                participant ->
                    new InboxParticipantResponse(
                        participant.getAddress(),
                        participant.getDisplayName(),
                        participant.getParticipantType()))
            .toList();

    return new InboxConversationResponse(
        conversation.getId().toString(),
        conversation.getConnection().getId().toString(),
        conversation.getExternalConversationId(),
        conversation.getProviderType(),
        conversation.getSubject(),
        conversation.getLastMessagePreview(),
        conversation.getLastMessageAt(),
        conversation.getUnreadCount(),
        conversation.getStatus(),
        participants);
  }

  private InboxMessageResponse message(InboxMessage message) {
    List<InboxAttachmentResponse> attachments =
        attachmentRepository.findByMessageIdOrderByFilenameAsc(message.getId()).stream()
            .map(
                attachment ->
                    new InboxAttachmentResponse(
                        attachment.getId().toString(),
                        attachment.getExternalAttachmentId(),
                        attachment.getFilename(),
                        attachment.getMimeType(),
                        attachment.getSizeBytes()))
            .toList();

    return new InboxMessageResponse(
        message.getId().toString(),
        message.getExternalMessageId(),
        message.getInternetMessageId(),
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
        attachments);
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
