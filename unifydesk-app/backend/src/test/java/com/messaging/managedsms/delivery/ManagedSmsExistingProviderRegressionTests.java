package com.messaging.managedsms.delivery;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.service.ManagedFinancialExposureGuard;
import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.service.*;
import com.messaging.broadcast.transport.*;
import com.messaging.contacts.consent.ContactEligibilityService;
import com.messaging.managedemail.delivery.*;
import com.messaging.managedemail.events.ManagedEmailPostAcceptanceReconciler;
import com.messaging.managedemail.metering.ManagedEmailUsageService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManagedSmsExistingProviderRegressionTests {

  @Test
  void customerManagedMsg91StaysOnExistingGatewayPath() {
    BroadcastEmailTransportRegistry emailRegistry = mock(BroadcastEmailTransportRegistry.class);
    BroadcastSmsTransportRegistry smsRegistry = mock(BroadcastSmsTransportRegistry.class);
    BroadcastRateLimiter genericRate = mock(BroadcastRateLimiter.class);
    BroadcastRetryPolicy retryPolicy = mock(BroadcastRetryPolicy.class);
    BroadcastDeliveryStateService state = mock(BroadcastDeliveryStateService.class);
    BroadcastTemplateRenderer renderer = mock(BroadcastTemplateRenderer.class);
    BroadcastEmailComplianceService emailCompliance =
        mock(BroadcastEmailComplianceService.class);
    ManagedEmailDeliveryGuard managedGuard = mock(ManagedEmailDeliveryGuard.class);
    ManagedEmailRateLimiter managedRate = mock(ManagedEmailRateLimiter.class);
    ManagedEmailUsageService emailUsage = mock(ManagedEmailUsageService.class);
    ManagedEmailPostAcceptanceReconciler reconciler =
        mock(ManagedEmailPostAcceptanceReconciler.class);
    ManagedEmailSuppressionGate suppression = mock(ManagedEmailSuppressionGate.class);
    ManagedEmailProviderBudgetService budget = mock(ManagedEmailProviderBudgetService.class);
    ManagedFinancialExposureGuard exposure = mock(ManagedFinancialExposureGuard.class);
    BroadcastSmsTransport transport = mock(BroadcastSmsTransport.class);
    ContactEligibilityService contactEligibility = mock(ContactEligibilityService.class);
    BroadcastWhatsAppDeliveryService whatsAppDelivery = mock(BroadcastWhatsAppDeliveryService.class);

    BroadcastRun run = new BroadcastRun();
    run.setId(60L);
    run.setCompanyId(10L);
    run.setSenderId(20L);
    run.setProviderType(BroadcastSenderProvider.MSG91_SMS);
    run.setBodyTextSnapshot("Body");

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(30L);
    connection.setCompanyId(10L);
    connection.setManagementMode(BroadcastSenderManagementMode.CUSTOMER_MANAGED);
    connection.setProvider(BroadcastSenderProvider.MSG91_SMS);

    BroadcastSender sender = new BroadcastSender();
    sender.setId(20L);
    sender.setCompanyId(10L);
    sender.setProvider(BroadcastSenderProvider.MSG91_SMS);

    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setId(70L);
    recipient.setCompanyId(10L);
    recipient.setSenderId(20L);
    recipient.setDestination("919999999999");
    recipient.setNormalizedDestination("919999999999");
    recipient.setProviderCorrelationId("corr70");

    when(genericRate.tryAcquire(connection)).thenReturn(true);
    when(renderer.render(any(), any(), any(), any(), eq(recipient), any()))
        .thenReturn(
            new BroadcastTemplateRenderer.RenderedContent(
                null, "Body", null, List.of(), false, Map.of()));
    BroadcastDeliveryStateService.AttemptContext attempt =
        new BroadcastDeliveryStateService.AttemptContext(70L, 60L, 80L, 1);
    when(state.begin(70L, BroadcastSenderProvider.MSG91_SMS)).thenReturn(attempt);
    when(smsRegistry.require(BroadcastSenderProvider.MSG91_SMS)).thenReturn(transport);
    when(transport.send(any(), any(), any()))
        .thenReturn(new BroadcastSmsResult("provider-1", Instant.now()));

    BroadcastDeliveryService service =
        new BroadcastDeliveryService(
            emailRegistry,
            smsRegistry,
            genericRate,
            retryPolicy,
            state,
            renderer,
            emailCompliance,
            managedGuard,
            managedRate,
            emailUsage,
            reconciler,
            suppression,
            budget,
            exposure,
            contactEligibility,
            whatsAppDelivery);

    service.deliver(run, connection, sender, recipient);

    verify(genericRate).tryAcquire(connection);
    verify(transport).send(eq(connection), eq(sender), any(BroadcastSmsCommand.class));
    verify(state).accepted(eq(attempt), eq("provider-1"), isNull(), any());
    verifyNoInteractions(
        managedGuard,
        managedRate,
        emailUsage,
        reconciler,
        suppression,
        budget,
        exposure,
        emailCompliance,
        contactEligibility,
        whatsAppDelivery);
  }
}
