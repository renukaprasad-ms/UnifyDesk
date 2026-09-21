package com.messaging.admin.service;

import com.messaging.admin.dto.WebhookCallbackResponse;
import com.messaging.admin.enums.PlatformPermission;
import com.messaging.channel.enums.ProviderType;
import com.messaging.observability.webhook.WebhookCallback;
import com.messaging.observability.webhook.WebhookCallbackRepository;
import com.messaging.observability.webhook.WebhookCallbackStatus;
import com.messaging.security.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformChannelAnalyticsService {

  private final WebhookCallbackRepository callbacks;
  private final CurrentUserService currentUser;
  private final PlatformAdminAuthorizationService auth;

  @Transactional(readOnly = true)
  public Page<WebhookCallbackResponse> webhookCallbacks(
      String search, ProviderType provider, WebhookCallbackStatus status, Pageable pageable) {
    auth.requirePermission(currentUser.currentUserId(), PlatformPermission.AUDIT_VIEW);
    String normalizedSearch = search == null ? "" : search.trim();
    return callbacks.search(provider, status, normalizedSearch, pageable).map(this::toResponse);
  }

  private WebhookCallbackResponse toResponse(WebhookCallback callback) {
    return new WebhookCallbackResponse(
        callback.getId().toString(),
        callback.getProviderType().name(),
        callback.getCallbackType(),
        callback.getEndpoint(),
        callback.getHttpMethod(),
        callback.getQueryString(),
        callback.getRemoteAddress(),
        callback.getContentType(),
        callback.getEventId(),
        callback.getRequestHeaders(),
        callback.getPayload(),
        callback.getDecodedPayload(),
        callback.getProcessingResult(),
        callback.getStatus().name(),
        callback.getHttpStatus(),
        callback.getErrorMessage(),
        callback.getReceivedAt(),
        callback.getCompletedAt(),
        callback.getDurationMs());
  }
}
