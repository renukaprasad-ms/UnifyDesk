package com.messaging.managedsms.delivery;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.service.BroadcastDeliveryStateService;
import com.messaging.broadcast.service.BroadcastRetryPolicy;
import com.messaging.broadcast.transport.BroadcastTransportException;
import com.messaging.managedsms.metering.*;
import com.messaging.managedsms.runtime.*;
import com.messaging.managedsms.safety.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagedSmsDeliveryCoordinatorTests {

  @Test
  void ambiguousProviderOutcomeIsHeldUnknownAndNeverRetried() {
    Fixture f = fixture();
    when(f.gateway.send(any(), anyString(), anyMap(), anyList(), anyString()))
        .thenThrow(
            BroadcastTransportException.unknown(
                "MANAGED_SMS_PROVIDER_UNKNOWN", "unknown", null));

    f.service.deliver(f.run, f.connection, f.sender, f.recipient);

    verify(f.usage).holdUnknown(10L, 70L, 1);
    verify(f.state).unknown(f.attempt, "MANAGED_SMS_PROVIDER_UNKNOWN", "unknown");
    verify(f.state, never()).retry(eq(f.attempt), anyString(), anyString(), any());
  }

  @Test
  void providerAcceptanceCommitsUsageOnceAtAcceptanceBoundary() {
    Fixture f = fixture();
    when(f.gateway.send(any(), anyString(), anyMap(), anyList(), anyString()))
        .thenReturn(new ManagedSmsSendResult("req-1", Instant.now()));

    f.service.deliver(f.run, f.connection, f.sender, f.recipient);

    verify(f.state).accepted(eq(f.attempt), eq("req-1"), isNull(), any());
    verify(f.usage)
        .commit(10L, 60L, 70L, 1, "req-1", "SERVICE", null);
    verify(f.usage, never()).holdUnknown(anyLong(), anyLong(), anyInt());
  }

  @Test
  void definiteRetryableFailureReleasesReservationBeforeRetry() {
    Fixture f = fixture();
    when(f.gateway.send(any(), anyString(), anyMap(), anyList(), anyString()))
        .thenThrow(
            BroadcastTransportException.retryable(
                "MANAGED_SMS_PROVIDER_RATE_LIMIT", "rate limited", null));
    when(f.retry.canRetry(1)).thenReturn(true);
    Instant next = Instant.now().plusSeconds(2);
    when(f.retry.nextAttempt(1)).thenReturn(next);

    f.service.deliver(f.run, f.connection, f.sender, f.recipient);

    verify(f.usage).release(10L, 70L, 1);
    verify(f.state)
        .retry(f.attempt, "MANAGED_SMS_PROVIDER_RATE_LIMIT", "rate limited", next);
  }


  @Test
  void definitePermanentFailureReleasesReservationAndDoesNotRetry() {
    Fixture f = fixture();
    when(f.gateway.send(any(), anyString(), anyMap(), anyList(), anyString()))
        .thenThrow(
            BroadcastTransportException.permanent(
                "MANAGED_SMS_PROVIDER_REJECTED", "rejected", null));

    f.service.deliver(f.run, f.connection, f.sender, f.recipient);

    verify(f.usage).release(10L, 70L, 1);
    verify(f.state).failed(f.attempt, "MANAGED_SMS_PROVIDER_REJECTED", "rejected");
    verify(f.state, never()).retry(eq(f.attempt), anyString(), anyString(), any());
  }

  private Fixture fixture() {
    ManagedSmsDeliveryGuard guard = mock(ManagedSmsDeliveryGuard.class);
    ManagedSmsRateLimiter rate = mock(ManagedSmsRateLimiter.class);
    ManagedSmsUsageService usage = mock(ManagedSmsUsageService.class);
    ManagedSmsSegmentCalculator segments = mock(ManagedSmsSegmentCalculator.class);
    ManagedSmsTemplateService templates = mock(ManagedSmsTemplateService.class);
    ManagedSmsCorrelationService correlations = mock(ManagedSmsCorrelationService.class);
    ManagedMsg91Gateway gateway = mock(ManagedMsg91Gateway.class);
    ManagedSmsProviderSafetyService safety = mock(ManagedSmsProviderSafetyService.class);
    BroadcastDeliveryStateService state = mock(BroadcastDeliveryStateService.class);
    BroadcastRetryPolicy retry = mock(BroadcastRetryPolicy.class);

    ManagedSmsRuntimeRepository.DeliveryRuntime runtime = runtime();
    BroadcastRun run = new BroadcastRun();
    run.setId(60L);
    run.setCompanyId(10L);
    run.setSenderId(20L);
    run.setProviderType(BroadcastSenderProvider.MSG91_SMS);
    run.setBodyTextSnapshot("Code 123");
    run.setTemplateVariablesJsonSnapshot("{\"code\":\"123\"}");

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(30L);
    BroadcastSender sender = new BroadcastSender();
    sender.setId(20L);
    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setId(70L);
    recipient.setDestination("919999999999");

    BroadcastDeliveryStateService.AttemptContext attempt =
        new BroadcastDeliveryStateService.AttemptContext(70L, 60L, 80L, 1);
    when(guard.eligible(run, connection, sender, recipient)).thenReturn(Optional.of(runtime));
    when(rate.tryAcquire(runtime, 60L)).thenReturn(true);
    when(state.begin(70L, BroadcastSenderProvider.MSG91_SMS)).thenReturn(attempt);
    ManagedSmsTemplateService.VariableSpec spec =
        new ManagedSmsTemplateService.VariableSpec("code", "NUMERIC", 10, "VAR1");
    when(templates.render(anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(
            new ManagedSmsTemplateService.RenderedTemplate(
                "Code 123", Map.of("code", "123"), List.of(spec)));
    when(segments.segments("Code 123")).thenReturn(1);
    when(correlations.create(10L, 60L, 70L, 1)).thenReturn("abc123");

    ManagedSmsDeliveryCoordinator service =
        new ManagedSmsDeliveryCoordinator(
            guard,
            rate,
            usage,
            segments,
            templates,
            correlations,
            gateway,
            safety,
            state,
            retry,
            new ObjectMapper());
    return new Fixture(
        service,
        gateway,
        usage,
        state,
        retry,
        run,
        connection,
        sender,
        recipient,
        attempt);
  }

  private ManagedSmsRuntimeRepository.DeliveryRuntime runtime() {
    return new ManagedSmsRuntimeRepository.DeliveryRuntime(
        10L,
        60L,
        20L,
        30L,
        40L,
        Instant.now().minusSeconds(864000),
        "READY",
        true,
        41L,
        "pe",
        "ACTIVE",
        "ACTIVE",
        42L,
        "ACMEIN",
        "SERVICE",
        43L,
        44L,
        "dlt",
        "Code {#var#}",
        "fingerprint",
        "[{\"key\":\"code\",\"type\":\"NUMERIC\",\"maxLength\":10}]",
        "{}",
        45L,
        "client-a",
        "provider-template",
        "provider-flow",
        "rules",
        "ACTIVE",
        "ACTIVE",
        "ACTIVE",
        "ACTIVE",
        true,
        "MESSAGING_MANAGED",
        "ACTIVE");
  }

  private record Fixture(
      ManagedSmsDeliveryCoordinator service,
      ManagedMsg91Gateway gateway,
      ManagedSmsUsageService usage,
      BroadcastDeliveryStateService state,
      BroadcastRetryPolicy retry,
      BroadcastRun run,
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastRecipient recipient,
      BroadcastDeliveryStateService.AttemptContext attempt) {}
}
