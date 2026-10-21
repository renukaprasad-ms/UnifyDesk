package com.messaging.notification.platform.delivery.worker;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.company.service.CompanyIamPermissionService;
import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.email.NotificationEmailRenderer;
import com.messaging.notification.platform.delivery.email.ProductEmailGateway;
import com.messaging.notification.platform.delivery.metrics.NotificationDeliveryMetrics;
import com.messaging.notification.platform.delivery.model.DeliveryModels.Channel;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryIntent;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryMode;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DigestBucket;
import com.messaging.notification.platform.delivery.model.DeliveryModels.RecipientSnapshot;
import com.messaging.notification.platform.delivery.policy.DeliveryEligibilityService;
import com.messaging.notification.platform.delivery.policy.NotificationDeliveryPolicy;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryRuntimeRepository;
import com.messaging.notification.platform.delivery.repository.NotificationDeliverySupportRepository;
import com.messaging.notification.platform.delivery.repository.ProviderAttemptLifecycleRepository;
import com.messaging.notification.platform.push.BrowserPushReadiness;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationEmailKillSwitchTests {

  private static final Instant NOW = Instant.parse("2026-09-27T08:00:00Z");

  @Test
  void disabledEmailDefersWelcomeWithZeroGatewayCalls() {
    NotificationDeliveryRuntimeRepository repository =
        mock(NotificationDeliveryRuntimeRepository.class);
    NotificationDeliverySupportRepository support =
        mock(NotificationDeliverySupportRepository.class);
    ProviderAttemptLifecycleRepository attempts = mock(ProviderAttemptLifecycleRepository.class);
    ProviderAttemptRecoveryService recovery = mock(ProviderAttemptRecoveryService.class);
    ProductEmailGateway gateway = mock(ProductEmailGateway.class);
    NotificationEmailRenderer renderer = mock(NotificationEmailRenderer.class);
    NotificationDeliveryMetrics metrics = mock(NotificationDeliveryMetrics.class);
    CompanyIamPermissionService permissions = mock(CompanyIamPermissionService.class);
    BrowserPushReadiness readiness = mock(BrowserPushReadiness.class);
    NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
    properties.getEmail().setEnabled(false);
    NotificationDeliveryPolicy policy = new NotificationDeliveryPolicy(properties);
    DeliveryEligibilityService eligibility =
        new DeliveryEligibilityService(repository, policy, permissions, properties, readiness);
    NotificationDeliveryWorker worker =
        new NotificationDeliveryWorker(
            repository,
            support,
            attempts,
            recovery,
            eligibility,
            policy,
            gateway,
            renderer,
            properties,
            metrics,
            new ObjectMapper());

    DeliveryIntent intent = welcomeIntent();
    when(repository.companyBusinessEmail(9L)).thenReturn("company@example.com");
    when(repository.recipientSnapshot(7L, 9L)).thenReturn(Optional.of(activeRecipient()));

    worker.processEmail(intent, NOW);

    verify(repository)
        .deferIntent(
            intent.id(),
            intent.claimToken(),
            "EMAIL_CHANNEL_DISABLED",
            NOW.plus(properties.getEmail().getDisabledRetryDelay()));
    verifyNoInteractions(gateway);
    verify(attempts, never()).prepareAttempt(intent.id(), null, 1, "EMAIL");
  }

  @Test
  void disabledEmailDefersDigestWithZeroGatewayCalls() {
    NotificationDeliveryRuntimeRepository repository =
        mock(NotificationDeliveryRuntimeRepository.class);
    NotificationDeliverySupportRepository support =
        mock(NotificationDeliverySupportRepository.class);
    ProviderAttemptLifecycleRepository attempts = mock(ProviderAttemptLifecycleRepository.class);
    ProviderAttemptRecoveryService recovery = mock(ProviderAttemptRecoveryService.class);
    DeliveryEligibilityService eligibility = mock(DeliveryEligibilityService.class);
    CompanyIamPermissionService permissions = mock(CompanyIamPermissionService.class);
    ProductEmailGateway gateway = mock(ProductEmailGateway.class);
    NotificationEmailRenderer renderer = mock(NotificationEmailRenderer.class);
    NotificationDeliveryMetrics metrics = mock(NotificationDeliveryMetrics.class);
    NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
    properties.getEmail().setEnabled(false);
    NotificationDeliveryPolicy policy = new NotificationDeliveryPolicy(properties);
    NotificationDigestWorker worker =
        new NotificationDigestWorker(
            repository,
            support,
            attempts,
            recovery,
            eligibility,
            policy,
            properties,
            permissions,
            gateway,
            renderer,
            metrics);

    DigestBucket bucket =
        new DigestBucket(
            20L,
            7L,
            9L,
            "INBOX_ACTIVITY",
            "CLAIMED",
            10,
            NOW.minusSeconds(3600),
            NOW.minusSeconds(900),
            NOW,
            NOW,
            "digest-claim",
            NOW.plusSeconds(60),
            null,
            null,
            null);

    worker.sendBucket(bucket, NOW);

    verify(repository)
        .deferDigest(
            20L,
            "digest-claim",
            "EMAIL_CHANNEL_DISABLED",
            NOW.plus(properties.getEmail().getDisabledRetryDelay()));
    verifyNoInteractions(gateway);
  }

  private DeliveryIntent welcomeIntent() {
    return new DeliveryIntent(
        1L,
        2L,
        3L,
        9L,
        7L,
        "COMPANY_CREATED",
        null,
        Channel.EMAIL,
        DeliveryMode.IMMEDIATE,
        "email:welcome:9:7",
        "CLAIMED",
        NOW,
        NOW,
        0,
        "claim",
        NOW.plusSeconds(60),
        NOW);
  }

  private RecipientSnapshot activeRecipient() {
    return new RecipientSnapshot(
        7L, "user@example.com", "User", true, true, 9L, "Workspace", "UTC", true, true);
  }
}
