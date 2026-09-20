package com.messaging.channel.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GmailDistributedQuotaManagerTests {

  @Test
  void providerCooldownUsesExponentialBackoffAndProviderHint() {
    GmailOAuthProperties properties = new GmailOAuthProperties();
    properties.setQuotaCooldownInitial(Duration.ofSeconds(30));
    properties.setQuotaCooldownMax(Duration.ofMinutes(5));

    GmailDistributedQuotaManager manager =
        new GmailDistributedQuotaManager(null, properties);

    assertThat(manager.penaltyDelay(1, Duration.ZERO)).isEqualTo(Duration.ofSeconds(30));
    assertThat(manager.penaltyDelay(2, Duration.ZERO)).isEqualTo(Duration.ofSeconds(60));
    assertThat(manager.penaltyDelay(2, Duration.ofSeconds(90)))
        .isEqualTo(Duration.ofSeconds(90));
    assertThat(manager.penaltyDelay(8, Duration.ofMinutes(10)))
        .isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void quotaOperationsMatchDocumentedCosts() {
    assertThat(GmailQuotaOperation.GET_PROFILE.units()).isEqualTo(1);
    assertThat(GmailQuotaOperation.LIST_MESSAGES.units()).isEqualTo(5);
    assertThat(GmailQuotaOperation.HISTORY_LIST.units()).isEqualTo(2);
    assertThat(GmailQuotaOperation.GET_MESSAGE.units()).isEqualTo(20);
    assertThat(GmailQuotaOperation.GET_ATTACHMENT.units()).isEqualTo(20);
    assertThat(GmailQuotaOperation.SEND_MESSAGE.units()).isEqualTo(100);
    assertThat(GmailQuotaOperation.WATCH.units()).isEqualTo(100);
  }
}
