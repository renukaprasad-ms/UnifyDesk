package com.messaging.notification.platform.core.api;

import static com.messaging.notification.platform.core.api.NotificationApiDtos.*;

import com.messaging.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationApiService service;

  @GetMapping
  public ResponseEntity<ApiResponse<NotificationPage>> list(
      @RequestParam(required = false) String companyId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "false") boolean unreadOnly) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            service.list(companyId, limit, cursor, unreadOnly),
            "Notifications fetched"));
  }

  @GetMapping("/{notificationId}")
  public ResponseEntity<ApiResponse<NotificationItem>> get(
      @PathVariable String notificationId,
      @RequestParam(required = false) String companyId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            service.get(notificationId, companyId),
            "Notification fetched"));
  }

  @GetMapping("/unread-count")
  public ResponseEntity<ApiResponse<UnreadCount>> unreadCount(
      @RequestParam(required = false) String companyId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            service.unreadCount(companyId),
            "Notification unread count fetched"));
  }

  @PostMapping("/seen")
  public ResponseEntity<ApiResponse<SeenResponse>> seen(@RequestBody SeenRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), service.markSeen(request), "Notifications marked seen"));
  }

  @PatchMapping("/{notificationId}/read-state")
  public ResponseEntity<ApiResponse<ReadStateResponse>> readState(
      @PathVariable String notificationId, @RequestBody ReadStateRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            service.setReadState(notificationId, request),
            "Notification read state updated"));
  }

  @PostMapping("/read-all")
  public ResponseEntity<ApiResponse<ReadAllResponse>> readAll(
      @RequestBody ReadAllRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            service.readAll(request),
            "Notifications marked read"));
  }

  @GetMapping("/preferences")
  public ResponseEntity<ApiResponse<PreferencesResponse>> preferences(
      @RequestParam(required = false) String companyId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            service.preferences(companyId),
            "Notification preferences fetched"));
  }

  @PutMapping("/preferences")
  public ResponseEntity<ApiResponse<PreferencesResponse>> updatePreferences(
      @RequestParam(required = false) String companyId,
      @RequestBody PreferencesRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            service.updatePreferences(companyId, request),
            "Notification preferences updated"));
  }
}
