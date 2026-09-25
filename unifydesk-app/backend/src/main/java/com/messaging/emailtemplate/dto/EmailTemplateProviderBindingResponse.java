package com.messaging.emailtemplate.dto;

import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.emailtemplate.entity.EmailTemplateProviderBinding;
import com.messaging.emailtemplate.enums.EmailTemplateProviderBindingStatus;
import java.time.Instant;

public record EmailTemplateProviderBindingResponse(
    String id,
    String templateId,
    String templateVersionId,
    long templateVersion,
    String connectionId,
    BroadcastSenderProvider provider,
    String providerTemplateId,
    String providerTemplateSlug,
    EmailTemplateProviderBindingStatus status,
    boolean sendable,
    String providerStatus,
    String errorCode,
    String errorMessage,
    Instant syncedAt,
    Instant lastCheckedAt,
    Instant updatedAt) {

  public static EmailTemplateProviderBindingResponse from(
      EmailTemplateProviderBinding binding) {
    return new EmailTemplateProviderBindingResponse(
        binding.getId().toString(),
        binding.getTemplateId().toString(),
        binding.getTemplateVersionId().toString(),
        binding.getTemplateVersionNumber(),
        binding.getConnectionId().toString(),
        binding.getProvider(),
        binding.getProviderTemplateId(),
        binding.getProviderTemplateSlug(),
        binding.getStatus(),
        binding.getStatus().sendable(),
        binding.getProviderStatus(),
        binding.getErrorCode(),
        binding.getErrorMessage(),
        binding.getSyncedAt(),
        binding.getLastCheckedAt(),
        binding.getUpdatedAt());
  }
}
