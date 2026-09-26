package com.messaging.managedemail.events;

import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.model.SuppressionListReason;

@Service
@RequiredArgsConstructor
public class ManagedEmailProviderEventProcessor {

  private final ManagedEmailEventLedger ledger;
  private final ManagedEmailSesEventParser parser;
  private final ManagedEmailRuntimeRepository runtimeRepository;
  private final ManagedEmailEventReceiptService receiptService;
  private final ManagedEmailEventLocalProcessor localProcessor;
  private final ManagedEmailProviderSuppressionService providerSuppressionService;
  private final SnsMessageVerifier snsMessageVerifier;
  private final SnsSubscriptionConfirmer subscriptionConfirmer;

  public void process(SnsEnvelope envelope) {
    if ("SubscriptionConfirmation".equals(envelope.type())) {
      if (envelope.subscribeUrl() != null) {
        URI uri =
            snsMessageVerifier.validateAwsHttpsUri(
                envelope.subscribeUrl(), envelope.topicArn(), false);
        subscriptionConfirmer.confirm(uri);
      }
      ledger.recordSubscription(envelope);
      return;
    }
    if (!"Notification".equals(envelope.type())) return;

    ManagedEmailProviderEvent event = parser.parse(envelope);
    ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime =
        event.recipientId() == null
            ? null
            : runtimeRepository.findRecipientRuntime(event.recipientId()).orElse(null);
    String mismatch = mismatch(event, runtime);
    if (mismatch != null) {
      ledger.recordRejected(event, mismatch);
      return;
    }

    receiptService.receiveMatched(event, runtime);
    localProcessor.process(event, runtime);

    if (event.hardBounce()) {
      providerSuppressionService.signal(runtime, SuppressionListReason.BOUNCE);
    } else if (event.type() == ManagedEmailProviderEventType.COMPLAINT) {
      providerSuppressionService.signal(runtime, SuppressionListReason.COMPLAINT);
    }
  }

  private String mismatch(
      ManagedEmailProviderEvent event,
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime) {
    if (event.companyId() == null
        || event.runId() == null
        || event.senderId() == null
        || event.domainId() == null
        || event.recipientId() == null) {
      return "MISSING_CORRELATION";
    }
    if (runtime == null) return "RECIPIENT_NOT_FOUND";
    if (!"MESSAGING_MANAGED".equals(runtime.managementMode())) return "NOT_MANAGED";
    if (!event.companyId().equals(runtime.companyId())
        || !event.runId().equals(runtime.runId())
        || !event.senderId().equals(runtime.senderId())
        || !event.domainId().equals(runtime.domainId())) {
      return "OWNERSHIP_MISMATCH";
    }
    if (runtime.providerMessageId() != null
        && !runtime.providerMessageId().isBlank()
        && !runtime.providerMessageId().equals(event.providerMessageId())) {
      return "PROVIDER_MESSAGE_MISMATCH";
    }
    if (event.correlation() == null
        || runtime.providerCorrelationId() == null
        || !runtime.providerCorrelationId().equals(event.correlation())) {
      return "RECIPIENT_CORRELATION_MISMATCH";
    }
    return null;
  }
}
