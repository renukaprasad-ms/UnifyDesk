package com.messaging.managedemail.events;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.managedemail.metering.ManagedEmailUsageService;
import com.messaging.managedemail.reputation.ManagedEmailReputationService;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sesv2.model.SuppressionListReason;

class ManagedEmailProviderEventProcessorTests {

  @Test
  void crossCompanyTagsAreRecordedButDoNotMutateRecipient() {
    Fixture fixture = fixture();
    ManagedEmailProviderEvent event = event(11L, "provider-1", ManagedEmailProviderEventType.DELIVERY, null);
    when(fixture.parser.parse(any())).thenReturn(event);
    when(fixture.runtime.findRecipientRuntime(70L)).thenReturn(Optional.of(runtime("provider-1")));

    fixture.processor.process(envelope());

    verify(fixture.ledger).recordRejected(event, "OWNERSHIP_MISMATCH");
    verifyNoInteractions(fixture.projection, fixture.suppression, fixture.usage);
  }

  @Test
  void providerMessageMismatchIsRejectedWithoutMutation() {
    Fixture fixture = fixture();
    ManagedEmailProviderEvent event = event(10L, "other-provider", ManagedEmailProviderEventType.BOUNCE, "Permanent");
    when(fixture.parser.parse(any())).thenReturn(event);
    when(fixture.runtime.findRecipientRuntime(70L)).thenReturn(Optional.of(runtime("provider-1")));

    fixture.processor.process(envelope());

    verify(fixture.ledger).recordRejected(event, "PROVIDER_MESSAGE_MISMATCH");
    verifyNoInteractions(fixture.projection, fixture.suppression, fixture.usage);
  }

  @Test
  void duplicateHardBounceOnlyAppliesSuppressionOnce() {
    Fixture fixture = fixture();
    ManagedEmailProviderEvent event =
        event(10L, "provider-1", ManagedEmailProviderEventType.BOUNCE, "Permanent");
    ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime = runtime("provider-1");
    when(fixture.ledger.alreadyReceived("sns-1")).thenReturn(false, true);
    when(fixture.parser.parse(any())).thenReturn(event);
    when(fixture.runtime.findRecipientRuntime(70L)).thenReturn(Optional.of(runtime));
    when(fixture.ledger.recordMatched(event, runtime)).thenReturn(true);

    fixture.processor.process(envelope());
    fixture.processor.process(envelope());

    verify(fixture.suppression, times(1)).hardBounce(runtime, event.eventTimestamp());
    verify(fixture.providerSuppression, times(1)).signal(runtime, SuppressionListReason.BOUNCE);
  }

  @Test
  void duplicateComplaintOnlyAppliesSuppressionOnce() {
    Fixture fixture = fixture();
    ManagedEmailProviderEvent event =
        event(10L, "provider-1", ManagedEmailProviderEventType.COMPLAINT, null);
    ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime = runtime("provider-1");
    when(fixture.ledger.alreadyReceived("sns-1")).thenReturn(false, true);
    when(fixture.parser.parse(any())).thenReturn(event);
    when(fixture.runtime.findRecipientRuntime(70L)).thenReturn(Optional.of(runtime));
    when(fixture.ledger.recordMatched(event, runtime)).thenReturn(true);

    fixture.processor.process(envelope());
    fixture.processor.process(envelope());

    verify(fixture.suppression, times(1)).complaint(runtime);
    verify(fixture.providerSuppression, times(1)).signal(runtime, SuppressionListReason.COMPLAINT);
  }

  private Fixture fixture() {
    ManagedEmailEventLedger ledger = mock(ManagedEmailEventLedger.class);
    ManagedEmailSesEventParser parser = mock(ManagedEmailSesEventParser.class);
    ManagedEmailRuntimeRepository runtime = mock(ManagedEmailRuntimeRepository.class);
    ManagedEmailDeliveryProjectionService projection = mock(ManagedEmailDeliveryProjectionService.class);
    ManagedEmailSuppressionFeedbackService suppression = mock(ManagedEmailSuppressionFeedbackService.class);
    ManagedEmailProviderSuppressionService providerSuppression =
        mock(ManagedEmailProviderSuppressionService.class);
    ManagedEmailUsageService usage = mock(ManagedEmailUsageService.class);
    ManagedEmailReputationService reputation = mock(ManagedEmailReputationService.class);
    SnsMessageVerifier verifier = mock(SnsMessageVerifier.class);
    SnsSubscriptionConfirmer confirmer = mock(SnsSubscriptionConfirmer.class);
    when(ledger.alreadyReceived(anyString())).thenReturn(false);
    ManagedEmailProviderEventProcessor processor =
        new ManagedEmailProviderEventProcessor(
            ledger,
            parser,
            runtime,
            projection,
            suppression,
            providerSuppression,
            usage,
            reputation,
            verifier,
            confirmer);
    return new Fixture(
        processor,
        ledger,
        parser,
        runtime,
        projection,
        suppression,
        providerSuppression,
        usage);
  }

  private ManagedEmailProviderEvent event(
      Long companyId, String providerMessageId, ManagedEmailProviderEventType type, String bounceType) {
    return new ManagedEmailProviderEvent(
        "sns-1",
        type,
        Instant.parse("2026-09-26T07:00:00Z"),
        providerMessageId,
        companyId,
        60L,
        20L,
        40L,
        70L,
        "corr-70",
        bounceType,
        null,
        "hash");
  }

  private ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime(String providerMessageId) {
    return new ManagedEmailRuntimeRepository.ManagedRecipientRuntime(
        70L, 10L, 60L, 20L, 30L, 50L, 40L, "ACCEPTED_BY_PROVIDER", "recipient@example.test",
        providerMessageId, "ACTIVE", "ACTIVE", "MESSAGING_MANAGED", true, "ACTIVE", "ENABLED",
        "HEALTHY", "ESTABLISHED", "ACTIVE", "example.test", "us-east-1", "tenant-10", "cfg-10",
        20.0, 10000.0);
  }

  private SnsEnvelope envelope() {
    return new SnsEnvelope(
        "Notification",
        "sns-1",
        "arn:aws:sns:us-east-1:123456789012:managed-email-events",
        Instant.parse("2026-09-26T07:00:00Z"),
        "{}",
        null);
  }

  private record Fixture(
      ManagedEmailProviderEventProcessor processor,
      ManagedEmailEventLedger ledger,
      ManagedEmailSesEventParser parser,
      ManagedEmailRuntimeRepository runtime,
      ManagedEmailDeliveryProjectionService projection,
      ManagedEmailSuppressionFeedbackService suppression,
      ManagedEmailProviderSuppressionService providerSuppression,
      ManagedEmailUsageService usage) {}
}
