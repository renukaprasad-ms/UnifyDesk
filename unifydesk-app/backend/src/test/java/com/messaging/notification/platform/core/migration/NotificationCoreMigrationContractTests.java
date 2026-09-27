package com.messaging.notification.platform.core.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NotificationCoreMigrationContractTests {

  @Test
  void v70ContainsFrozenReplayCoalescingCounterAndIntentConstraints() throws Exception {
    try (var stream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("db/migration/V70__notification_core.sql")) {
      assertThat(stream).isNotNull();
      String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

      assertThat(sql)
          .contains("uk_notification_events_event_key")
          .contains("UNIQUE (notification_event_id, user_id)")
          .contains("uk_user_notifications_company_bucket")
          .contains("uk_notification_counter_account")
          .contains("uk_notification_counter_company")
          .contains("uk_notification_delivery_intent_key")
          .contains("idx_notification_events_publish_due")
          .contains("idx_notification_fanout_due");
    }
  }
}
