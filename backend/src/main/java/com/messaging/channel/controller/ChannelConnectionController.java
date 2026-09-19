package com.messaging.channel.controller;

import com.messaging.channel.dto.ChannelConnectionResponse;
import com.messaging.channel.dto.CreateChannelConnectionRequest;
import com.messaging.channel.dto.CreateSenderIdentityRequest;
import com.messaging.channel.dto.SenderIdentityResponse;
import com.messaging.channel.dto.UpdateChannelConnectionRequest;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.service.ChannelConnectionService;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Comparator;
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
      @PathVariable Long companyId, @Valid @RequestBody CreateChannelConnectionRequest request) {
    Long userId = currentUserService.currentUserId();
    companyAuthorizationService.requirePermission(
        userId, companyId, connectPermission(request.channelType()));
    ChannelConnectionResponse response =
        channelConnectionService.create(companyId, userId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(), response, "Channel connection created"));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<ChannelConnectionResponse>>> list(
      @PathVariable Long companyId, @RequestParam(required = false) ChannelType channelType) {
    Long userId = currentUserService.currentUserId();
    companyAuthorizationService.requireActiveMembership(userId, companyId);

    List<ChannelConnectionResponse> connections;
    if (channelType != null) {
      companyAuthorizationService.requirePermission(userId, companyId, readPermission(channelType));
      connections = channelConnectionService.list(companyId, channelType);
    } else {
      connections =
          Arrays.stream(ChannelType.values())
              .filter(
                  type ->
                      companyAuthorizationService.hasPermission(
                          userId, companyId, readPermission(type)))
              .flatMap(type -> channelConnectionService.list(companyId, type).stream())
              .sorted(Comparator.comparing(ChannelConnectionResponse::createdAt).reversed())
              .toList();
    }

    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), connections, "Channel connections fetched"));
  }

  @GetMapping("/{connectionId}")
  public ResponseEntity<ApiResponse<ChannelConnectionResponse>> get(
      @PathVariable Long companyId, @PathVariable Long connectionId) {
    Long userId = currentUserService.currentUserId();
    ChannelConnectionResponse connection = channelConnectionService.get(companyId, connectionId);
    companyAuthorizationService.requirePermission(
        userId, companyId, readPermission(connection.channelType()));
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), connection, "Channel connection fetched"));
  }

  @PatchMapping("/{connectionId}")
  public ResponseEntity<ApiResponse<ChannelConnectionResponse>> update(
      @PathVariable Long companyId,
      @PathVariable Long connectionId,
      @Valid @RequestBody UpdateChannelConnectionRequest request) {
    Long userId = currentUserService.currentUserId();
    ChannelConnectionResponse existing = channelConnectionService.get(companyId, connectionId);
    companyAuthorizationService.requirePermission(
        userId, companyId, modifyPermission(existing.channelType()));
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            channelConnectionService.update(companyId, connectionId, request),
            "Channel connection updated"));
  }

  @PostMapping("/{connectionId}/disconnect")
  public ResponseEntity<ApiResponse<ChannelConnectionResponse>> disconnect(
      @PathVariable Long companyId, @PathVariable Long connectionId) {
    Long userId = currentUserService.currentUserId();
    ChannelConnectionResponse existing = channelConnectionService.get(companyId, connectionId);
    companyAuthorizationService.requirePermission(
        userId, companyId, disconnectPermission(existing.channelType()));
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            channelConnectionService.disconnect(companyId, connectionId, userId),
            "Channel disconnected"));
  }

  @PostMapping("/{connectionId}/senders")
  public ResponseEntity<ApiResponse<SenderIdentityResponse>> addSender(
      @PathVariable Long companyId,
      @PathVariable Long connectionId,
      @Valid @RequestBody CreateSenderIdentityRequest request) {
    Long userId = currentUserService.currentUserId();
    ChannelConnectionResponse existing = channelConnectionService.get(companyId, connectionId);
    companyAuthorizationService.requirePermission(
        userId, companyId, createSenderPermission(existing.channelType()));
    SenderIdentityResponse response =
        channelConnectionService.addSender(companyId, connectionId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response, "Sender identity created"));
  }

  @GetMapping("/{connectionId}/senders")
  public ResponseEntity<ApiResponse<List<SenderIdentityResponse>>> listSenders(
      @PathVariable Long companyId, @PathVariable Long connectionId) {
    Long userId = currentUserService.currentUserId();
    ChannelConnectionResponse existing = channelConnectionService.get(companyId, connectionId);
    companyAuthorizationService.requirePermission(
        userId, companyId, readSenderPermission(existing.channelType()));
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
    Long userId = currentUserService.currentUserId();
    ChannelConnectionResponse existing = channelConnectionService.get(companyId, connectionId);
    companyAuthorizationService.requirePermission(
        userId, companyId, modifySenderPermission(existing.channelType()));
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            channelConnectionService.makeDefaultSender(companyId, connectionId, senderIdentityId),
            "Default sender updated"));
  }

  private String connectPermission(ChannelType type) {
    return switch (type) {
      case EMAIL -> CompanyPermissionKeys.EMAIL_CONNECT;
      case WHATSAPP -> CompanyPermissionKeys.WHATSAPP_CONNECT;
      case SMS -> CompanyPermissionKeys.SMS_CONNECT;
    };
  }

  private String disconnectPermission(ChannelType type) {
    return switch (type) {
      case EMAIL -> CompanyPermissionKeys.EMAIL_DISCONNECT;
      case WHATSAPP -> CompanyPermissionKeys.WHATSAPP_DISCONNECT;
      case SMS -> CompanyPermissionKeys.SMS_DISCONNECT;
    };
  }

  private String readPermission(ChannelType type) {
    return switch (type) {
      case EMAIL -> CompanyPermissionKeys.EMAIL_READ;
      case WHATSAPP -> CompanyPermissionKeys.WHATSAPP_READ;
      case SMS -> CompanyPermissionKeys.SMS_READ;
    };
  }

  private String modifyPermission(ChannelType type) {
    return switch (type) {
      case EMAIL -> CompanyPermissionKeys.EMAIL_MODIFY;
      case WHATSAPP -> CompanyPermissionKeys.WHATSAPP_MODIFY;
      case SMS -> CompanyPermissionKeys.SMS_MODIFY;
    };
  }

  private String createSenderPermission(ChannelType type) {
    return switch (type) {
      case EMAIL -> CompanyPermissionKeys.EMAIL_MODIFY;
      case WHATSAPP -> CompanyPermissionKeys.WHATSAPP_MODIFY;
      case SMS -> CompanyPermissionKeys.SMS_SENDER_CREATE;
    };
  }

  private String readSenderPermission(ChannelType type) {
    return switch (type) {
      case EMAIL -> CompanyPermissionKeys.EMAIL_READ;
      case WHATSAPP -> CompanyPermissionKeys.WHATSAPP_READ;
      case SMS -> CompanyPermissionKeys.SMS_SENDER_READ;
    };
  }

  private String modifySenderPermission(ChannelType type) {
    return switch (type) {
      case EMAIL -> CompanyPermissionKeys.EMAIL_MODIFY;
      case WHATSAPP -> CompanyPermissionKeys.WHATSAPP_MODIFY;
      case SMS -> CompanyPermissionKeys.SMS_SENDER_MODIFY;
    };
  }
}
