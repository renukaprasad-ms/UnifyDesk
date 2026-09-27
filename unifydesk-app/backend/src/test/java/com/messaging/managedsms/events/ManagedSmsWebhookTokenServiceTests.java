package com.messaging.managedsms.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ManagedSmsWebhookTokenServiceTests {

  @Test
  void currentAccountTokenIsScopedAndHighEntropyKeyIsRequired() {
    ManagedSmsRuntimeProperties properties = new ManagedSmsRuntimeProperties();
    properties.setWebhookTokenKey("0123456789abcdef0123456789abcdef");
    ManagedSmsWebhookTokenService service = new ManagedSmsWebhookTokenService(properties);

    String tokenA = service.tokenForAccount("account-a");
    assertThat(service.matches("account-a", tokenA)).isTrue();
    assertThat(service.matches("account-b", tokenA)).isFalse();

    properties.setWebhookTokenKey("short");
    assertThat(service.matches("account-a", tokenA)).isFalse();
  }

  @Test
  void previousKeyWorksOnlyInsideExplicitRotationDeadline() {
    String previousKey = "previous-key-0123456789abcdef01234567";
    ManagedSmsRuntimeProperties previousOnly = new ManagedSmsRuntimeProperties();
    previousOnly.setWebhookTokenKey(previousKey);
    String previousToken =
        new ManagedSmsWebhookTokenService(previousOnly).tokenForAccount("account-a");

    ManagedSmsRuntimeProperties rotating = new ManagedSmsRuntimeProperties();
    rotating.setWebhookTokenKey("current-key--0123456789abcdef01234567");
    rotating.setWebhookPreviousTokenKey(previousKey);
    rotating.setWebhookPreviousTokenValidUntil(Instant.now().plusSeconds(60).toString());
    ManagedSmsWebhookTokenService service = new ManagedSmsWebhookTokenService(rotating);

    assertThat(service.matches("account-a", previousToken)).isTrue();

    rotating.setWebhookPreviousTokenValidUntil(Instant.now().minusSeconds(1).toString());
    assertThat(service.matches("account-a", previousToken)).isFalse();
  }
}
