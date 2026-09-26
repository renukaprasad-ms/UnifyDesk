package com.messaging.managedemail.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.messaging.managedemail.metering.ManagedEmailUsageService;
import com.messaging.managedemail.reputation.ManagedEmailReputationService;
import com.messaging.managedemail.runtime.ManagedEmailIdGenerator;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class ManagedEmailEventReliabilityDatabaseTests {

  private static JdbcTemplate admin;
  private JdbcTemplate jdbc;
  private TransactionTemplate transaction;
  private ManagedEmailEventLedger ledger;
  private ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime;
  private ManagedEmailProviderEvent event;

  @BeforeAll
  static void createSchema() {
    admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:55432/messaging_test", "test", "test"));
    admin.execute("drop schema if exists b2_event_reliability_test cascade");
    admin.execute("create schema b2_event_reliability_test");
  }

  @AfterAll
  static void dropSchema() {
    if (admin != null) {
      admin.execute("drop schema if exists b2_event_reliability_test cascade");
    }
  }

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            "jdbc:postgresql://127.0.0.1:55432/messaging_test"
                + "?currentSchema=b2_event_reliability_test",
            "test",
            "test");
    jdbc = new JdbcTemplate(dataSource);
    transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    jdbc.execute(
        """
        create table if not exists managed_email_provider_events (
          id bigint primary key,
          sns_message_id varchar(128) not null unique,
          normalized_event_key varchar(255) not null unique,
          company_id bigint,
          tenant_id bigint,
          domain_id bigint,
          sender_id bigint,
          run_id bigint,
          recipient_id bigint,
          provider_message_id varchar(512),
          event_type varchar(40) not null,
          event_timestamp timestamptz not null,
          bounce_type varchar(32),
          diagnostic_code varchar(128),
          correlation_status varchar(32) not null,
          payload_hash varchar(128) not null,
          received_at timestamptz not null,
          processed_at timestamptz
        )
        """);
    jdbc.update("delete from managed_email_provider_events");

    ManagedEmailIdGenerator ids = mock(ManagedEmailIdGenerator.class);
    when(ids.nextId()).thenReturn(1001L, 1002L, 1003L);
    ledger = new ManagedEmailEventLedger(jdbc, ids);

    runtime =
        new ManagedEmailRuntimeRepository.ManagedRecipientRuntime(
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

    event =
        new ManagedEmailProviderEvent(
            "sns-retry-1",
            ManagedEmailProviderEventType.DELIVERY,
            Instant.parse("2026-09-26T10:00:00Z"),
            "provider-1",
            10L,
            60L,
            20L,
            40L,
            70L,
            "corr-70",
            null,
            null,
            "payload-hash");
  }

  @Test
  void receiptSurvivesFailureAndSameSnsRetryFinishesExactlyOnce() {
    transaction.executeWithoutResult(status -> ledger.ensureMatchedReceipt(event, runtime));
    assertThat(processed()).isFalse();

    ManagedEmailDeliveryProjectionService projection =
        mock(ManagedEmailDeliveryProjectionService.class);
    ManagedEmailSuppressionFeedbackService suppression =
        mock(ManagedEmailSuppressionFeedbackService.class);
    ManagedEmailUsageService usage = mock(ManagedEmailUsageService.class);
    ManagedEmailReputationService reputation = mock(ManagedEmailReputationService.class);
    ManagedEmailEventLocalProcessor local =
        new ManagedEmailEventLocalProcessor(
            ledger, projection, suppression, usage, reputation);

    doThrow(new IllegalStateException("forced projection failure"))
        .doNothing()
        .when(projection)
        .apply(event, runtime);

    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> local.process(event, runtime)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("forced projection failure");
    assertThat(processed()).isFalse();

    transaction.executeWithoutResult(status -> ledger.ensureMatchedReceipt(event, runtime));
    transaction.executeWithoutResult(status -> local.process(event, runtime));

    assertThat(processed()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from managed_email_provider_events where sns_message_id = ?",
                Long.class,
                event.snsMessageId()))
        .isEqualTo(1L);
    verify(projection, times(2)).apply(event, runtime);

    transaction.executeWithoutResult(status -> local.process(event, runtime));
    verify(projection, times(2)).apply(event, runtime);
  }

  private boolean processed() {
    Boolean value =
        jdbc.queryForObject(
            """
            select processed_at is not null
              from managed_email_provider_events
             where sns_message_id = ?
            """,
            Boolean.class,
            event.snsMessageId());
    return Boolean.TRUE.equals(value);
  }
}
