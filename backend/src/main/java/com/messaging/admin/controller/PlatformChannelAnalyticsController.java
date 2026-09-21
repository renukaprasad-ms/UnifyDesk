package com.messaging.admin.controller;

import com.messaging.admin.dto.WebhookCallbackResponse;
import com.messaging.admin.service.PlatformChannelAnalyticsService;
import com.messaging.channel.enums.ProviderType;
import com.messaging.common.response.ApiResponse;
import com.messaging.observability.webhook.WebhookCallbackStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/channel-analytics")
@RequiredArgsConstructor
public class PlatformChannelAnalyticsController {

  private final PlatformChannelAnalyticsService service;

  @GetMapping("/webhook-callbacks")
  public ResponseEntity<ApiResponse<Page<WebhookCallbackResponse>>> webhookCallbacks(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) ProviderType provider,
      @RequestParam(required = false) WebhookCallbackStatus status,
      @PageableDefault(
              size = 20,
              sort = "receivedAt",
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.webhookCallbacks(search, provider, status, pageable),
            "Webhook callbacks fetched"));
  }
}
