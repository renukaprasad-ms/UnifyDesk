package com.messaging.inbox.controller;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.inbox.dto.InboxConversationResponse;
import com.messaging.inbox.dto.InboxMessageResponse;
import com.messaging.inbox.dto.InboxPageResponse;
import com.messaging.inbox.service.InboxQueryService;
import com.messaging.security.service.CurrentUserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/inbox")
@RequiredArgsConstructor
public class InboxController {

  private final InboxQueryService inboxQueryService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/conversations")
  public ResponseEntity<ApiResponse<InboxPageResponse<InboxConversationResponse>>> conversations(
      @PathVariable Long companyId,
      @RequestParam(required = false) Long connectionId,
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
                companyId, connectionId, resolvedPage, resolvedSize),
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
