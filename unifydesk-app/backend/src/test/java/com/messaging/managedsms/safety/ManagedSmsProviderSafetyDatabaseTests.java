package com.messaging.managedsms.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ManagedSmsProviderSafetyDatabaseTests {

  private static JdbcTemplate admin;
  private JdbcTemplate jdbc;
  private ManagedSmsRuntimeProperties properties;
  private FakeObservationAdapter adapter;
  private ManagedSmsProviderObservationService observations;
  private ManagedSmsProviderSafetyService safety;

  @BeforeAll
  static void createSchema() {
    admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:55432/messaging_test", "test", "test"));
    admin.execute("drop schema if exists b2_managed_sms_safety_test cascade");
    admin.execute("create schema b2_managed_sms_safety_test");
  }

  @AfterAll
  static void dropSchema() {
    if (admin != null) {
      admin.execute("drop schema if exists b2_managed_sms_safety_test cascade");
    }
  }

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            "jdbc:postgresql://127.0.0.1:55432/messaging_test"
                + "?currentSchema=b2_managed_sms_safety_test",
            "test",
            "test");
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute(
        """
        create table if not exists managed_sms_provider_snapshots (
          id bigint primary key,
          provider_account_key varchar(255) not null,
          provider_client_ref varchar(255),
          balance numeric(18,6),
          credit_limit numeric(18,6),
          max_send_rate numeric(18,6),
          sending_enabled boolean not null,
          observed_at timestamptz not null,
          valid_until timestamptz not null,
          observation_source varchar(80),
          unique(provider_account_key, observed_at)
        )
        """);
    jdbc.update("delete from managed_sms_provider_snapshots");

    properties = new ManagedSmsRuntimeProperties();
    properties.setProviderBalanceReserve(new BigDecimal("100"));
    adapter = new FakeObservationAdapter();
    observations = new ManagedSmsProviderObservationService(jdbc, adapter,
        new com.messaging.managedsms.metareach.MetaReachProperties());
    safety = new ManagedSmsProviderSafetyService(jdbc, properties);
  }

  @Test
  void cleanDatabaseAndManuallySeededHealthyRowFailClosed() {
    assertThat(safety.isAvailable("account-a")).isFalse();
    properties.setProviderBalanceReserve(BigDecimal.ZERO);
    assertThat(safety.isAvailable("account-a")).isFalse();
    properties.setProviderBalanceReserve(new BigDecimal("100"));

    Instant now = Instant.now();
    jdbc.update(
        """
        insert into managed_sms_provider_snapshots (
          id, provider_account_key, balance, sending_enabled, observed_at, valid_until
        ) values (?, 'account-a', 500, true, ?, ?)
        """,
        999998L,
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now.plusSeconds(300)));

    assertThat(safety.isAvailable("account-a")).isFalse();
    assertThat(safety.isAvailable("account-b")).isFalse();
  }

  @Test
  void freshHealthyObservationEnablesOnlyItsAccount() {
    adapter.observation =
        observation("account-a", "200", true, Instant.now().minusSeconds(2));
    assertThat(observations.refresh("account-a", "client-a")).isTrue();

    assertThat(safety.isAvailable("account-a")).isTrue();
    assertThat(safety.isAvailable("account-b")).isFalse();
  }

  @Test
  void lowDisabledAndExpiredLatestSnapshotsFailClosed() {
    Instant base = Instant.now().minusSeconds(20);
    adapter.observation = observation("account-a", "50", true, base);
    assertThat(observations.refresh("account-a", "client-a")).isTrue();
    assertThat(safety.isAvailable("account-a")).isFalse();

    properties.setProviderBalanceReserve(BigDecimal.ZERO);
    adapter.observation = observation("account-a", "500", false, base.plusSeconds(1));
    assertThat(observations.refresh("account-a", "client-a")).isTrue();
    assertThat(safety.isAvailable("account-a")).isFalse();
    properties.setProviderBalanceReserve(new BigDecimal("100"));

    jdbc.update(
        """
        insert into managed_sms_provider_snapshots (
          id, provider_account_key, balance, sending_enabled, observed_at, valid_until,
          observation_source
        ) values (?, 'account-a', 500, true, ?, ?, 'VERIFIED_ADAPTER:EXPIRED_TEST')
        """,
        999999L,
        java.sql.Timestamp.from(base.plusSeconds(2)),
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
    assertThat(safety.isAvailable("account-a")).isFalse();
  }

  @Test
  void insufficientCreditTripRequiresNewerHealthyObservationForRecovery() {
    Instant base = Instant.now().minusSeconds(5);
    adapter.observation = observation("account-a", "500", true, base);
    assertThat(observations.refresh("account-a", "client-a")).isTrue();
    assertThat(safety.isAvailable("account-a")).isTrue();

    safety.tripInsufficientCredit("account-a");
    assertThat(safety.isAvailable("account-a")).isFalse();

    adapter.observation =
        observation("account-a", "500", true, Instant.now().plusMillis(10));
    assertThat(observations.refresh("account-a", "client-a")).isTrue();
    assertThat(safety.isAvailable("account-a")).isTrue();
  }

  private ManagedSmsProviderObservationAdapter.ProviderObservation observation(
      String account, String balance, boolean enabled, Instant observedAt) {
    return new ManagedSmsProviderObservationAdapter.ProviderObservation(
        account,
        "client-a",
        new BigDecimal(balance),
        null,
        new BigDecimal("15"),
        enabled,
        observedAt,
        observedAt.plusSeconds(300),
        "FAKE_VERIFIED_OBSERVER");
  }

  private static final class FakeObservationAdapter
      implements ManagedSmsProviderObservationAdapter {
    private ProviderObservation observation;

    @Override
    public Optional<ProviderObservation> observe(
        String providerAccountKey, String providerClientRef) {
      return Optional.ofNullable(observation);
    }
  }
}
