package com.messaging.managedemail.events;

import com.messaging.managedemail.metering.ManagedEmailUsageService;
import com.messaging.managedemail.reputation.ManagedEmailReputationService;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sesv2.model.SuppressionListReason;

@Service
@RequiredArgsConstructor
public class ManagedEmailEventLocalProcessor {

  private final ManagedEmailEventLedger ledger;
  private final ManagedEmailDeliveryProjectionService projectionService;
  private final ManagedEmailSuppressionFeedbackService suppressionService;
  private final ManagedEmailUsageService usageService;
  private final ManagedEmailReputationService reputationService;
  private final ManagedEmailProviderEffectRepository providerEffectRepository;

  @Transactional
  public void process(
      ManagedEmailProviderEvent event,
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime) {
    if (!ledger.lockForLocalProcessing(event)) {
      return;
    }

    projectionService.apply(event, runtime);
    switch (event.type()) {
      case SEND ->
          usageService.commitLatest(
              runtime.companyId(),
              runtime.recipientId(),
              runtime.runId(),
              event.providerMessageId());
      case BOUNCE -> {
        if (event.hardBounce()) {
          suppressionService.hardBounce(runtime, event);
          reputationService.evaluate(runtime.companyId());
          providerEffectRepository.ensureTenantSuppressionScheduled(
              event, runtime, SuppressionListReason.BOUNCE);
        }
      }
      case COMPLAINT -> {
        suppressionService.complaint(runtime, event);
        reputationService.evaluate(runtime.companyId());
        providerEffectRepository.ensureTenantSuppressionScheduled(
            event, runtime, SuppressionListReason.COMPLAINT);
      }
      case DELIVERY, REJECT, OPEN, CLICK, DELIVERY_DELAY, RENDERING_FAILURE, SUBSCRIPTION -> {
      }
    }
    ledger.markProcessed(event);
  }
}
