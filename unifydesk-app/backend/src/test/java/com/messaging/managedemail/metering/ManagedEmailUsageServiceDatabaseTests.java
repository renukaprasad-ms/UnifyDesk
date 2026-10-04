package com.messaging.managedemail.metering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.service.EntitlementService;
import com.messaging.billing.service.ManagedFinancialExposureGuard;
import com.messaging.billing.service.UsageBillingService;
import com.messaging.managedemail.runtime.ManagedEmailIdGenerator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class ManagedEmailUsageServiceDatabaseTests {

  private static JdbcTemplate admin;
  private JdbcTemplate jdbc;
  private TransactionTemplate transaction;
  private EntitlementService entitlement;
  private ManagedEmailUsageService usage;

  @BeforeAll
  static void createSchema() {
    admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:55432/messaging_test", "test", "test"));
    admin.execute("drop schema if exists b2_managed_email_usage_test cascade");
    admin.execute("create schema b2_managed_email_usage_test");
  }

  @AfterAll
  static void dropSchema() {
    if (admin != null) {
      admin.execute("drop schema if exists b2_managed_email_usage_test cascade");
    }
  }

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            "jdbc:postgresql://127.0.0.1:55432/messaging_test"
                + "?currentSchema=b2_managed_email_usage_test",
            "test",
            "test");
    jdbc = new JdbcTemplate(dataSource);
    transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    jdbc.execute(
        """
        create table if not exists managed_email_usage_periods (
          company_id bigint not null,
          period_start date not null,
          accepted_count bigint not null default 0,
          reserved_count bigint not null default 0,
          adjusted_count bigint not null default 0,
          updated_at timestamptz not null,
          version bigint not null default 0,
          primary key (company_id, period_start)
        )
        """);
    jdbc.execute(
        """
        create table if not exists managed_email_usage_events (
          id bigint primary key,
          company_id bigint not null,
          recipient_id bigint not null,
          run_id bigint not null,
          provider_message_id varchar(512),
          event_type varchar(32) not null,
          units bigint not null,
          period_start date not null,
          idempotency_key varchar(255) not null unique,
          created_at timestamptz not null,
          metadata_json text
        )
        """);
    jdbc.update("delete from managed_email_usage_events");
    jdbc.update("delete from managed_email_usage_periods");

    entitlement = mock(EntitlementService.class);
    ManagedEmailIdGenerator ids = mock(ManagedEmailIdGenerator.class);
    AtomicLong sequence = new AtomicLong(1000);
    when(ids.nextId()).thenAnswer(invocation -> sequence.incrementAndGet());
    usage =
        new ManagedEmailUsageService(
            jdbc,
            entitlement,
            ids,
            mock(UsageBillingService.class),
            mock(ManagedFinancialExposureGuard.class));
  }

  @Test
  void concurrentMonthlyReservationsCannotExceedAllowance() throws Exception {
    when(entitlement.integerValue(10L, ManagedEmailUsageService.MONTHLY_ENTITLEMENT))
        .thenReturn(1L);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    Future<Throwable> first = pool.submit(() -> reserveAfterBarrier(70L, ready, start));
    Future<Throwable> second = pool.submit(() -> reserveAfterBarrier(71L, ready, start));
    ready.await(5, TimeUnit.SECONDS);
    start.countDown();

    Throwable firstFailure = first.get(5, TimeUnit.SECONDS);
    Throwable secondFailure = second.get(5, TimeUnit.SECONDS);
    pool.shutdownNow();

    assertThat(
            java.util.List.of(firstFailure, secondFailure).stream()
                .filter(PlanEntitlementException.class::isInstance)
                .count())
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select reserved_count from managed_email_usage_periods where company_id = 10",
                Long.class))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from managed_email_usage_events where event_type = 'RESERVE'",
                Long.class))
        .isEqualTo(1L);
  }

  @Test
  void reserveCommitAndReleaseAreIdempotent() {
    when(entitlement.integerValue(10L, ManagedEmailUsageService.MONTHLY_ENTITLEMENT))
        .thenReturn(10L);

    transaction.executeWithoutResult(status -> usage.reserve(10L, 70L, 60L, 1));
    transaction.executeWithoutResult(status -> usage.reserve(10L, 70L, 60L, 1));
    transaction.executeWithoutResult(
        status -> usage.commit(10L, 70L, 60L, 1, "provider-1"));
    transaction.executeWithoutResult(
        status -> usage.commit(10L, 70L, 60L, 1, "provider-1"));
    transaction.executeWithoutResult(status -> usage.release(10L, 70L, 60L, 1));

    assertThat(
            jdbc.queryForObject(
                "select accepted_count from managed_email_usage_periods where company_id = 10",
                Long.class))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select reserved_count from managed_email_usage_periods where company_id = 10",
                Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from managed_email_usage_events where event_type = 'COMMIT'",
                Long.class))
        .isEqualTo(1L);

    transaction.executeWithoutResult(status -> usage.reserve(10L, 71L, 60L, 1));
    transaction.executeWithoutResult(status -> usage.release(10L, 71L, 60L, 1));
    transaction.executeWithoutResult(status -> usage.release(10L, 71L, 60L, 1));

    assertThat(
            jdbc.queryForObject(
                "select reserved_count from managed_email_usage_periods where company_id = 10",
                Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from managed_email_usage_events where event_type = 'RELEASE'",
                Long.class))
        .isEqualTo(1L);
  }

  private Throwable reserveAfterBarrier(
      Long recipientId, CountDownLatch ready, CountDownLatch start) {
    try {
      ready.countDown();
      start.await(5, TimeUnit.SECONDS);
      transaction.executeWithoutResult(status -> usage.reserve(10L, recipientId, 60L, 1));
      return NoFailure.INSTANCE;
    } catch (Throwable throwable) {
      return rootCause(throwable);
    }
  }

  private Throwable rootCause(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) current = current.getCause();
    return current;
  }

  private static final class NoFailure extends Throwable {
    private static final NoFailure INSTANCE = new NoFailure();
  }
}
