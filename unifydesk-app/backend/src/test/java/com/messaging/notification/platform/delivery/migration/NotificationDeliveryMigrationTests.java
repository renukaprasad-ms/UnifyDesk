package com.messaging.notification.platform.delivery.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NotificationDeliveryMigrationTests {

  @Test
  void v71OwnsOnlyDeliveryTablesAndDoesNotRequireV70ForeignKeys() throws IOException {
    String sql = migration("V71__notification_delivery.sql");

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

  @Test
  void v74AddsExplicitProviderCallLifecycleAndBoundedCleanupIndexes() throws IOException {
    String sql = migration("V74__notification_delivery_r2.sql");

    assertThat(sql)
        .contains("provider_call_state")
        .contains("'prepared'")
        .contains("'in_flight'")
        .contains("'completed'")
        .contains("idx_notification_delivery_attempts_recovery")
        .contains("idx_notification_delivery_attempts_retention")
        .contains("idx_notification_delivery_intents_retention")
        .contains("idx_notification_push_attempts_retention")
        .contains("idx_notification_digest_buckets_retention")
        .contains("idx_notification_push_subscriptions_retention");
  }

  @Test
  void v75PersistsProviderRetryAfterWithoutRewritingExistingRows() throws IOException {
    String sql = migration("V75__notification_delivery_r3.sql");

    assertThat(sql)
        .contains("add column provider_retry_after_at timestamptz null")
        .contains("provider_retry_after_at is null or provider_call_state = 'completed'");
    assertThat(sql).doesNotContain("update notification_delivery_attempts");
  }

  private String migration(String file) throws IOException {
    try (var input = getClass().getClassLoader().getResourceAsStream("db/migration/" + file)) {
      assertThat(input).isNotNull();
      return new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
    }
  }
}
