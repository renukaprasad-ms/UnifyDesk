package com.messaging.broadcast.controller;

import com.messaging.broadcast.dto.*;
import com.messaging.broadcast.service.*;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/broadcast")
@RequiredArgsConstructor
public class BroadcastController {

  private final BroadcastGroupService groupService;
  private final BroadcastMemberService memberService;
  private final BroadcastMessageService messageService;
  private final BroadcastExecutionControlService controlService;
  private final BroadcastAudiencePreviewService audiencePreviewService;
  private final BroadcastChannelReadinessService channelReadinessService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/groups")
  public ResponseEntity<ApiResponse<Page<BroadcastGroupResponse>>> groups(
      @PathVariable Long companyId,
      @RequestParam(defaultValue = "EMAIL") ChannelType channelType,
      @PageableDefault(size = 30) Pageable pageable) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, groupService.list(companyId, channelType, pageable), "Broadcast groups fetched"));
  }

  @PostMapping("/groups")
  public ResponseEntity<ApiResponse<BroadcastGroupResponse>> createGroup(
      @PathVariable Long companyId, @Valid @RequestBody CreateBroadcastGroupRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201, groupService.create(companyId, userId, request), "Broadcast group created"));
  }

  @PatchMapping("/groups/{groupId}")
  public ResponseEntity<ApiResponse<BroadcastGroupResponse>> updateGroup(
      @PathVariable Long companyId,
      @PathVariable Long groupId,
      @Valid @RequestBody UpdateBroadcastGroupRequest request) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, groupService.update(companyId, groupId, request), "Broadcast group updated"));
  }

  @GetMapping("/groups/{groupId}")
  public ResponseEntity<ApiResponse<BroadcastGroupResponse>> group(
      @PathVariable Long companyId, @PathVariable Long groupId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, groupService.get(companyId, groupId), "Broadcast group fetched"));
  }

  @GetMapping("/groups/{groupId}/members")
  public ResponseEntity<ApiResponse<Page<BroadcastMemberResponse>>> members(
      @PathVariable Long companyId,
      @PathVariable Long groupId,
      @PageableDefault(size = 50) Pageable pageable) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, groupService.members(companyId, groupId, pageable), "Broadcast members fetched"));
  }

  @PostMapping("/groups/{groupId}/members")
  public ResponseEntity<ApiResponse<BroadcastMemberImportResponse>> addMembers(
      @PathVariable Long companyId,
      @PathVariable Long groupId,
      @Valid @RequestBody BroadcastMemberBatchRequest request) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, memberService.add(companyId, groupId, request), "Broadcast members imported"));
  }

  @PostMapping(
      path = "/groups/{groupId}/members/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<BroadcastMemberImportResponse>> importMembers(
      @PathVariable Long companyId,
      @PathVariable Long groupId,
      @RequestPart("file") MultipartFile file) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, memberService.importCsv(companyId, groupId, file), "Broadcast CSV imported"));
  }

  @DeleteMapping("/groups/{groupId}/members/{memberId}")
  public ResponseEntity<ApiResponse<Void>> removeMember(
      @PathVariable Long companyId,
      @PathVariable Long groupId,
      @PathVariable Long memberId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    memberService.remove(companyId, groupId, memberId);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Broadcast member removed"));
  }

  @GetMapping("/groups/{groupId}/messages")
  public ResponseEntity<ApiResponse<Page<BroadcastMessageSummaryResponse>>> messages(
      @PathVariable Long companyId,
      @PathVariable Long groupId,
      @PageableDefault(size = 30) Pageable pageable) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, messageService.list(companyId, groupId, pageable), "Broadcast history fetched"));
  }

  @PostMapping("/groups/{groupId}/messages")
  public ResponseEntity<ApiResponse<BroadcastMessageResponse>> createMessage(
      @PathVariable Long companyId,
      @PathVariable Long groupId,
      @Valid @RequestBody CreateBroadcastMessageRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                messageService.create(companyId, userId, groupId, request),
                "Broadcast draft created"));
  }

  @PostMapping("/audiences/preview")
  public ResponseEntity<ApiResponse<BroadcastAudiencePreviewResponse>> previewAudience(
      @PathVariable Long companyId,
      @Valid @RequestBody BroadcastAudiencePreviewRequest request) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            audiencePreviewService.preview(companyId, request),
            "Broadcast audience preview computed"));
  }

  @GetMapping("/readiness")
  public ResponseEntity<ApiResponse<java.util.List<BroadcastChannelReadinessResponse.ChannelReadiness>>>
      readiness(@PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            channelReadinessService.readiness(companyId),
            "Broadcast channel readiness fetched"));
  }

  @PostMapping("/audiences/messages")
  public ResponseEntity<ApiResponse<BroadcastMessageResponse>> createAudienceMessage(
      @PathVariable Long companyId,
      @Valid @RequestBody CreateAudienceBroadcastMessageRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                messageService.createAudience(companyId, userId, request),
                "Audience Broadcast draft created"));
  }

  @GetMapping("/messages")
  public ResponseEntity<ApiResponse<Page<BroadcastMessageSummaryResponse>>> messagesByChannel(
      @PathVariable Long companyId,
      @RequestParam(defaultValue = "EMAIL") ChannelType channelType,
      @PageableDefault(size = 30) Pageable pageable) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            messageService.listByChannel(companyId, channelType, pageable),
            "Broadcast history fetched"));
  }

  @PutMapping("/messages/{broadcastId}")
  public ResponseEntity<ApiResponse<BroadcastMessageResponse>> updateMessage(
      @PathVariable Long companyId,
      @PathVariable Long broadcastId,
      @Valid @RequestBody UpdateBroadcastMessageRequest request) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, messageService.update(companyId, broadcastId, request), "Broadcast updated"));
  }

  @GetMapping("/messages/{broadcastId}")
  public ResponseEntity<ApiResponse<BroadcastMessageResponse>> message(
      @PathVariable Long companyId, @PathVariable Long broadcastId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, messageService.get(companyId, broadcastId), "Broadcast fetched"));
  }


  @GetMapping("/messages/{broadcastId}/recipients")
  public ResponseEntity<ApiResponse<Page<BroadcastRecipientResponse>>> recipients(
      @PathVariable Long companyId,
      @PathVariable Long broadcastId,
      @PageableDefault(size = 50) Pageable pageable) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            messageService.recipients(companyId, broadcastId, pageable),
            "Broadcast recipients fetched"));
  }

  @PostMapping("/messages/{broadcastId}/send")
  public ResponseEntity<ApiResponse<BroadcastMessageResponse>> send(
      @PathVariable Long companyId,
      @PathVariable Long broadcastId,
      @RequestBody(required = false) SendBroadcastRequest request) {
    require(companyId, CompanyPermissionKeys.BROADCAST_SEND);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                202,
                messageService.sendNow(
                    companyId,
                    broadcastId,
                    request == null ? null : request.expectedSourceDescriptor()),
                "Broadcast queued"));
  }

  @PostMapping("/messages/{broadcastId}/schedule")
  public ResponseEntity<ApiResponse<BroadcastMessageResponse>> schedule(
      @PathVariable Long companyId,
      @PathVariable Long broadcastId,
      @Valid @RequestBody ScheduleBroadcastRequest request) {
    require(companyId, CompanyPermissionKeys.BROADCAST_SEND);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, messageService.schedule(companyId, broadcastId, request), "Broadcast scheduled"));
  }

  @PostMapping("/messages/{broadcastId}/pause")
  public ResponseEntity<ApiResponse<BroadcastMessageResponse>> pause(
      @PathVariable Long companyId, @PathVariable Long broadcastId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, controlService.pause(companyId, broadcastId), "Broadcast paused"));
  }

  @PostMapping("/messages/{broadcastId}/resume")
  public ResponseEntity<ApiResponse<BroadcastMessageResponse>> resume(
      @PathVariable Long companyId, @PathVariable Long broadcastId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, controlService.resume(companyId, broadcastId), "Broadcast resumed"));
  }

  @PostMapping("/messages/{broadcastId}/cancel")
  public ResponseEntity<ApiResponse<BroadcastMessageResponse>> cancel(
      @PathVariable Long companyId, @PathVariable Long broadcastId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, controlService.cancel(companyId, broadcastId), "Broadcast cancelled"));
  }

  private Long require(Long companyId, String permission) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }
}
