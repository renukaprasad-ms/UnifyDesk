package com.messaging.notification.platform.delivery.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NotificationDeliveryMigrationTests {

  @Test
  void v71OwnsOnlyDeliveryTablesAndDoesNotRequireV70ForeignKeys() throws IOException {
    try (var input =
        getClass()
            .getClassLoader()
            .getResourceAsStream("db/migration/V71__notification_delivery.sql")) {
      assertThat(input).isNotNull();
      String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

      assertThat(sql)
          .contains("create table notification_delivery_attempts")
          .contains("create table notification_push_subscriptions")
          .contains("create table notification_push_attempts")
          .contains("create table notification_digest_buckets")
          .contains("create table notification_digest_items");
      assertThat(sql).doesNotContain("references notification_delivery_intents");
      assertThat(sql).doesNotContain("references user_notifications");
      assertThat(sql).doesNotContain("references notification_events");
    }
  }
}
