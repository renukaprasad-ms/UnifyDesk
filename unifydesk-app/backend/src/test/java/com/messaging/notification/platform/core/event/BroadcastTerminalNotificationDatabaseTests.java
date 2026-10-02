package com.messaging.notification.platform.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.notification.platform.core.registry.NotificationTypeRegistry;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class BroadcastTerminalNotificationDatabaseTests {

  private static JdbcTemplate admin;
  private static JdbcTemplate jdbc;

  @BeforeAll
  static void createSchema() {
    admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:55432/messaging_test", "test", "test"));
    admin.execute("drop schema if exists cmj_terminal_notification_test cascade");
    admin.execute("create schema cmj_terminal_notification_test");

    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:55432/messaging_test"
                    + "?currentSchema=cmj_terminal_notification_test",
                "test",
                "test"));
    jdbc.execute(
        """
        create table notification_events (
          id bigint primary key,
          event_key varchar(255) not null unique,
          event_type varchar(100) not null,
          schema_version integer not null,
          company_id bigint,
          actor_user_id bigint,
          recipient_mode varchar(32) not null,
          recipient_user_id bigint,
          required_permission_key varchar(160),
          subject_type varchar(64) not null,
          subject_id varchar(160) not null,
          priority varchar(16) not null,
          group_key varchar(180),
          variables_json jsonb not null,
          correlation_id varchar(80) not null,
          causation_id varchar(80),
          occurred_at timestamptz not null,
          publish_state varchar(24) not null,
          publish_attempts integer not null,
          next_publish_at timestamptz not null,
          created_at timestamptz not null,
          updated_at timestamptz not null
        )
        """);
  }

  @AfterAll
  static void dropSchema() {
    if (admin != null) {
      admin.execute("drop schema if exists cmj_terminal_notification_test cascade");
    }
  }

  @Test
  void completedEventUsesDeterministicRunKeyAndReplayIsIdempotent() {
    NotificationEventRecorder recorder =
        new NotificationEventRecorder(jdbc, new ObjectMapper(), new NotificationTypeRegistry());
    Instant completedAt = Instant.parse("2026-10-02T06:10:00Z");

    long first = recorder.recordBroadcastCompleted(30L, 20L, 10L, "EMAIL", completedAt);
    long replay = recorder.recordBroadcastCompleted(30L, 20L, 10L, "EMAIL", completedAt);

    assertThat(replay).isEqualTo(first);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from notification_events where event_key = ?",
                Long.class,
                "broadcast.completed:30"))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select event_type from notification_events where event_key = ?",
                String.class,
                "broadcast.completed:30"))
        .isEqualTo("broadcast.completed");
    assertThat(
            jdbc.queryForObject(
                "select company_id from notification_events where event_key = ?",
                Long.class,
                "broadcast.completed:30"))
        .isEqualTo(10L);
  }
}
