package com.messaging.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

class PlatformMigrationHardeningTests {

  private static final String ADMIN_URL = "jdbc:postgresql://127.0.0.1:55432/postgres";
  private static final String USER = "test";
  private static final String PASSWORD = "test";
  private static final MigrationVersion CURRENT_HEAD = MigrationVersion.fromVersion("95");

  @Test
  void cleanDatabaseMigratesToV95AndHibernateValidatesSchema() throws Exception {
    withDatabase(
        "platform_clean",
        url -> {
          Flyway flyway = flyway(url, null, false, "0");
          flyway.migrate();

          assertThat(flyway.info().current().getVersion()).isEqualTo(CURRENT_HEAD);
          validateHibernateSchema(url);
        });
  }

  @Test
  void representativeV79DatabaseUpgradesToV95() throws Exception {
    withDatabase(
        "platform_from79",
        url -> {
          Flyway through79 = flyway(url, MigrationVersion.fromVersion("79"), false, "0");
          through79.migrate();
          assertThat(through79.info().current().getVersion().getVersion()).isEqualTo("79");

          Flyway latest = flyway(url, null, false, "0");
          latest.migrate();

          assertThat(latest.info().current().getVersion()).isEqualTo(CURRENT_HEAD);
          validateHibernateSchema(url);
        });
  }

  @Test
  void unmanagedNonEmptyV1EquivalentSchemaFailsClosedByDefault() throws Exception {
    withDatabase(
        "platform_unmanaged",
        url -> {
          createV1EquivalentLegacySchema(url);

          Flyway defaultSafe = flyway(url, null, false, "1");

          assertThatThrownBy(defaultSafe::migrate).isInstanceOf(FlywayException.class);
        });
  }

  @Test
  void explicitBaselineAtKnownV1CanAdoptCompatibleLegacySchemaAndMigrateForward()
      throws Exception {
    withDatabase(
        "platform_adopt_v1",
        url -> {
          createV1EquivalentLegacySchema(url);

          Flyway adoption = flyway(url, null, true, "1");
          adoption.migrate();

          assertThat(adoption.info().current().getVersion()).isEqualTo(CURRENT_HEAD);
          assertThat(baselineVersion(url)).isEqualTo("1");
          validateHibernateSchema(url);
        });
  }

  @Test
  void billingCommercialCoreDatabaseConstraintsArePresent() throws Exception {
    withDatabase(
        "billing_r1_constraints",
        url -> {
          Flyway flyway = flyway(url, null, false, "0");
          flyway.migrate();

          Set<String> constraints = new HashSet<>();
          try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD);
              var statement =
                  connection.prepareStatement(
                      """
                      select conname
                      from pg_constraint
                      where conname in (
                        'uk_subscription_changes_company_idempotency',
                        'uk_invoices_billing_period',
                        'uk_invoice_line_items_idempotency',
                        'uk_billing_period_company_kind_range',
                        'uk_invoices_subscription_change',
                        'uk_subscription_checkout_intents_company_idempotency',
                        'uk_subscription_checkout_intents_billing_period',
                        'uk_subscription_checkout_intents_invoice',
                        'uk_subscription_checkout_intents_subscription'
                      )
                      """);
              var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
              constraints.add(resultSet.getString(1));
            }
          }

          assertThat(constraints)
              .containsExactlyInAnyOrder(
                  "uk_subscription_changes_company_idempotency",
                  "uk_invoices_billing_period",
                  "uk_invoice_line_items_idempotency",
                  "uk_billing_period_company_kind_range",
                  "uk_invoices_subscription_change",
                  "uk_subscription_checkout_intents_company_idempotency",
                  "uk_subscription_checkout_intents_billing_period",
                  "uk_subscription_checkout_intents_invoice",
                  "uk_subscription_checkout_intents_subscription");
        });
  }

  @Test
  void authoritativeScheduledDowngradeIndexRejectsConcurrentReplicaRace() throws Exception {
    withDatabase(
        "billing_r1_scheduled_downgrade",
        url -> {
          flyway(url, null, false, "0").migrate();
          seedScheduledDowngradeFixture(url);

          Instant boundary = Instant.parse("2026-11-01T00:00:00Z");
          try (Connection winner = DriverManager.getConnection(url, USER, PASSWORD);
              Connection loser = DriverManager.getConnection(url, USER, PASSWORD)) {
            winner.setAutoCommit(false);
            loser.setAutoCommit(false);
            insertScheduledDowngrade(winner, 600L, "winner", boundary);

            try (var executor = Executors.newSingleThreadExecutor()) {
              var losingInsert =
                  executor.submit(
                      () -> {
                        insertScheduledDowngrade(loser, 601L, "loser", boundary);
                        loser.commit();
                        return null;
                      });

              Thread.sleep(100);
              winner.commit();

              assertThatThrownBy(() -> losingInsert.get(5, TimeUnit.SECONDS))
                  .hasCauseInstanceOf(SQLException.class);
              loser.rollback();
            }
          }

          try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD);
              var statement =
                  connection.prepareStatement(
                      """
                      select count(*)
                        from subscription_changes
                       where subscription_id = 400
                         and change_type = 'DOWNGRADE'
                         and status = 'SCHEDULED'
                         and effective_at = ?
                      """)) {
            statement.setObject(1, java.time.OffsetDateTime.parse("2026-11-01T00:00:00Z"));
            try (var rs = statement.executeQuery()) {
              assertThat(rs.next()).isTrue();
              assertThat(rs.getInt(1)).isEqualTo(1);
            }
          }
        });
  }

  @Test
  void authoritativeScheduledDowngradePartialUniqueIndexIsPresent() throws Exception {
    withDatabase(
        "billing_r1_scheduled_downgrade_index",
        url -> {
          flyway(url, null, false, "0").migrate();
          try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD);
              var statement =
                  connection.prepareStatement(
                      """
                      select indexdef
                        from pg_indexes
                       where indexname = 'uk_subscription_changes_scheduled_downgrade_boundary'
                      """);
              var rs = statement.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1))
                .contains("(subscription_id, effective_at)")
                .contains("change_type")
                .contains("DOWNGRADE")
                .contains("SCHEDULED");
          }
        });
  }

  private static void seedScheduledDowngradeFixture(String url) throws Exception {
    try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD);
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into companies
            (id, name, slug, business_email, country, timezone, status, created_by_user_id, created_at, updated_at)
          values
            (100, 'Billing Test', 'billing-test-final-003', 'billing@example.com', 'IN', 'UTC', 'ACTIVE', 1, now(), now())
          """);
      statement.execute(
          """
          insert into plans
            (id, code, name, status, billing_cycle, base_price, currency, version, created_at, updated_at)
          values
            (200, 'CURRENT', 'Current', 'PUBLISHED', 'MONTHLY', 999, 'INR', 1, now(), now()),
            (201, 'TARGET', 'Target', 'PUBLISHED', 'MONTHLY', 499, 'INR', 1, now(), now())
          """);
      statement.execute(
          """
          insert into plan_prices
            (id, plan_id, billing_cycle, amount, currency, effective_from, status, created_at, updated_at)
          values
            (300, 200, 'MONTHLY', 999, 'INR', '2026-01-01T00:00:00Z', 'ACTIVE', now(), now()),
            (301, 201, 'MONTHLY', 499, 'INR', '2026-01-01T00:00:00Z', 'ACTIVE', now(), now())
          """);
      statement.execute(
          """
          insert into company_subscriptions
            (id, company_id, plan_id, plan_price_id, status, started_at, current_period_start,
             current_period_end, cancel_at_period_end, created_at, updated_at)
          values
            (400, 100, 200, 300, 'ACTIVE', '2026-10-01T00:00:00Z',
             '2026-10-01T00:00:00Z', '2026-11-01T00:00:00Z', false, now(), now())
          """);
    }
  }

  private static void insertScheduledDowngrade(
      Connection connection, long id, String idempotencyKey, Instant boundary) throws SQLException {
    try (var statement =
        connection.prepareStatement(
            """
            insert into subscription_changes
              (id, company_id, subscription_id, from_plan_id, to_plan_id, from_plan_price_id,
               to_plan_price_id, change_type, status, requested_at, effective_at, idempotency_key,
               currency, current_price_snapshot, target_price_snapshot, current_period_start,
               current_period_end, net_amount, amount_due, credit_amount, created_at, updated_at)
            values
              (?, 100, 400, 200, 201, 300, 301, 'DOWNGRADE', 'SCHEDULED',
               '2026-10-15T00:00:00Z', ?, ?, 'INR', 999, 499,
               '2026-10-01T00:00:00Z', '2026-11-01T00:00:00Z', 0, 0, 0, now(), now())
            """)) {
      statement.setLong(1, id);
      statement.setObject(2, java.time.OffsetDateTime.ofInstant(boundary, java.time.ZoneOffset.UTC));
      statement.setString(3, idempotencyKey);
      statement.executeUpdate();
    }
  }

  private static void createV1EquivalentLegacySchema(String url) throws Exception {
    Flyway throughV1 = flyway(url, MigrationVersion.fromVersion("1"), false, "0");
    throughV1.migrate();
    assertThat(throughV1.info().current().getVersion().getVersion()).isEqualTo("1");

    try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD);
        Statement statement = connection.createStatement()) {
      statement.execute("drop table flyway_schema_history");
    }
  }

  private static Flyway flyway(
      String url, MigrationVersion target, boolean baselineOnMigrate, String baselineVersion) {
    var configuration =
        Flyway.configure()
            .dataSource(url, USER, PASSWORD)
            .locations("classpath:db/migration")
            .baselineOnMigrate(baselineOnMigrate)
            .baselineVersion(baselineVersion);
    if (target != null) {
      configuration.target(target);
    }
    return configuration.load();
  }

  private static void validateHibernateSchema(String url) {
    DataSource dataSource = new DriverManagerDataSource(url, USER, PASSWORD);
    LocalContainerEntityManagerFactoryBean entityManagerFactory =
        new LocalContainerEntityManagerFactoryBean();
    entityManagerFactory.setDataSource(dataSource);
    entityManagerFactory.setPackagesToScan("com.messaging");
    entityManagerFactory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    entityManagerFactory.setJpaPropertyMap(
        Map.of(
            "hibernate.hbm2ddl.auto", "validate",
            "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect",
            "hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));

    entityManagerFactory.afterPropertiesSet();
    try {
      assertThat(entityManagerFactory.getObject()).isNotNull();
    } finally {
      entityManagerFactory.destroy();
    }
  }

  private static String baselineVersion(String url) throws Exception {
    try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD);
        var statement =
            connection.prepareStatement(
                "select version from flyway_schema_history where type = 'BASELINE' limit 1");
        var resultSet = statement.executeQuery()) {
      assertThat(resultSet.next()).isTrue();
      return resultSet.getString(1);
    }
  }

  private static void withDatabase(String prefix, DatabaseAssertion assertion) throws Exception {
    String database =
        prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    dropDatabase(database);
    try (Connection admin = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
        Statement statement = admin.createStatement()) {
      statement.execute("create database " + database);
    }
    try {
      assertion.run("jdbc:postgresql://127.0.0.1:55432/" + database);
    } finally {
      dropDatabase(database);
    }
  }

  private static void dropDatabase(String database) throws Exception {
    try (Connection admin = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
        Statement statement = admin.createStatement()) {
      statement.execute("drop database if exists " + database + " with (force)");
    }
  }

  @FunctionalInterface
  private interface DatabaseAssertion {
    void run(String url) throws Exception;
  }
}
