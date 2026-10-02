package com.messaging.managedemail.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.messaging.managedemail.metering.ManagedEmailUsageService;
import com.messaging.managedemail.reputation.ManagedEmailReputationService;
import com.messaging.managedemail.runtime.ManagedEmailIdGenerator;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sesv2.model.SuppressionListReason;

class ManagedEmailProviderEffectDatabaseTests {

  private static JdbcTemplate admin;
  private JdbcTemplate jdbc;
  private TransactionTemplate transaction;
  private ManagedEmailEventLedger ledger;
  private JdbcManagedEmailProviderEffectRepository effects;
  private ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime;
  private ManagedEmailProviderEvent event;

  @BeforeAll
  static void createSchema() {
    admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:55432/messaging_test", "test", "test"));
    admin.execute("drop schema if exists b2_provider_effect_test cascade");
    admin.execute("create schema b2_provider_effect_test");
  }

  @AfterAll
  static void dropSchema() {
    if (admin != null) {
      admin.execute("drop schema if exists b2_provider_effect_test cascade");
    }
  }

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            "jdbc:postgresql://127.0.0.1:55432/messaging_test"
                + "?currentSchema=b2_provider_effect_test",
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
    jdbc.execute(
        """
        create table if not exists managed_email_provider_effects (
          id bigint primary key,
          event_id bigint not null references managed_email_provider_events(id) on delete restrict,
          company_id bigint not null,
          tenant_id bigint not null,
          recipient_id bigint not null,
          effect_type varchar(48) not null,
          effect_reason varchar(32) not null,
          status varchar(24) not null,
          attempt_count integer not null default 0,
          next_attempt_at timestamptz not null,
          claimed_at timestamptz,
          completed_at timestamptz,
          last_error_code varchar(128),
          last_error_message varchar(1000),
          created_at timestamptz not null,
          updated_at timestamptz not null,
          unique(event_id, effect_type)
        )
        """);
    jdbc.update("delete from managed_email_provider_effects");
    jdbc.update("delete from managed_email_provider_events");

    ManagedEmailIdGenerator ids = mock(ManagedEmailIdGenerator.class);
    when(ids.nextId()).thenReturn(1001L, 2001L, 2002L, 2003L);
    ledger = new ManagedEmailEventLedger(jdbc, ids);
    effects = new JdbcManagedEmailProviderEffectRepository(jdbc, ids);
    runtime = runtime();
    event =
        new ManagedEmailProviderEvent(
            "sns-bounce-1",
            ManagedEmailProviderEventType.BOUNCE,
            Instant.parse("2026-09-26T10:00:00Z"),
            "provider-1",
            10L,
            60L,
            20L,
            40L,
            70L,
            "corr-70",
            "Permanent",
            null,
            "payload-hash");
  }

  @Test
  void localCommitDurablySchedulesEffectBeforeProcessedAt() {
    transaction.executeWithoutResult(status -> ledger.ensureMatchedReceipt(event, runtime));

    ManagedEmailDeliveryProjectionService projection =
        mock(ManagedEmailDeliveryProjectionService.class);
    ManagedEmailSuppressionFeedbackService suppression =
        mock(ManagedEmailSuppressionFeedbackService.class);
    ManagedEmailUsageService usage = mock(ManagedEmailUsageService.class);
    ManagedEmailReputationService reputation = mock(ManagedEmailReputationService.class);
    ManagedEmailEventLocalProcessor local =
        new ManagedEmailEventLocalProcessor(
            ledger, projection, suppression, usage, reputation, effects);

    transaction.executeWithoutResult(status -> local.process(event, runtime));
    transaction.executeWithoutResult(status -> local.process(event, runtime));

    assertThat(
            jdbc.queryForObject(
                "select processed_at is not null from managed_email_provider_events where sns_message_id = ?",
                Boolean.class,
                event.snsMessageId()))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from managed_email_provider_effects", Long.class))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select status from managed_email_provider_effects", String.class))
        .isEqualTo("PENDING");
    verify(suppression, times(1)).hardBounce(runtime, event);
  }

  @Test
  void crashBeforeProviderCallLeavesClaimRecoverableAndLaterWorkerSucceeds() {
    transaction.executeWithoutResult(status -> ledger.ensureMatchedReceipt(event, runtime));
    transaction.executeWithoutResult(
        status ->
            effects.ensureTenantSuppressionScheduled(
                event, runtime, SuppressionListReason.BOUNCE));

    Instant claimTime =
        jdbc.queryForObject(
                "select next_attempt_at from managed_email_provider_effects limit 1",
                java.time.OffsetDateTime.class)
            .toInstant()
            .plusSeconds(1);
    var claimed =
        transaction.execute(
            status ->
                effects.claimNext(
                    claimTime, claimTime.minusSeconds(30)).orElseThrow());
    assertThat(claimed.attemptCount()).isEqualTo(1);

    jdbc.update(
        "update managed_email_provider_effects set claimed_at = ? where id = ?",
        java.sql.Timestamp.from(claimTime.minusSeconds(60)),
        claimed.id());

    ManagedEmailRuntimeRepository runtimeRepository =
        mock(ManagedEmailRuntimeRepository.class);
    ManagedEmailProviderSuppressionService suppression =
        mock(ManagedEmailProviderSuppressionService.class);
    when(runtimeRepository.findRecipientRuntime(70L)).thenReturn(Optional.of(runtime));
    ManagedEmailRuntimeProperties properties = new ManagedEmailRuntimeProperties();
    properties.setProviderEffectStaleAfterSeconds(1);
    ManagedEmailProviderEffectWorker worker =
        new ManagedEmailProviderEffectWorker(
            effects, runtimeRepository, suppression, properties);

    assertThat(worker.runOnce()).isTrue();

    assertThat(
            jdbc.queryForObject(
                "select status from managed_email_provider_effects", String.class))
        .isEqualTo("SUCCEEDED");
    verify(suppression).signal(runtime, SuppressionListReason.BOUNCE);
  }

  private ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime() {
    return new ManagedEmailRuntimeRepository.ManagedRecipientRuntime(
        70L, 10L, 60L, 20L, 30L, 50L, 40L, "ACCEPTED_BY_PROVIDER",
        "recipient@example.test", "provider-1", "corr-70", "offers@example.test",
        "ACTIVE", "ACTIVE", "MESSAGING_MANAGED", true, true, "ACTIVE", "ENABLED",
        "HEALTHY", "ESTABLISHED", "ACTIVE", "example.test", "us-east-1",
        "tenant-10", "cfg-10", 20.0, 10000.0, 100.0, Instant.now());
  }
}
