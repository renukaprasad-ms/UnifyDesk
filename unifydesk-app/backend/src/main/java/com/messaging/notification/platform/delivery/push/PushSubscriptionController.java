package com.messaging.notification.platform.delivery.push;

import com.messaging.common.response.ApiResponse;
import com.messaging.notification.platform.delivery.push.PushSubscriptionService.Capability;
import com.messaging.notification.platform.delivery.push.PushSubscriptionService.RegisterPushSubscription;
import com.messaging.notification.platform.delivery.push.PushSubscriptionService.RegistrationResult;
import com.messaging.notification.platform.delivery.push.PushSubscriptionService.SubscriptionResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications/push")
@RequiredArgsConstructor
public class PushSubscriptionController {

  private final PushSubscriptionService service;

  @GetMapping("/capability")
  public ResponseEntity<ApiResponse<Capability>> capability() {
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), service.capability(), "Push capability fetched"));
  }

  @PostMapping("/subscriptions")
  public ResponseEntity<ApiResponse<SubscriptionResponse>> register(
      @RequestBody RegisterPushSubscription request) {
    RegistrationResult result = service.register(request);
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status)
        .body(
            ApiResponse.success(
                status.value(), result.response(), "Push subscription registered"));
  }

  @GetMapping("/subscriptions")
  public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> list() {
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), service.list(), "Push subscriptions fetched"));
  }

  @DeleteMapping("/subscriptions/{subscriptionId}")
  public ResponseEntity<ApiResponse<SubscriptionResponse>> revoke(
      @PathVariable long subscriptionId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), service.revoke(subscriptionId), "Push subscription revoked"));
  }
}
