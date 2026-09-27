package com.messaging.notification.platform.delivery.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.model.DeliveryModels.QuietHours;
import java.time.Instant;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class NotificationDeliveryPolicyTests {

  private final NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
  private final NotificationDeliveryPolicy policy = new NotificationDeliveryPolicy(properties);

  @Test
  void digestUsesCountTriggerWithDebounceButNeverPastTwoHourMaxWait() {
    Instant first = Instant.parse("2026-09-27T00:00:00Z");
    Instant latest = Instant.parse("2026-09-27T01:55:00Z");

    assertThat(policy.digestEligibleAt(first, latest, 9))
        .isEqualTo(Instant.parse("2026-09-27T02:00:00Z"));
    assertThat(policy.digestEligibleAt(first, latest, 10))
        .isEqualTo(Instant.parse("2026-09-27T02:00:00Z"));
    assertThat(policy.digestEligibleAt(first, first.plusSeconds(60 * 30), 10))
        .isEqualTo(Instant.parse("2026-09-27T00:45:00Z"));
  }

  @Test
  void quietHoursAcrossMidnightDefersToConfiguredLocalEnd() {
    Instant now = Instant.parse("2026-09-27T18:00:00Z");
    QuietHours quiet =
        new QuietHours("Asia/Kolkata", true, LocalTime.of(22, 0), LocalTime.of(8, 0));

    assertThat(policy.quietHoursDeferral(now, quiet))
        .isEqualTo(Instant.parse("2026-09-28T02:30:00Z"));
  }

  @Test
  void retriesAreBoundedByConfiguredAttemptCounts() {
    assertThat(policy.emailAttemptsExhausted(4)).isFalse();
    assertThat(policy.emailAttemptsExhausted(5)).isTrue();
    assertThat(policy.pushAttemptsExhausted(3)).isFalse();
    assertThat(policy.pushAttemptsExhausted(4)).isTrue();
  }
}
