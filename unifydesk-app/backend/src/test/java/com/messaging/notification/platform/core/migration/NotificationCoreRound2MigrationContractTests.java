package com.messaging.notification.platform.core.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NotificationCoreRound2MigrationContractTests {

  @Test
  void v73AddsPermissionCounterBucketsBackfillAndCleanupIndexes() throws Exception {
    try (var stream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("db/migration/V73__notification_core_r2.sql")) {
      assertThat(stream).isNotNull();
      String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

      assertThat(sql)
          .contains("notification_unread_permission_counters")
          .contains("PRIMARY KEY (user_id, company_id, permission_key)")
          .contains("required_permission_key IS NOT NULL")
          .contains("required_permission_key IS NULL")
          .contains("idx_user_notifications_company_permission_unread")
          .contains("idx_user_notifications_retention")
          .contains("idx_notification_event_recipients_company")
          .contains("idx_notification_fanout_company");
    }
  }
}
