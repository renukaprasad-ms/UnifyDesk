package com.messaging.managedemail.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.messaging.managedemail.metering.ManagedEmailUsageService;
import com.messaging.managedemail.reputation.ManagedEmailReputationService;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sesv2.model.SuppressionListReason;

class ManagedEmailEventLocalProcessorTests {

  @Test
  void duplicateSendDoesNotDoubleBill() {
    Fixture fixture = fixture();
    ManagedEmailProviderEvent event = event(ManagedEmailProviderEventType.SEND, null);
    when(fixture.ledger.lockForLocalProcessing(event)).thenReturn(true, false);

    fixture.processor.process(event, fixture.runtime);
    fixture.processor.process(event, fixture.runtime);

    verify(fixture.usage, times(1)).commitLatest(10L, 70L, 60L, "provider-1");
    verify(fixture.effects, never()).ensureTenantSuppressionScheduled(any(), any(), any());
    verify(fixture.ledger, times(1)).markProcessed(event);
  }

  @Test
  void hardBounceSchedulesExactlyOneDurableProviderEffect() {
    Fixture fixture = fixture();
    ManagedEmailProviderEvent event =
        event(ManagedEmailProviderEventType.BOUNCE, "Permanent");
    when(fixture.ledger.lockForLocalProcessing(event)).thenReturn(true, false);

    fixture.processor.process(event, fixture.runtime);
    fixture.processor.process(event, fixture.runtime);

    verify(fixture.suppression, times(1))
        .hardBounce(fixture.runtime, event);
    verify(fixture.effects, times(1))
        .ensureTenantSuppressionScheduled(event, fixture.runtime, SuppressionListReason.BOUNCE);
    verify(fixture.ledger, times(1)).markProcessed(event);
  }

  @Test
  void complaintSchedulesExactlyOneDurableProviderEffect() {
    Fixture fixture = fixture();
    ManagedEmailProviderEvent event =
        event(ManagedEmailProviderEventType.COMPLAINT, null);
    when(fixture.ledger.lockForLocalProcessing(event)).thenReturn(true, false);

    fixture.processor.process(event, fixture.runtime);
    fixture.processor.process(event, fixture.runtime);

    verify(fixture.suppression, times(1)).complaint(fixture.runtime, event);
    verify(fixture.effects, times(1))
        .ensureTenantSuppressionScheduled(event, fixture.runtime, SuppressionListReason.COMPLAINT);
    verify(fixture.ledger, times(1)).markProcessed(event);
  }

  @Test
  void durableSchedulingFailureDoesNotMarkEventProcessed() {
    Fixture fixture = fixture();
    ManagedEmailProviderEvent event =
        event(ManagedEmailProviderEventType.BOUNCE, "Permanent");
    when(fixture.ledger.lockForLocalProcessing(event)).thenReturn(true);
    doThrow(new IllegalStateException("provider effect table unavailable"))
        .when(fixture.effects)
        .ensureTenantSuppressionScheduled(event, fixture.runtime, SuppressionListReason.BOUNCE);

    assertThatThrownBy(() -> fixture.processor.process(event, fixture.runtime))
        .isInstanceOf(IllegalStateException.class);

    verify(fixture.ledger, never()).markProcessed(event);
  }

  private Fixture fixture() {
    ManagedEmailEventLedger ledger = mock(ManagedEmailEventLedger.class);
    ManagedEmailDeliveryProjectionService projection =
        mock(ManagedEmailDeliveryProjectionService.class);
    ManagedEmailSuppressionFeedbackService suppression =
        mock(ManagedEmailSuppressionFeedbackService.class);
    ManagedEmailUsageService usage = mock(ManagedEmailUsageService.class);
    ManagedEmailReputationService reputation = mock(ManagedEmailReputationService.class);
    ManagedEmailProviderEffectRepository effects = mock(ManagedEmailProviderEffectRepository.class);
    ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime = runtime();
    ManagedEmailEventLocalProcessor processor =
        new ManagedEmailEventLocalProcessor(
            ledger, projection, suppression, usage, reputation, effects);
    return new Fixture(
        processor, ledger, projection, suppression, usage, reputation, effects, runtime);
  }

  private ManagedEmailProviderEvent event(
      ManagedEmailProviderEventType type, String bounceType) {
    return new ManagedEmailProviderEvent(
        "sns-1",
        type,
        Instant.parse("2026-09-26T07:00:00Z"),
        "provider-1",
        10L,
        60L,
        20L,
        40L,
        70L,
        "corr-70",
        bounceType,
        null,
        "hash");
  }

  private ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime() {
    return new ManagedEmailRuntimeRepository.ManagedRecipientRuntime(
        70L,
        10L,
        60L,
        20L,
        30L,
        50L,
        40L,
        "ACCEPTED_BY_PROVIDER",
        "recipient@example.test",
        "provider-1",
        "corr-70",
        "offers@example.test",
        "ACTIVE",
        "ACTIVE",
        "MESSAGING_MANAGED",
        true,
        true,
        "ACTIVE",
        "ENABLED",
        "HEALTHY",
        "ESTABLISHED",
        "ACTIVE",
        "example.test",
        "us-east-1",
        "tenant-10",
        "cfg-10",
        20.0,
        10000.0,
        100.0,
        Instant.now());
  }

  private record Fixture(
      ManagedEmailEventLocalProcessor processor,
      ManagedEmailEventLedger ledger,
      ManagedEmailDeliveryProjectionService projection,
      ManagedEmailSuppressionFeedbackService suppression,
      ManagedEmailUsageService usage,
      ManagedEmailReputationService reputation,
      ManagedEmailProviderEffectRepository effects,
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime) {}
}
