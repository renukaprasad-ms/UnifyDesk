package com.messaging.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
  private static final MigrationVersion CURRENT_HEAD = MigrationVersion.fromVersion("91");

  @Test
  void cleanDatabaseMigratesToV91AndHibernateValidatesSchema() throws Exception {
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
  void representativeV79DatabaseUpgradesToV91() throws Exception {
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
