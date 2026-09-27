package com.messaging.notification.platform.core.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.common.exception.BadRequestException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationTypeRegistryTests {

  private final NotificationTypeRegistry registry = new NotificationTypeRegistry();

  @Test
  void companyCreatedIsMandatoryAndRendersOnlyServerOwnedRoute() {
    NotificationTypeRegistry.Definition definition =
        registry.requireSource("company.created", 1);

    assertThat(definition.notificationType()).isEqualTo("COMPANY_CREATED");
    assertThat(definition.mandatoryEmail()).isTrue();
    assertThat(registry.render(definition, Map.of("companyName", "Acme")).actionPath())
        .isEqualTo("/workspace");
  }

  @Test
  void inboxAllowsOnlyConnectionIdAndDigestEmailPreference() {
    NotificationTypeRegistry.Definition definition =
        registry.requireSource("inbox.activity", 1);

    registry.validateVariables(definition, Map.of("connectionId", "123"));

    assertThat(definition.coalesced()).isTrue();
    assertThat(definition.allowedEmailModes())
        .containsExactly(
            NotificationTypeRegistry.EmailMode.OFF,
            NotificationTypeRegistry.EmailMode.DIGEST);
    assertThatThrownBy(
            () ->
                registry.validateVariables(
                    definition, Map.of("connectionId", "123", "subject", "secret")))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void unknownSourceOrSchemaIsRejected() {
    assertThatThrownBy(() -> registry.requireSource("arbitrary.notification", 1))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> registry.requireSource("company.created", 2))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void stablePreferenceTaxonomyContainsSecurityAndInboxTypes() {
    assertThat(registry.requireNotificationType("SECURITY_ACCOUNT_CRITICAL").mandatoryEmail())
        .isTrue();
    assertThat(registry.requireNotificationType("INBOX_ACTIVITY").defaultEmailMode())
        .isEqualTo(NotificationTypeRegistry.EmailMode.OFF);
  }
}
