package com.messaging.broadcast.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.messaging.billing.service.ManagedFinancialExposureGuard;
import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.transport.BroadcastEmailTransportRegistry;
import com.messaging.broadcast.transport.BroadcastSmsTransportRegistry;
import com.messaging.contacts.consent.ContactEligibilityService;
import com.messaging.contacts.consent.ConsentTypes.EligibilityDecision;
import com.messaging.contacts.consent.ConsentTypes.EligibilityReason;
import com.messaging.contacts.consent.ConsentTypes.EligibilityResult;
import com.messaging.managedemail.delivery.ManagedEmailDeliveryGuard;
import com.messaging.managedemail.delivery.ManagedEmailProviderBudgetService;
import com.messaging.managedemail.delivery.ManagedEmailRateLimiter;
import com.messaging.managedemail.delivery.ManagedEmailSuppressionGate;
import com.messaging.managedemail.events.ManagedEmailPostAcceptanceReconciler;
import com.messaging.managedemail.metering.ManagedEmailUsageService;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BroadcastDeliveryServiceUnsubscribeTests {

  @Test
  void unsubscribeAfterAudienceFreezeSkipsBeforeAnyTransportCall() {
    BroadcastEmailTransportRegistry emailTransports = mock(BroadcastEmailTransportRegistry.class);
    BroadcastSmsTransportRegistry smsTransports = mock(BroadcastSmsTransportRegistry.class);
    BroadcastRateLimiter rateLimiter = mock(BroadcastRateLimiter.class);
    BroadcastRetryPolicy retryPolicy = mock(BroadcastRetryPolicy.class);
    BroadcastDeliveryStateService stateService = mock(BroadcastDeliveryStateService.class);
    BroadcastTemplateRenderer renderer = mock(BroadcastTemplateRenderer.class);
    BroadcastEmailComplianceService compliance = mock(BroadcastEmailComplianceService.class);
    ManagedEmailDeliveryGuard managedGuard = mock(ManagedEmailDeliveryGuard.class);
    ManagedEmailRateLimiter managedRate = mock(ManagedEmailRateLimiter.class);
    ManagedEmailUsageService usage = mock(ManagedEmailUsageService.class);
    ManagedEmailPostAcceptanceReconciler reconciler =
        mock(ManagedEmailPostAcceptanceReconciler.class);
    ManagedEmailSuppressionGate suppression = mock(ManagedEmailSuppressionGate.class);
    ManagedEmailProviderBudgetService budget = mock(ManagedEmailProviderBudgetService.class);
    ManagedFinancialExposureGuard exposure = mock(ManagedFinancialExposureGuard.class);
    ContactEligibilityService eligibility = mock(ContactEligibilityService.class);
    BroadcastWhatsAppDeliveryService whatsapp = mock(BroadcastWhatsAppDeliveryService.class);

    BroadcastDeliveryService service =
        new BroadcastDeliveryService(
            emailTransports,
            smsTransports,
            rateLimiter,
            retryPolicy,
            stateService,
            renderer,
            compliance,
            managedGuard,
            managedRate,
            usage,
            reconciler,
            suppression,
            budget,
            exposure,
            eligibility,
            whatsapp);

    BroadcastRun run = new BroadcastRun();
    run.setCompanyId(101L);
    run.setProviderType(BroadcastSenderProvider.SENDGRID);
    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setManagementMode(BroadcastSenderManagementMode.CUSTOMER_MANAGED);
    BroadcastSender sender = new BroadcastSender();
    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setId(401L);
    recipient.setContactId(201L);
    recipient.setContactIdentityId(301L);
    recipient.setNormalizedDestination("recipient@example.test");

    when(eligibility.evaluate(eq(101L), eq(201L), eq(301L), any(), any(), any()))
        .thenReturn(
            new EligibilityResult(
                EligibilityDecision.INELIGIBLE,
                EligibilityReason.OPTED_OUT,
                301L,
                "recipient@example.test",
                "v1",
                Instant.now()));

    service.deliver(run, connection, sender, recipient);

    verify(stateService)
        .skipProcessing(
            401L,
            "CONTACT_OPTED_OUT",
            "Contact is no longer eligible for this broadcast");
    verifyNoInteractions(emailTransports, smsTransports, rateLimiter, renderer, compliance);
  }
}
