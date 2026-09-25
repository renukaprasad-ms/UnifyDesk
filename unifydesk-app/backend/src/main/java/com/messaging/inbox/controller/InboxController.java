package com.messaging.inbox.controller;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.channel.email.EmailFolder;
import com.messaging.common.exception.BadRequestException;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/conversations")
  public ResponseEntity<ApiResponse<InboxPageResponse<InboxConversationResponse>>> conversations(
      @PathVariable Long companyId,
      @RequestParam(required = false) Long connectionId,
      @RequestParam(defaultValue = "INBOX") EmailFolder folder,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_READ);

    int resolvedPage = Math.max(page, 0);
    int resolvedSize = Math.min(Math.max(size, 1), 100);

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            inboxQueryService.listConversations(
                companyId, connectionId, folder, resolvedPage, resolvedSize),
            "Inbox conversations fetched"));
  }

  @GetMapping("/conversations/{conversationId}")
  public ResponseEntity<ApiResponse<InboxConversationResponse>> conversation(
      @PathVariable Long companyId, @PathVariable Long conversationId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_READ);

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
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_MODIFY);
    if (request == null || request.read() == null) {
      throw new BadRequestException("Read state is required");
    }

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            readStateService.setMessageReadState(companyId, messageId, request.read()),
            request.read() ? "Message marked as read" : "Message marked as unread"));
  }

  @PutMapping("/conversations/{conversationId}/read-state")
  public ResponseEntity<ApiResponse<InboxReadStateResponse>> conversationReadState(
      @PathVariable Long companyId,
      @PathVariable Long conversationId,
      @RequestBody(required = false) InboxReadStateRequest request) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_MODIFY);
    if (request == null || request.read() == null) {
      throw new BadRequestException("Read state is required");
    }

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            readStateService.setConversationReadState(companyId, conversationId, request.read()),
            request.read() ? "Conversation marked as read" : "Conversation marked as unread"));
  }

  @PostMapping("/conversations/mark-all-read")
  public ResponseEntity<ApiResponse<AsyncJobResponse>> markAllRead(
      @PathVariable Long companyId,
      @RequestParam Long connectionId,
      @RequestParam(defaultValue = "INBOX") EmailFolder folder) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_MODIFY);

    AsyncJobResponse job =
        readStateQueueService.queueMarkAllRead(companyId, userId, connectionId, folder);
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
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_READ);

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
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_READ);

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
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.EMAIL_READ);

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            inboxQueryService.listMessages(companyId, conversationId),
            "Inbox messages fetched"));
  }
}
