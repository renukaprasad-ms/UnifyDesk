package com.messaging.managedemail.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagedEmailProviderEventProcessorTests {

  @Test
  void crossCompanyTagsAreRecordedButNeverProcessed() {
    Fixture fixture = fixture();
    ManagedEmailProviderEvent event =
        event(11L, ManagedEmailProviderEventType.DELIVERY, null);
    when(fixture.parser.parse(any())).thenReturn(event);
    when(fixture.runtime.findRecipientRuntime(70L)).thenReturn(Optional.of(runtime()));

    fixture.processor.process(envelope());

    verify(fixture.ledger).recordRejected(event, "OWNERSHIP_MISMATCH");
    verifyNoInteractions(fixture.receipt, fixture.local);
  }

  @Test
  void failureAfterDurableReceiptCanRetrySameSnsMessage() {
    Fixture fixture = fixture();
    ManagedEmailProviderEvent event =
        event(10L, ManagedEmailProviderEventType.DELIVERY, null);
    ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime = runtime();
    when(fixture.parser.parse(any())).thenReturn(event);
    when(fixture.runtime.findRecipientRuntime(70L)).thenReturn(Optional.of(runtime));
    doThrow(new IllegalStateException("projection failed"))
        .doNothing()
        .when(fixture.local)
        .process(event, runtime);

    assertThatThrownBy(() -> fixture.processor.process(envelope()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("projection failed");

    fixture.processor.process(envelope());

    verify(fixture.receipt, times(2)).receiveMatched(event, runtime);
    verify(fixture.local, times(2)).process(event, runtime);
  }

  @Test
  void bounceWebhookDoesNotCallSesDirectly() {
    Fixture fixture = fixture();
    ManagedEmailProviderEvent event =
        event(10L, ManagedEmailProviderEventType.BOUNCE, "Permanent");
    ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime = runtime();
    when(fixture.parser.parse(any())).thenReturn(event);
    when(fixture.runtime.findRecipientRuntime(70L)).thenReturn(Optional.of(runtime));

    fixture.processor.process(envelope());

    verify(fixture.receipt).receiveMatched(event, runtime);
    verify(fixture.local).process(event, runtime);
  }

  private Fixture fixture() {
    ManagedEmailEventLedger ledger = mock(ManagedEmailEventLedger.class);
    ManagedEmailSesEventParser parser = mock(ManagedEmailSesEventParser.class);
    ManagedEmailRuntimeRepository runtime = mock(ManagedEmailRuntimeRepository.class);
    ManagedEmailEventReceiptService receipt = mock(ManagedEmailEventReceiptService.class);
    ManagedEmailEventLocalProcessor local = mock(ManagedEmailEventLocalProcessor.class);
    SnsMessageVerifier verifier = mock(SnsMessageVerifier.class);
    SnsSubscriptionConfirmer confirmer = mock(SnsSubscriptionConfirmer.class);

    ManagedEmailProviderEventProcessor processor =
        new ManagedEmailProviderEventProcessor(
            ledger, parser, runtime, receipt, local, verifier, confirmer);
    return new Fixture(processor, ledger, parser, runtime, receipt, local);
  }

  private ManagedEmailProviderEvent event(
      Long companyId, ManagedEmailProviderEventType type, String bounceType) {
    return new ManagedEmailProviderEvent(
        "sns-1",
        type,
        Instant.parse("2026-09-26T07:00:00Z"),
        "provider-1",
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
      ManagedEmailEventReceiptService receipt,
      ManagedEmailEventLocalProcessor local) {}
}
