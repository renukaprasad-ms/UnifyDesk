package com.messaging.managedemail.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.messaging.broadcast.enums.BroadcastRecipientStatus;
import com.messaging.broadcast.service.BroadcastStatsService;
import com.messaging.contacts.integration.TrustedProviderSuppressionBridge;
import com.messaging.managedemail.runtime.ManagedEmailIdGenerator;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ManagedEmailDeliveryProjectionServiceDatabaseTests {

  private static JdbcTemplate admin;
  private JdbcTemplate jdbc;
  private ManagedEmailDeliveryProjectionService projection;
  private ManagedEmailSuppressionFeedbackService suppression;
  private BroadcastStatsService stats;

  @BeforeAll
  static void createSchema() {
    admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:55432/messaging_test", "test", "test"));
    admin.execute("drop schema if exists b2_managed_email_projection_test cascade");
    admin.execute("create schema b2_managed_email_projection_test");
  }

  @AfterAll
  static void dropSchema() {
    if (admin != null) {
      admin.execute("drop schema if exists b2_managed_email_projection_test cascade");
    }
  }

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            "jdbc:postgresql://127.0.0.1:55432/messaging_test"
                + "?currentSchema=b2_managed_email_projection_test",
            "test",
            "test");
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute(
        """
        create table if not exists broadcast_recipients (
          id bigint primary key,
          company_id bigint not null,
          run_id bigint not null,
          status varchar(32) not null,
          bounced_at timestamptz,
          updated_at timestamptz not null
        )
        """);
    jdbc.execute(
        """
        create table if not exists broadcast_suppressions (
          id bigint primary key,
          company_id bigint not null,
          channel_type varchar(24) not null,
          normalized_destination varchar(320) not null,
          reason varchar(32) not null,
          source_recipient_id bigint,
          active boolean not null,
          created_at timestamptz not null,
          updated_at timestamptz not null,
          unique(company_id, channel_type, normalized_destination)
        )
        """);
    jdbc.execute(
        """
        create table if not exists managed_email_recipient_delivery (
          recipient_id bigint primary key,
          company_id bigint not null,
          provider_message_id varchar(512),
          sent_event_at timestamptz,
          delivered_at timestamptz,
          complained_at timestamptz,
          rejected_at timestamptz,
          first_open_at timestamptz,
          last_open_at timestamptz,
          open_count bigint not null default 0,
          first_click_at timestamptz,
          last_click_at timestamptz,
          click_count bigint not null default 0,
          last_delivery_delay_at timestamptz,
          last_event_at timestamptz not null,
          updated_at timestamptz not null,
          version bigint not null default 0
        )
        """);
    jdbc.update("delete from managed_email_recipient_delivery");
    jdbc.update("delete from broadcast_suppressions");
    jdbc.update("delete from broadcast_recipients");
    jdbc.update(
        """
        insert into broadcast_recipients
          (id, company_id, run_id, status, updated_at)
        values (70, 10, 60, 'ACCEPTED_BY_PROVIDER', now())
        """);

    stats = mock(BroadcastStatsService.class);
    ManagedEmailIdGenerator ids = mock(ManagedEmailIdGenerator.class);
    when(ids.nextId()).thenReturn(1001L, 1002L, 1003L);
    projection = new ManagedEmailDeliveryProjectionService(jdbc);
    TrustedProviderSuppressionBridge contactSuppressionBridge =
        mock(TrustedProviderSuppressionBridge.class);
    suppression =
        new ManagedEmailSuppressionFeedbackService(jdbc, stats, ids, contactSuppressionBridge);
  }

  @Test
  void openAndClickCountsAreIdempotentlyProjectedPerDistinctLedgerEvent() {
    ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime = runtime();
    projection.apply(event(ManagedEmailProviderEventType.OPEN, "sns-open-1", "hash-1", "2026-09-26T07:01:00Z"), runtime);
    projection.apply(event(ManagedEmailProviderEventType.OPEN, "sns-open-2", "hash-2", "2026-09-26T07:02:00Z"), runtime);
    projection.apply(event(ManagedEmailProviderEventType.CLICK, "sns-click-1", "hash-3", "2026-09-26T07:03:00Z"), runtime);

    assertThat(
            jdbc.queryForObject(
                "select open_count from managed_email_recipient_delivery where recipient_id = 70",
                Long.class))
        .isEqualTo(2L);
    assertThat(
            jdbc.queryForObject(
                "select click_count from managed_email_recipient_delivery where recipient_id = 70",
                Long.class))
        .isEqualTo(1L);
  }

  @Test
  void lateDeliveryDoesNotClearComplaintOrHardBounce() {
    ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime = runtime();
    ManagedEmailProviderEvent bounce =
        event(
            ManagedEmailProviderEventType.BOUNCE,
            "sns-bounce",
            "hash-b",
            "2026-09-26T07:02:00Z");
    suppression.hardBounce(runtime, bounce);
    projection.apply(
        event(ManagedEmailProviderEventType.COMPLAINT, "sns-complaint", "hash-c", "2026-09-26T07:03:00Z"),
        runtime);
    projection.apply(
        event(ManagedEmailProviderEventType.DELIVERY, "sns-delivery", "hash-d", "2026-09-26T07:04:00Z"),
        runtime);

    assertThat(
            jdbc.queryForObject(
                "select status from broadcast_recipients where id = 70", String.class))
        .isEqualTo("BOUNCED");
    assertThat(
            jdbc.queryForObject(
                "select complained_at is not null from managed_email_recipient_delivery where recipient_id = 70",
                Boolean.class))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select delivered_at is not null from managed_email_recipient_delivery where recipient_id = 70",
                Boolean.class))
        .isTrue();
    verify(stats)
        .transition(
            60L,
            BroadcastRecipientStatus.ACCEPTED_BY_PROVIDER,
            BroadcastRecipientStatus.BOUNCED);
  }

  private ManagedEmailProviderEvent event(
      ManagedEmailProviderEventType type,
      String snsMessageId,
      String hash,
      String timestamp) {
    return new ManagedEmailProviderEvent(
        snsMessageId,
        type,
        Instant.parse(timestamp),
        "provider-1",
        10L,
        60L,
        20L,
        40L,
        70L,
        "corr-70",
        type == ManagedEmailProviderEventType.BOUNCE ? "Permanent" : null,
        null,
        hash);
  }

  private ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime() {
    return new ManagedEmailRuntimeRepository.ManagedRecipientRuntime(
        70L,
        10L,
        60L,
        20L,
        30L,
        50L,
        40L,
        "ACCEPTED_BY_PROVIDER",
        "recipient@example.test",
        "provider-1",
        "corr-70",
        "offers@example.test",
        "ACTIVE",
        "ACTIVE",
        "MESSAGING_MANAGED",
        true,
        true,
        "ACTIVE",
        "ENABLED",
        "HEALTHY",
        "ESTABLISHED",
        "ACTIVE",
        "example.test",
        "us-east-1",
        "tenant-10",
        "cfg-10",
        20.0,
        10000.0,
        100.0,
        Instant.now());
  }
}
