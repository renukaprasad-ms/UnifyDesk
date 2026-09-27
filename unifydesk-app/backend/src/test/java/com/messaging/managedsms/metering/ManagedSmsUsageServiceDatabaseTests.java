package com.messaging.managedsms.metering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.service.EntitlementService;
import com.messaging.billing.service.UsageBillingService;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class ManagedSmsUsageServiceDatabaseTests {

  private static JdbcTemplate admin;
  private JdbcTemplate jdbc;
  private TransactionTemplate transaction;
  private EntitlementService entitlement;
  private UsageBillingService billing;
  private ManagedSmsUsageService usage;

  @BeforeAll
  static void createSchema() {
    admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:55432/messaging_test", "test", "test"));
    admin.execute("drop schema if exists b2_managed_sms_usage_test cascade");
    admin.execute("create schema b2_managed_sms_usage_test");
  }

  @AfterAll
  static void dropSchema() {
    if (admin != null) {
      admin.execute("drop schema if exists b2_managed_sms_usage_test cascade");
    }
  }

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            "jdbc:postgresql://127.0.0.1:55432/messaging_test"
                + "?currentSchema=b2_managed_sms_usage_test",
            "test",
            "test");
    jdbc = new JdbcTemplate(dataSource);
    transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    jdbc.execute(
        """
        create table if not exists managed_sms_usage_reservations (
          id bigint primary key,
          company_id bigint not null,
          run_id bigint not null,
          recipient_id bigint not null,
          attempt_no integer not null,
          estimated_segments integer not null,
          state varchar(32) not null,
          idempotency_key varchar(255) not null unique,
          provider_credit_snapshot numeric(18,6),
          created_at timestamptz not null,
          updated_at timestamptz not null,
          unique(recipient_id, attempt_no)
        )
        """);
    jdbc.update("delete from managed_sms_usage_reservations");
    entitlement = mock(EntitlementService.class);
    billing = mock(UsageBillingService.class);
    usage = new ManagedSmsUsageService(jdbc, entitlement, billing);
  }

  @Test
  void concurrentReservationsCannotExceedMonthlySegmentAllowance() throws Exception {
    when(entitlement.integerValue(10L, ManagedSmsUsageService.MONTHLY_ENTITLEMENT))
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
                "select coalesce(sum(estimated_segments), 0) from managed_sms_usage_reservations where state = 'RESERVED'",
                Long.class))
        .isEqualTo(1L);
  }

  @Test
  void acceptanceCommitIsIdempotent() {
    when(entitlement.integerValue(10L, ManagedSmsUsageService.MONTHLY_ENTITLEMENT))
        .thenReturn(10L);

    transaction.executeWithoutResult(status -> usage.reserve(10L, 60L, 70L, 1, 2));
    transaction.executeWithoutResult(
        status -> usage.commit(10L, 60L, 70L, 1, "req-1", "SERVICE", null));
    transaction.executeWithoutResult(
        status -> usage.commit(10L, 60L, 70L, 1, "req-1", "SERVICE", null));

    assertThat(
            jdbc.queryForObject(
                "select state from managed_sms_usage_reservations where recipient_id = 70",
                String.class))
        .isEqualTo("COMMITTED");
    verify(billing, times(1))
        .record(
            eq(10L),
            eq("MANAGED_SMS"),
            eq("70"),
            eq("managed-sms:recipient:70:attempt:1:accepted"),
            any(),
            any(),
            any(),
            any(),
            eq("IN"),
            isNull(),
            any(),
            eq(2L),
            any());
  }

  @Test
  void unknownHoldIsNotReleasedOrCommittedAutomatically() {
    when(entitlement.integerValue(10L, ManagedSmsUsageService.MONTHLY_ENTITLEMENT))
        .thenReturn(10L);

    transaction.executeWithoutResult(status -> usage.reserve(10L, 60L, 70L, 1, 1));
    transaction.executeWithoutResult(status -> usage.holdUnknown(10L, 70L, 1));

    assertThat(
            jdbc.queryForObject(
                "select state from managed_sms_usage_reservations where recipient_id = 70",
                String.class))
        .isEqualTo("HELD_UNKNOWN");
    verifyNoInteractions(billing);
  }

  private Throwable reserveAfterBarrier(
      Long recipientId, CountDownLatch ready, CountDownLatch start) {
    try {
      ready.countDown();
      start.await(5, TimeUnit.SECONDS);
      transaction.executeWithoutResult(status -> usage.reserve(10L, 60L, recipientId, 1, 1));
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
