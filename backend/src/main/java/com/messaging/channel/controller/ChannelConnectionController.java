package com.messaging.channel.controller;

import com.messaging.channel.dto.ChannelConnectionResponse;
import com.messaging.channel.dto.CreateChannelConnectionRequest;
import com.messaging.channel.dto.CreateSenderIdentityRequest;
import com.messaging.channel.dto.SenderIdentityResponse;
import com.messaging.channel.dto.UpdateChannelConnectionRequest;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.service.ChannelConnectionService;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.enums.CompanyPermission;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/channels")
@RequiredArgsConstructor
public class ChannelConnectionController {

  private final ChannelConnectionService channelConnectionService;
  private final CompanyAuthorizationService companyAuthorizationService;
  private final CurrentUserService currentUserService;

  @PostMapping
  public ResponseEntity<ApiResponse<ChannelConnectionResponse>> create(
      @PathVariable Long companyId,
      @Valid @RequestBody CreateChannelConnectionRequest request) {
    Long userId = currentUserService.currentUserId();
    requireManage(userId, companyId);
    ChannelConnectionResponse response =
        channelConnectionService.create(companyId, userId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(), response, "Channel connection created"));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<ChannelConnectionResponse>>> list(
      @PathVariable Long companyId,
      @RequestParam(required = false) ChannelType channelType) {
    requireView(currentUserService.currentUserId(), companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            channelConnectionService.list(companyId, channelType),
            "Channel connections fetched"));
  }

  @GetMapping("/{connectionId}")
  public ResponseEntity<ApiResponse<ChannelConnectionResponse>> get(
      @PathVariable Long companyId, @PathVariable Long connectionId) {
    requireView(currentUserService.currentUserId(), companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            channelConnectionService.get(companyId, connectionId),
            "Channel connection fetched"));
  }

  @PatchMapping("/{connectionId}")
  public ResponseEntity<ApiResponse<ChannelConnectionResponse>> update(
      @PathVariable Long companyId,
      @PathVariable Long connectionId,
      @Valid @RequestBody UpdateChannelConnectionRequest request) {
    requireManage(currentUserService.currentUserId(), companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            channelConnectionService.update(companyId, connectionId, request),
            "Channel connection updated"));
  }

  @PostMapping("/{connectionId}/disconnect")
  public ResponseEntity<ApiResponse<ChannelConnectionResponse>> disconnect(
      @PathVariable Long companyId, @PathVariable Long connectionId) {
    requireManage(currentUserService.currentUserId(), companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            channelConnectionService.disconnect(companyId, connectionId),
            "Channel disconnected"));
  }

  @PostMapping("/{connectionId}/senders")
  public ResponseEntity<ApiResponse<SenderIdentityResponse>> addSender(
      @PathVariable Long companyId,
      @PathVariable Long connectionId,
      @Valid @RequestBody CreateSenderIdentityRequest request) {
    requireManage(currentUserService.currentUserId(), companyId);
    SenderIdentityResponse response =
        channelConnectionService.addSender(companyId, connectionId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(), response, "Sender identity created"));
  }

  @GetMapping("/{connectionId}/senders")
  public ResponseEntity<ApiResponse<List<SenderIdentityResponse>>> listSenders(
      @PathVariable Long companyId, @PathVariable Long connectionId) {
    requireView(currentUserService.currentUserId(), companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            channelConnectionService.listSenders(companyId, connectionId),
            "Sender identities fetched"));
  }

  @PostMapping("/{connectionId}/senders/{senderIdentityId}/default")
  public ResponseEntity<ApiResponse<SenderIdentityResponse>> makeDefaultSender(
      @PathVariable Long companyId,
      @PathVariable Long connectionId,
      @PathVariable Long senderIdentityId) {
    requireManage(currentUserService.currentUserId(), companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            channelConnectionService.makeDefaultSender(
                companyId, connectionId, senderIdentityId),
            "Default sender updated"));
  }

  private void requireView(Long userId, Long companyId) {
    companyAuthorizationService.requirePermission(
        userId, companyId, CompanyPermission.SETTINGS_VIEW);
  }

  private void requireManage(Long userId, Long companyId) {
    companyAuthorizationService.requirePermission(
        userId, companyId, CompanyPermission.SETTINGS_MANAGE);
  }
}
