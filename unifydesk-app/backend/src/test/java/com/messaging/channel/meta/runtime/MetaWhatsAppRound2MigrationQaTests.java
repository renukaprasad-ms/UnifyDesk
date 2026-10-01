package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class MetaWhatsAppRound2MigrationQaTests {

  private static final String ADMIN_URL = "jdbc:postgresql://127.0.0.1:55432/postgres";
  private static final String USER = "test";
  private static final String PASSWORD = "test";

  @Test
  void cleanFlywayMigratesFromV1ThroughV79() throws Exception {
    withDatabase(
        "whatsapp_r2_clean",
        url -> {
          Flyway flyway = flyway(url, MigrationVersion.fromVersion("79"));
          flyway.clean();
          flyway.migrate();
          assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("79");
        });
  }

  @Test
  void representativeV75UpgradeReachesV79() throws Exception {
    withDatabase(
        "whatsapp_r2_from75",
        url -> {
          Flyway through75 = flyway(url, MigrationVersion.fromVersion("75"));
          through75.migrate();
          assertThat(through75.info().current().getVersion().getVersion()).isEqualTo("75");

          Flyway latest = flyway(url, MigrationVersion.fromVersion("79"));
          latest.migrate();
          assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("79");
        });
  }

  @Test
  void representativeV78UpgradeReachesV79() throws Exception {
    withDatabase(
        "whatsapp_r2_from78",
        url -> {
          Flyway through78 = flyway(url, MigrationVersion.fromVersion("78"));
          through78.migrate();
          assertThat(through78.info().current().getVersion().getVersion()).isEqualTo("78");

          Flyway latest = flyway(url, MigrationVersion.fromVersion("79"));
          latest.migrate();
          assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("79");
        });
  }

  private Flyway flyway(String url, MigrationVersion target) {
    var configuration =
        Flyway.configure()
            .dataSource(url, USER, PASSWORD)
            .locations("classpath:db/migration")
            .cleanDisabled(false);
    if (target != null) {
      configuration.target(target);
    }
    return configuration.load();
  }

  private void withDatabase(String database, DatabaseAssertion assertion) throws Exception {
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

  private void dropDatabase(String database) throws Exception {
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
