package com.messaging.managedemail.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.messaging.billing.service.ManagedFinancialExposureGuard;
import com.messaging.managedemail.metering.ManagedEmailUsageService;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ManagedEmailProviderBudgetServiceDatabaseTests {

  private static JdbcTemplate admin;
  private JdbcTemplate jdbc;
  private ManagedEmailUsageService usage;
  private ManagedEmailRuntimeProperties properties;

  @BeforeAll
  static void createSchema() {
    admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:55432/messaging_test", "test", "test"));
    admin.execute("drop schema if exists b2_provider_budget_test cascade");
    admin.execute("create schema b2_provider_budget_test");
  }

  @AfterAll
  static void dropSchema() {
    if (admin != null) {
      admin.execute("drop schema if exists b2_provider_budget_test cascade");
    }
  }

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            "jdbc:postgresql://127.0.0.1:55432/messaging_test"
                + "?currentSchema=b2_provider_budget_test",
            "test",
            "test");
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute(
        """
        create table if not exists broadcast_sender_connections (
          id bigint primary key,
          management_mode varchar(32) not null,
          provider varchar(40) not null,
          region varchar(64) not null
        )
        """);
    jdbc.execute(
        """
        create table if not exists broadcast_senders (
          id bigint primary key,
          connection_id bigint not null
        )
        """);
    jdbc.execute(
        """
        create table if not exists broadcast_recipients (
          id bigint primary key,
          sender_id bigint not null
        )
        """);
    jdbc.execute(
        """
        create table if not exists managed_email_usage_events (
          id bigint primary key,
          company_id bigint not null,
          recipient_id bigint not null,
          run_id bigint not null,
          event_type varchar(32) not null,
          units bigint not null,
          period_start date not null,
          idempotency_key varchar(255) not null unique,
          created_at timestamptz not null
        )
        """);

    jdbc.update("delete from managed_email_usage_events");
    jdbc.update("delete from broadcast_recipients");
    jdbc.update("delete from broadcast_senders");
    jdbc.update("delete from broadcast_sender_connections");
    jdbc.update(
        """
        insert into broadcast_sender_connections
          (id, management_mode, provider, region)
        values (30, 'MESSAGING_MANAGED', 'AMAZON_SES', 'us-east-1')
        """);
    jdbc.update("insert into broadcast_senders (id, connection_id) values (20, 30)");
    jdbc.update("insert into broadcast_recipients (id, sender_id) values (70, 20)");

    usage = mock(ManagedEmailUsageService.class);
    properties = new ManagedEmailRuntimeProperties();
    properties.setProviderSnapshotMaxAgeSeconds(7200);
  }

  @Test
  void nonZeroProviderUsageAndLocalSendsReduceRollingCapacity() {
    Instant snapshot = Instant.now().minusSeconds(3600);
    insertCommit(1001, 70, snapshot.plusSeconds(10));
    insertCommit(1002, 70, snapshot.plusSeconds(20));
    ManagedEmailProviderBudgetService service = service();

    ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime =
        runtime(5.0, 100.0, 97.0, snapshot);

    assertThat(service.reserveForSend(runtime, 70L, 60L, 1)).isTrue();
    verify(usage).reserve(10L, 70L, 60L, 1);

    insertCommit(1003, 70, snapshot.plusSeconds(30));
    assertThat(service.reserveForSend(runtime, 70L, 60L, 2)).isFalse();
  }

  @Test
  void exactProviderObservationTimestampIsTheInclusiveLowerBound() {
    Instant providerObservationT1 = Instant.parse("2026-09-26T10:00:00Z");
    Instant acceptedAtBoundary = providerObservationT1;
    Instant acceptedAfterObservationT2 = Instant.parse("2026-09-26T10:00:01Z");
    Instant persistenceProcessingT3 = Instant.parse("2026-09-26T10:00:10Z");
    properties.setProviderSnapshotMaxAgeSeconds(Integer.MAX_VALUE);

    insertCommit(1001, 70, acceptedAtBoundary);
    insertCommit(1002, 70, acceptedAfterObservationT2);

    ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime =
        runtime(5.0, 100.0, 98.0, providerObservationT1);

    assertThat(persistenceProcessingT3).isAfter(acceptedAfterObservationT2);
    assertThat(service().reserveForSend(runtime, 70L, 60L, 3)).isFalse();
    verifyNoInteractions(usage);
  }

  @Test
  void utcMidnightDoesNotResetProviderBudget() {
    Instant snapshot = Instant.parse("2026-09-25T23:59:00Z");
    properties.setProviderSnapshotMaxAgeSeconds(Integer.MAX_VALUE);
    insertCommit(1001, 70, Instant.parse("2026-09-26T00:01:00Z"));
    ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime =
        runtime(5.0, 2.0, 1.0, snapshot);

    assertThat(service().reserveForSend(runtime, 70L, 60L, 1)).isFalse();
    verifyNoInteractions(usage);
  }

  @Test
  void restartAndRedisLossCannotForgetDurableOutstandingReservation() {
    Instant snapshot = Instant.now().minusSeconds(60);
    insertReserve(1001, 70, snapshot.plusSeconds(5), "managed-email:reserve:70:1");
    ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime =
        runtime(5.0, 2.0, 1.0, snapshot);

    ManagedEmailProviderBudgetService first = service();
    ManagedEmailProviderBudgetService restarted = service();

    assertThat(first.reserveForSend(runtime, 70L, 60L, 2)).isFalse();
    assertThat(restarted.reserveForSend(runtime, 70L, 60L, 2)).isFalse();
    verifyNoInteractions(usage);
  }

  @Test
  void staleLoweredOrMissingProviderQuotaFailsClosed() {
    ManagedEmailProviderBudgetService service = service();
    Instant stale = Instant.now().minusSeconds(7201);

    assertThat(service.hasUsableProviderSnapshot(runtime(5.0, 100.0, 1.0, stale))).isFalse();
    assertThat(
            service.hasUsableProviderSnapshot(
                runtime(5.0, 10.0, 11.0, Instant.now())))
        .isFalse();
    assertThat(
            service.hasUsableProviderSnapshot(
                runtime(5.0, null, 0.0, Instant.now())))
        .isFalse();
    assertThat(
            service.hasUsableProviderSnapshot(
                runtime(5.0, 100.0, null, Instant.now())))
        .isFalse();
  }

  private ManagedEmailProviderBudgetService service() {
    return new ManagedEmailProviderBudgetService(
        jdbc, usage, properties, mock(ManagedFinancialExposureGuard.class));
  }

  private void insertCommit(long id, long recipientId, Instant at) {
    jdbc.update(
        """
        insert into managed_email_usage_events
          (id, company_id, recipient_id, run_id, event_type, units,
           period_start, idempotency_key, created_at)
        values (?, 10, ?, 60, 'COMMIT', 1, current_date, ?, ?)
        """,
        id,
        recipientId,
        "commit:" + id,
        java.sql.Timestamp.from(at));
  }

  private void insertReserve(long id, long recipientId, Instant at, String key) {
    jdbc.update(
        """
        insert into managed_email_usage_events
          (id, company_id, recipient_id, run_id, event_type, units,
           period_start, idempotency_key, created_at)
        values (?, 10, ?, 60, 'RESERVE', 1, current_date, ?, ?)
        """,
        id,
        recipientId,
        key,
        java.sql.Timestamp.from(at));
  }

  private ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime(
      Double maxRate, Double max24h, Double sent24h, Instant validatedAt) {
    return new ManagedEmailRuntimeRepository.ManagedSenderRuntime(
        10L,
        20L,
        30L,
        50L,
        40L,
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
        maxRate,
        max24h,
        sent24h,
        validatedAt);
  }
}
