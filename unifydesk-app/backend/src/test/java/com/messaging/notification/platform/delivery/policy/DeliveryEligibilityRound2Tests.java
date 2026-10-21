package com.messaging.notification.platform.delivery.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.messaging.company.service.CompanyIamPermissionService;
import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.model.DeliveryModels.Channel;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryIntent;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryMode;
import com.messaging.notification.platform.delivery.model.DeliveryModels.EffectivePreference;
import com.messaging.notification.platform.delivery.model.DeliveryModels.RecipientSnapshot;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryRuntimeRepository;
import com.messaging.notification.platform.push.BrowserPushReadiness;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeliveryEligibilityRound2Tests {

  private static final Instant NOW = Instant.parse("2026-09-27T08:00:00Z");

  @Test
  void welcomeUsesCompanyEmailEvenWhenCreatorEmailIsUnverified() {
    NotificationDeliveryRuntimeRepository repository =
        mock(NotificationDeliveryRuntimeRepository.class);
    NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
    properties.getEmail().setEnabled(true);
    DeliveryEligibilityService service =
        new DeliveryEligibilityService(
            repository,
            new NotificationDeliveryPolicy(properties),
            mock(CompanyIamPermissionService.class),
            properties,
            mock(BrowserPushReadiness.class));
    when(repository.recipientSnapshot(7L, 9L))
        .thenReturn(
            Optional.of(
                new RecipientSnapshot(
                    7L,
                    "creator@example.com",
                    "Creator",
                    true,
                    false,
                    9L,
                    "Acme",
                    "UTC",
                    true,
                    true)));
    when(repository.companyBusinessEmail(9L)).thenReturn("business@example.com");

    var decision = service.evaluate(welcomeIntent(), NOW);

    assertThat(decision.action()).isEqualTo(DeliveryEligibilityService.Decision.Action.SEND);
    assertThat(decision.recipient().email()).isEqualTo("business@example.com");
    assertThat(decision.recipient().userId()).isEqualTo(7L);
  }

  @Test
  void welcomeWithoutCompanyEmailDoesNotFallBackToCreatorEmail() {
    NotificationDeliveryRuntimeRepository repository =
        mock(NotificationDeliveryRuntimeRepository.class);
    NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
    properties.getEmail().setEnabled(true);
    DeliveryEligibilityService service =
        new DeliveryEligibilityService(
            repository,
            new NotificationDeliveryPolicy(properties),
            mock(CompanyIamPermissionService.class),
            properties,
            mock(BrowserPushReadiness.class));
    when(repository.recipientSnapshot(7L, 9L))
        .thenReturn(Optional.of(activeRecipient(7L, 9L, true, true)));

    var decision = service.evaluate(welcomeIntent(), NOW);

    assertThat(decision.action()).isEqualTo(DeliveryEligibilityService.Decision.Action.SUPPRESS);
    assertThat(decision.code()).isEqualTo("COMPANY_EMAIL_MISSING_OR_INVALID");
  }

  @Test
  void emailKillSwitchDefersWelcomeInsteadOfCallingChannel() {
    NotificationDeliveryRuntimeRepository repository =
        mock(NotificationDeliveryRuntimeRepository.class);
    CompanyIamPermissionService permissions = mock(CompanyIamPermissionService.class);
    BrowserPushReadiness readiness = mock(BrowserPushReadiness.class);
    NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
    properties.getEmail().setEnabled(false);
    DeliveryEligibilityService service =
        new DeliveryEligibilityService(
            repository,
            new NotificationDeliveryPolicy(properties),
            permissions,
            properties,
            readiness);

    DeliveryIntent intent = welcomeIntent();
    when(repository.companyBusinessEmail(9L)).thenReturn("company@example.com");
    when(repository.recipientSnapshot(7L, 9L))
        .thenReturn(Optional.of(activeRecipient(7L, 9L, true, true)));

    var decision = service.evaluate(intent, NOW);

    assertThat(decision.action()).isEqualTo(DeliveryEligibilityService.Decision.Action.DEFER);
    assertThat(decision.code()).isEqualTo("EMAIL_CHANNEL_DISABLED");
    assertThat(decision.deferUntil()).isAfter(NOW);
  }

  @Test
  void reenabledEmailMakesDeferredWelcomeSendableAgain() {
    NotificationDeliveryRuntimeRepository repository =
        mock(NotificationDeliveryRuntimeRepository.class);
    CompanyIamPermissionService permissions = mock(CompanyIamPermissionService.class);
    BrowserPushReadiness readiness = mock(BrowserPushReadiness.class);
    NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
    properties.getEmail().setEnabled(false);
    DeliveryEligibilityService service =
        new DeliveryEligibilityService(
            repository,
            new NotificationDeliveryPolicy(properties),
            permissions,
            properties,
            readiness);

    when(repository.companyBusinessEmail(9L)).thenReturn("company@example.com");
    when(repository.recipientSnapshot(7L, 9L))
        .thenReturn(Optional.of(activeRecipient(7L, 9L, true, true)));

    assertThat(service.evaluate(welcomeIntent(), NOW).action())
        .isEqualTo(DeliveryEligibilityService.Decision.Action.DEFER);

    properties.getEmail().setEnabled(true);

    assertThat(service.evaluate(welcomeIntent(), NOW.plusSeconds(300)).action())
        .isEqualTo(DeliveryEligibilityService.Decision.Action.SEND);
  }

  @Test
  void missingCompanyScopeFailsClosed() {
    NotificationDeliveryRuntimeRepository repository =
        mock(NotificationDeliveryRuntimeRepository.class);
    CompanyIamPermissionService permissions = mock(CompanyIamPermissionService.class);
    BrowserPushReadiness readiness = mock(BrowserPushReadiness.class);
    NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
    DeliveryEligibilityService service =
        new DeliveryEligibilityService(
            repository,
            new NotificationDeliveryPolicy(properties),
            permissions,
            properties,
            readiness);

    when(repository.companyBusinessEmail(9L)).thenReturn("company@example.com");
    when(repository.recipientSnapshot(7L, 9L))
        .thenReturn(Optional.of(activeRecipient(7L, null, false, false)));

    var decision = service.evaluate(welcomeIntent(), NOW);

    assertThat(decision.action()).isEqualTo(DeliveryEligibilityService.Decision.Action.SUPPRESS);
    assertThat(decision.code()).isEqualTo("COMPANY_INELIGIBLE");
  }

  @Test
  void pushFailsClosedWhenCipherIsNotReady() {
    NotificationDeliveryRuntimeRepository repository =
        mock(NotificationDeliveryRuntimeRepository.class);
    CompanyIamPermissionService permissions = mock(CompanyIamPermissionService.class);
    BrowserPushReadiness readiness = mock(BrowserPushReadiness.class);
    NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
    properties.getPush().setVapidPublicKey("public");
    properties.getPush().setVapidPrivateKey("private");
    DeliveryEligibilityService service =
        new DeliveryEligibilityService(
            repository,
            new NotificationDeliveryPolicy(properties),
            permissions,
            properties,
            readiness);

    DeliveryIntent intent =
        new DeliveryIntent(
            1L,
            2L,
            3L,
            9L,
            7L,
            "INBOX_ACTIVITY",
            null,
            Channel.WEB_PUSH,
            DeliveryMode.IMMEDIATE,
            "push:3:7",
            "CLAIMED",
            NOW,
            NOW,
            0,
            "claim",
            NOW.plusSeconds(60),
            NOW);
    when(repository.companyBusinessEmail(9L)).thenReturn("company@example.com");
    when(repository.recipientSnapshot(7L, 9L))
        .thenReturn(Optional.of(activeRecipient(7L, 9L, true, true)));
    when(repository.effectivePreference(7L, 9L, "INBOX_ACTIVITY"))
        .thenReturn(new EffectivePreference(null, true));
    when(readiness.ready()).thenReturn(false);

    var decision = service.evaluate(intent, NOW);

    assertThat(decision.action()).isEqualTo(DeliveryEligibilityService.Decision.Action.SUPPRESS);
    assertThat(decision.code()).isEqualTo("PUSH_NOT_CONFIGURED");
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

  private RecipientSnapshot activeRecipient(
      long userId, Long companyId, boolean companyEligible, boolean activeMember) {
    return new RecipientSnapshot(
        userId,
        "user@example.com",
        "User",
        true,
        true,
        companyId,
        companyId == null ? null : "Workspace",
        "UTC",
        companyEligible,
        activeMember);
  }
}
