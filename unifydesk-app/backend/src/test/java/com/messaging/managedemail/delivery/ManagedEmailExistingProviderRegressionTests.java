package com.messaging.managedemail.delivery;

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
import com.messaging.managedemail.events.ManagedEmailPostAcceptanceReconciler;
import com.messaging.managedemail.metering.ManagedEmailUsageService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ManagedEmailExistingProviderRegressionTests {

  @ParameterizedTest
  @EnumSource(
      value = BroadcastSenderProvider.class,
      names = {"AMAZON_SES", "SENDGRID", "AZURE_EMAIL", "MSG91_EMAIL"})
  void customerManagedEmailProvidersStayOnExistingDeliveryPath(
      BroadcastSenderProvider provider) {
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
    run.setProviderType(provider);
    run.setSubjectSnapshot("Subject");
    run.setBodyTextSnapshot("Body");

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(30L);
    connection.setCompanyId(10L);
    connection.setManagementMode(BroadcastSenderManagementMode.CUSTOMER_MANAGED);
    connection.setProvider(provider);

    BroadcastSender sender = new BroadcastSender();
    sender.setId(20L);
    sender.setCompanyId(10L);
    sender.setProvider(provider);

    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setId(70L);
    recipient.setCompanyId(10L);
    recipient.setSenderId(20L);
    recipient.setDestination("recipient@example.test");
    recipient.setNormalizedDestination("recipient@example.test");
    recipient.setProviderCorrelationId("corr-70");

    when(genericRate.tryAcquire(connection)).thenReturn(true);
    when(renderer.render(any(), any(), any(), any(), eq(recipient), any()))
        .thenReturn(
            new BroadcastTemplateRenderer.RenderedContent(
                "Subject", "Body", null, List.of(), false, Map.of()));
    when(emailCompliance.apply(any(), eq(recipient)))
        .thenReturn(
            new BroadcastEmailComplianceService.CompliantContent(
                "Subject",
                "Body",
                null,
                Map.of(),
                new BroadcastEmailComplianceMetadata(
                    "https://app.unifydesk.in/unsubscribe?token=test",
                    "https://api.unifydesk.in/api/v1/public/recipient-actions/email/unsubscribe/test")));
    BroadcastDeliveryStateService.AttemptContext attempt =
        new BroadcastDeliveryStateService.AttemptContext(70L, 60L, 80L, 1);
    when(state.begin(70L, provider)).thenReturn(attempt);
    when(emailRegistry.require(provider)).thenReturn(transport);
    when(transport.send(any(), any(), any()))
        .thenReturn(
            new BroadcastEmailResult(
                "provider-message", null, Instant.parse("2026-09-26T10:00:00Z")));

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
            usage,
            reconciler,
            suppressionGate,
            budget,
            contactEligibility,
            whatsAppDelivery);

    service.deliver(run, connection, sender, recipient);

    verify(genericRate).tryAcquire(connection);
    verify(transport).send(eq(connection), eq(sender), any(BroadcastEmailCommand.class));
    verify(state).accepted(eq(attempt), any(BroadcastEmailResult.class));
    verifyNoInteractions(
        managedGuard,
        managedRate,
        usage,
        reconciler,
        suppressionGate,
        budget,
        contactEligibility,
        whatsAppDelivery);
  }
}
