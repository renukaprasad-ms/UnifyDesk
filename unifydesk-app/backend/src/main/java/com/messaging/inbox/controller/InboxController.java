package com.messaging.inbox.controller;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.meta.runtime.MetaWhatsAppInboxReadStateService;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.inbox.dto.InboxConversationDetailResponse;
import com.messaging.inbox.dto.InboxConversationResponse;
import com.messaging.inbox.dto.InboxMessageResponse;
import com.messaging.inbox.dto.InboxPageResponse;
import com.messaging.inbox.dto.InboxReadStateRequest;
import com.messaging.inbox.dto.InboxReadStateResponse;
import com.messaging.inbox.dto.InboxRealtimeSnapshotRequest;
import com.messaging.inbox.dto.InboxRealtimeSnapshotResponse;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.inbox.service.InboxQueryService;
import com.messaging.inbox.service.InboxReadStateQueueService;
import com.messaging.inbox.service.InboxReadStateService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/inbox")
@RequiredArgsConstructor
public class InboxController {

  private final InboxQueryService inboxQueryService;
  private final InboxReadStateService readStateService;
  private final InboxReadStateQueueService readStateQueueService;
  private final MetaWhatsAppInboxReadStateService whatsappReadStateService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;
  private final ChannelConnectionRepository connectionRepository;
  private final InboxConversationRepository conversationRepository;
  private final InboxMessageRepository messageRepository;

  @GetMapping("/conversations")
  public ResponseEntity<ApiResponse<InboxPageResponse<InboxConversationResponse>>> conversations(
      @PathVariable Long companyId,
      @RequestParam(required = false) Long connectionId,
      @RequestParam(defaultValue = "INBOX") EmailFolder folder,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size) {
    Long userId = currentUserService.currentUserId();
    boolean emailAllowed;
    boolean whatsappAllowed;
    if (connectionId != null) {
      ChannelConnection connection = requireConnection(companyId, connectionId);
      requireRead(userId, companyId, connection);
      emailAllowed = connection.getChannelType() == ChannelType.EMAIL;
      whatsappAllowed = connection.getProviderType() == ProviderType.META_WHATSAPP;
    } else {
      authorizationService.requireActiveMembership(userId, companyId);
      emailAllowed =
          authorizationService.hasPermission(userId, companyId, CompanyPermissionKeys.EMAIL_READ);
      whatsappAllowed =
          authorizationService.hasPermission(
              userId, companyId, CompanyPermissionKeys.WHATSAPP_READ);
    }

    int resolvedPage = Math.max(page, 0);
    int resolvedSize = Math.min(Math.max(size, 1), 100);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            inboxQueryService.listConversations(
                companyId,
                connectionId,
                folder,
                resolvedPage,
                resolvedSize,
                emailAllowed,
                whatsappAllowed),
            "Inbox conversations fetched"));
  }

  @GetMapping("/conversations/{conversationId}")
  public ResponseEntity<ApiResponse<InboxConversationResponse>> conversation(
      @PathVariable Long companyId, @PathVariable Long conversationId) {
    Long userId = currentUserService.currentUserId();
    requireConversationRead(userId, companyId, conversationId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            inboxQueryService.getConversation(companyId, conversationId),
            "Inbox conversation fetched"));
  }

  @PutMapping("/messages/{messageId}/read-state")
  public ResponseEntity<ApiResponse<InboxReadStateResponse>> messageReadState(
      @PathVariable Long companyId,
      @PathVariable Long messageId,
      @RequestBody(required = false) InboxReadStateRequest request) {
    Long userId = currentUserService.currentUserId();
    if (request == null || request.read() == null) {
      throw new BadRequestException("Read state is required");
    }
    ChannelConnection connection =
        messageRepository
            .findByIdAndCompanyId(messageId, companyId)
            .map(message -> message.getConnection())
            .orElseThrow(() -> new NotFoundException("Inbox message not found"));
    InboxReadStateResponse response;
    if (isWhatsApp(connection)) {
      requirePermissionOrNotFound(
          userId, companyId, CompanyPermissionKeys.WHATSAPP_MODIFY, "Inbox message not found");
      response = whatsappReadStateService.setMessage(companyId, messageId, request.read());
    } else {
      requirePermissionOrNotFound(
          userId, companyId, CompanyPermissionKeys.EMAIL_MODIFY, "Inbox message not found");
      response = readStateService.setMessageReadState(companyId, messageId, request.read());
    }
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            response,
            request.read() ? "Message marked as read" : "Message marked as unread"));
  }

  @PutMapping("/conversations/{conversationId}/read-state")
  public ResponseEntity<ApiResponse<InboxReadStateResponse>> conversationReadState(
      @PathVariable Long companyId,
      @PathVariable Long conversationId,
      @RequestBody(required = false) InboxReadStateRequest request) {
    Long userId = currentUserService.currentUserId();
    if (request == null || request.read() == null) {
      throw new BadRequestException("Read state is required");
    }
    ChannelConnection connection =
        conversationRepository
            .findByIdAndCompanyId(conversationId, companyId)
            .map(conversation -> conversation.getConnection())
            .orElseThrow(() -> new NotFoundException("Inbox conversation not found"));
    InboxReadStateResponse response;
    if (isWhatsApp(connection)) {
      requirePermissionOrNotFound(
          userId, companyId, CompanyPermissionKeys.WHATSAPP_MODIFY, "Inbox conversation not found");
      response =
          whatsappReadStateService.setConversation(companyId, conversationId, request.read());
    } else {
      requirePermissionOrNotFound(
          userId, companyId, CompanyPermissionKeys.EMAIL_MODIFY, "Inbox conversation not found");
      response = readStateService.setConversationReadState(companyId, conversationId, request.read());
    }
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            response,
            request.read() ? "Conversation marked as read" : "Conversation marked as unread"));
  }

  @PostMapping("/conversations/mark-all-read")
  public ResponseEntity<ApiResponse<AsyncJobResponse>> markAllRead(
      @PathVariable Long companyId,
      @RequestParam Long connectionId,
      @RequestParam(defaultValue = "INBOX") EmailFolder folder) {
    Long userId = currentUserService.currentUserId();
    ChannelConnection connection = requireConnection(companyId, connectionId);
    AsyncJobResponse job;
    if (isWhatsApp(connection)) {
      requirePermissionOrNotFound(
          userId, companyId, CompanyPermissionKeys.WHATSAPP_MODIFY, "Inbox connection not found");
      job = whatsappReadStateService.markAllRead(companyId, userId, connectionId);
    } else {
      requirePermissionOrNotFound(
          userId, companyId, CompanyPermissionKeys.EMAIL_MODIFY, "Inbox connection not found");
      job = readStateQueueService.queueMarkAllRead(companyId, userId, connectionId, folder);
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                HttpStatus.ACCEPTED.value(), job, "Mark all as read queued"));
  }

  @PostMapping("/realtime-snapshot")
  public ResponseEntity<ApiResponse<InboxRealtimeSnapshotResponse>> realtimeSnapshot(
      @PathVariable Long companyId,
      @Valid @RequestBody InboxRealtimeSnapshotRequest request) {
    Long userId = currentUserService.currentUserId();
    ChannelConnection connection =
        requireConnection(companyId, parseId(request.connectionId(), "connectionId"));
    requireRead(userId, companyId, connection);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            inboxQueryService.realtimeSnapshot(companyId, request),
            "Inbox realtime snapshot fetched"));
  }

  @GetMapping("/conversations/{conversationId}/detail")
  public ResponseEntity<ApiResponse<InboxConversationDetailResponse>> conversationDetail(
      @PathVariable Long companyId, @PathVariable Long conversationId) {
    Long userId = currentUserService.currentUserId();
    requireConversationRead(userId, companyId, conversationId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            inboxQueryService.getConversationDetail(companyId, conversationId),
            "Inbox conversation detail fetched"));
  }

  @GetMapping("/conversations/{conversationId}/messages")
  public ResponseEntity<ApiResponse<List<InboxMessageResponse>>> messages(
      @PathVariable Long companyId, @PathVariable Long conversationId) {
    Long userId = currentUserService.currentUserId();
    requireConversationRead(userId, companyId, conversationId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            inboxQueryService.listMessages(companyId, conversationId),
            "Inbox messages fetched"));
  }

  private void requireConversationRead(Long userId, Long companyId, Long conversationId) {
    ProviderType provider = inboxQueryService.providerForConversation(companyId, conversationId);
    if (provider == ProviderType.META_WHATSAPP) {
      requirePermissionOrNotFound(
          userId, companyId, CompanyPermissionKeys.WHATSAPP_READ, "Inbox conversation not found");
    } else {
      requirePermissionOrNotFound(
          userId, companyId, CompanyPermissionKeys.EMAIL_READ, "Inbox conversation not found");
    }
  }

  private void requireRead(Long userId, Long companyId, ChannelConnection connection) {
    if (isWhatsApp(connection)) {
      requirePermissionOrNotFound(
          userId, companyId, CompanyPermissionKeys.WHATSAPP_READ, "Inbox connection not found");
    } else if (connection.getChannelType() == ChannelType.EMAIL) {
      requirePermissionOrNotFound(
          userId, companyId, CompanyPermissionKeys.EMAIL_READ, "Inbox connection not found");
    } else {
      throw new NotFoundException("Inbox connection not found");
    }
  }

  private void requirePermissionOrNotFound(
      Long userId, Long companyId, String permission, String notFoundMessage) {
    authorizationService.requireActiveMembership(userId, companyId);
    if (!authorizationService.hasPermission(userId, companyId, permission)) {
      throw new NotFoundException(notFoundMessage);
    }
  }

  private ChannelConnection requireConnection(Long companyId, Long connectionId) {
    return connectionRepository
        .findByIdAndCompanyId(connectionId, companyId)
        .orElseThrow(() -> new NotFoundException("Inbox connection not found"));
  }

  private boolean isWhatsApp(ChannelConnection connection) {
    return connection.getProviderType() == ProviderType.META_WHATSAPP
        && connection.getChannelType() == ChannelType.WHATSAPP;
  }

  private long parseId(String value, String field) {
    try {
      return Long.parseLong(value);
    } catch (Exception exception) {
      throw new BadRequestException("Invalid " + field);
    }
  }
}
