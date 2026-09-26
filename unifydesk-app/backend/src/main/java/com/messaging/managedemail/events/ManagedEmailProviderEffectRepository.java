package com.messaging.managedemail.events;

import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.time.Instant;
import java.util.Optional;
import software.amazon.awssdk.services.sesv2.model.SuppressionListReason;

public interface ManagedEmailProviderEffectRepository {

  void ensureTenantSuppressionScheduled(
      ManagedEmailProviderEvent event,
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime,
      SuppressionListReason reason);

  boolean available();

  Optional<ClaimedProviderEffect> claimNext(Instant now, Instant staleBefore);

  void succeeded(Long effectId, int attemptCount, Instant completedAt);

  void retry(Long effectId, int attemptCount, Instant nextAttemptAt, String errorCode);

  void failed(Long effectId, int attemptCount, Instant completedAt, String errorCode);

  record ClaimedProviderEffect(
      Long id,
      Long eventId,
      Long companyId,
      Long tenantId,
      Long recipientId,
      SuppressionListReason reason,
      int attemptCount) {}
}
