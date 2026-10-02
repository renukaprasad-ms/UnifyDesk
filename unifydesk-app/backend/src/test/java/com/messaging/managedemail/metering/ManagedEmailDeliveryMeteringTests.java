package com.messaging.managedemail.metering;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.service.*;
import com.messaging.broadcast.transport.*;
import com.messaging.contacts.consent.ContactEligibilityService;
import com.messaging.managedemail.delivery.ManagedEmailDeliveryGuard;
import com.messaging.managedemail.delivery.ManagedEmailProviderBudgetService;
import com.messaging.managedemail.delivery.ManagedEmailRateLimiter;
import com.messaging.managedemail.delivery.ManagedEmailSuppressionGate;
import com.messaging.managedemail.events.ManagedEmailPostAcceptanceReconciler;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagedEmailDeliveryMeteringTests {

  @Test
  void unknownOutcomeRetainsReservation() {
    Fixture fixture = fixture();
    doThrow(BroadcastTransportException.unknown("UNKNOWN", "unknown", null))
        .when(fixture.transport)
        .send(any(), any(), any());

    fixture.service.deliver(
        fixture.run, fixture.connection, fixture.sender, fixture.recipient);

    verify(fixture.budget).reserveForSend(fixture.runtime, 70L, 60L, 1);
    verify(fixture.usage, never()).release(anyLong(), anyLong(), anyLong(), anyInt());
    verify(fixture.usage, never())
        .commit(anyLong(), anyLong(), anyLong(), anyInt(), any());
    verify(fixture.state).unknown(any(), eq("UNKNOWN"), eq("unknown"));
  }

  @Test
  void definiteProviderFailureReleasesReservation() {
    Fixture fixture = fixture();
    doThrow(BroadcastTransportException.permanent("REJECTED", "rejected", null))
        .when(fixture.transport)
        .send(any(), any(), any());

    fixture.service.deliver(
        fixture.run, fixture.connection, fixture.sender, fixture.recipient);

    verify(fixture.budget).reserveForSend(fixture.runtime, 70L, 60L, 1);
    verify(fixture.usage).release(10L, 70L, 60L, 1);
    verify(fixture.state).failed(any(), eq("REJECTED"), eq("rejected"));
  }

  @Test
  void providerAcceptanceCommitsReservation() {
    Fixture fixture = fixture();
    when(fixture.transport.send(any(), any(), any()))
        .thenReturn(
            new BroadcastEmailResult(
                "provider-1", null, Instant.parse("2026-09-26T07:00:00Z")));

    fixture.service.deliver(
        fixture.run, fixture.connection, fixture.sender, fixture.recipient);

    verify(fixture.budget).reserveForSend(fixture.runtime, 70L, 60L, 1);
    verify(fixture.usage).commit(10L, 70L, 60L, 1, "provider-1");
    verify(fixture.usage, never()).release(anyLong(), anyLong(), anyLong(), anyInt());
  }

  @Test
  void suppressionAddedAfterSnapshotStopsBeforeRateUsageAndProvider() {
    Fixture fixture = fixture();
    when(fixture.suppressionGate.skipIfSuppressed(
            10L, 70L, "recipient@example.test"))
        .thenReturn(true);

    fixture.service.deliver(
        fixture.run, fixture.connection, fixture.sender, fixture.recipient);

    verifyNoInteractions(fixture.managedRate, fixture.budget, fixture.transport, fixture.usage);
    verify(fixture.state, never()).begin(anyLong(), any());
  }

  private Fixture fixture() {
    BroadcastEmailTransportRegistry emailRegistry = mock(BroadcastEmailTransportRegistry.class);
    BroadcastSmsTransportRegistry smsRegistry = mock(BroadcastSmsTransportRegistry.class);
    BroadcastRateLimiter genericRate = mock(BroadcastRateLimiter.class);
    BroadcastRetryPolicy retryPolicy = mock(BroadcastRetryPolicy.class);
    BroadcastDeliveryStateService state = mock(BroadcastDeliveryStateService.class);
    BroadcastTemplateRenderer renderer = mock(BroadcastTemplateRenderer.class);
    ManagedEmailDeliveryGuard guard = mock(ManagedEmailDeliveryGuard.class);
    ManagedEmailRateLimiter managedRate = mock(ManagedEmailRateLimiter.class);
    ManagedEmailUsageService usage = mock(ManagedEmailUsageService.class);
    ManagedEmailPostAcceptanceReconciler reconciler =
        mock(ManagedEmailPostAcceptanceReconciler.class);
    ManagedEmailSuppressionGate suppressionGate = mock(ManagedEmailSuppressionGate.class);
    ManagedEmailProviderBudgetService budget = mock(ManagedEmailProviderBudgetService.class);
    BroadcastEmailTransport transport = mock(BroadcastEmailTransport.class);
    ContactEligibilityService contactEligibility = mock(ContactEligibilityService.class);
    BroadcastWhatsAppDeliveryService whatsAppDelivery = mock(BroadcastWhatsAppDeliveryService.class);

    BroadcastRun run = new BroadcastRun();
    run.setId(60L);
    run.setCompanyId(10L);
    run.setSenderId(20L);
    run.setProviderType(BroadcastSenderProvider.AMAZON_SES);
    run.setSubjectSnapshot("Subject");
    run.setBodyTextSnapshot("Body");

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(30L);
    connection.setCompanyId(10L);
    connection.setManagementMode(BroadcastSenderManagementMode.MESSAGING_MANAGED);
    connection.setProvider(BroadcastSenderProvider.AMAZON_SES);
    BroadcastSender sender = new BroadcastSender();
    sender.setId(20L);
    sender.setCompanyId(10L);
    sender.setProvider(BroadcastSenderProvider.AMAZON_SES);
    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setId(70L);
    recipient.setCompanyId(10L);
    recipient.setSenderId(20L);
    recipient.setDestination("recipient@example.test");
    recipient.setNormalizedDestination("recipient@example.test");
    recipient.setProviderCorrelationId("corr-70");

    ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime =
        new ManagedEmailRuntimeRepository.ManagedSenderRuntime(
            10L,
            20L,
            30L,
            50L,
            40L,
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

    when(guard.eligible(run, connection, sender, recipient)).thenReturn(Optional.of(runtime));
    when(suppressionGate.skipIfSuppressed(
            10L, 70L, "recipient@example.test"))
        .thenReturn(false);
    when(managedRate.tryAcquire(runtime, 60L)).thenReturn(true);
    when(budget.reserveForSend(runtime, 70L, 60L, 1)).thenReturn(true);
    when(renderer.render(any(), any(), any(), any(), eq(recipient), any()))
        .thenReturn(
            new BroadcastTemplateRenderer.RenderedContent(
                "Subject", "Body", null, List.of(), false, Map.of()));
    BroadcastDeliveryStateService.AttemptContext attempt =
        new BroadcastDeliveryStateService.AttemptContext(70L, 60L, 80L, 1);
    when(state.begin(70L, BroadcastSenderProvider.AMAZON_SES)).thenReturn(attempt);
    when(emailRegistry.require(BroadcastSenderProvider.AMAZON_SES)).thenReturn(transport);

    BroadcastDeliveryService service =
        new BroadcastDeliveryService(
            emailRegistry,
            smsRegistry,
            genericRate,
            retryPolicy,
            state,
            renderer,
            guard,
            managedRate,
            usage,
            reconciler,
            suppressionGate,
            budget,
            contactEligibility,
            whatsAppDelivery);
    return new Fixture(
        service,
        state,
        usage,
        managedRate,
        budget,
        suppressionGate,
        transport,
        runtime,
        run,
        connection,
        sender,
        recipient);
  }

  private record Fixture(
      BroadcastDeliveryService service,
      BroadcastDeliveryStateService state,
      ManagedEmailUsageService usage,
      ManagedEmailRateLimiter managedRate,
      ManagedEmailProviderBudgetService budget,
      ManagedEmailSuppressionGate suppressionGate,
      BroadcastEmailTransport transport,
      ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime,
      BroadcastRun run,
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastRecipient recipient) {}
}
