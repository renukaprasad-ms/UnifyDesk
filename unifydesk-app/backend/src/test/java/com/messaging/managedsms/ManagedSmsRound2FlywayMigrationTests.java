package com.messaging.managedsms;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ManagedSmsRound2FlywayMigrationTests {

  private static final String URL = "jdbc:postgresql://127.0.0.1:55432/messaging_test";
  private static final String USER = "test";
  private static final String PASSWORD = "test";

  private final JdbcTemplate admin =
      new JdbcTemplate(new DriverManagerDataSource(URL, USER, PASSWORD));

  @AfterEach
  void cleanup() {
    admin.execute("drop schema if exists qa_managed_sms_r2_clean cascade");
    admin.execute("drop schema if exists qa_managed_sms_r2_upgrade cascade");
  }

  @Test
  void cleanMigrationReachesV66() {
    String schema = "qa_managed_sms_r2_clean";
    Flyway flyway = flyway(schema, null);

    flyway.clean();
    flyway.migrate();

    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("66");
    assertThat(tableExists(schema, "managed_sms_compliance_actions")).isTrue();
    assertThat(columnExists(schema, "managed_sms_provider_events", "provider_event_at")).isTrue();
    assertThat(columnExists(schema, "managed_sms_provider_snapshots", "observation_source")).isTrue();
  }

  @Test
  void representativeUpgradeFromV64ReachesV66() {
    String schema = "qa_managed_sms_r2_upgrade";
    Flyway throughV64 = flyway(schema, MigrationVersion.fromVersion("64"));

    throughV64.clean();
    throughV64.migrate();
    assertThat(throughV64.info().current().getVersion().getVersion()).isEqualTo("64");

    Flyway throughV66 = flyway(schema, null);
    throughV66.migrate();

    assertThat(throughV66.info().current().getVersion().getVersion()).isEqualTo("66");
    assertThat(tableExists(schema, "managed_sms_compliance_actions")).isTrue();
    assertThat(columnExists(schema, "managed_sms_provider_events", "provider_event_at")).isTrue();
    assertThat(columnExists(schema, "managed_sms_provider_snapshots", "observation_source")).isTrue();
  }

  private Flyway flyway(String schema, MigrationVersion target) {
    var configuration =
        Flyway.configure()
            .dataSource(URL, USER, PASSWORD)
            .schemas(schema)
            .defaultSchema(schema)
            .createSchemas(true)
            .cleanDisabled(false);
    if (target != null) {
      configuration.target(target);
    }
    return configuration.load();
  }

  private boolean tableExists(String schema, String table) {
    Integer count =
        admin.queryForObject(
            """
            select count(*)
              from information_schema.tables
             where table_schema = ?
               and table_name = ?
            """,
            Integer.class,
            schema,
            table);
    return count != null && count == 1;
  }

  private boolean columnExists(String schema, String table, String column) {
    Integer count =
        admin.queryForObject(
            """
            select count(*)
              from information_schema.columns
             where table_schema = ?
               and table_name = ?
               and column_name = ?
            """,
            Integer.class,
            schema,
            table,
            column);
    return count != null && count == 1;
  }
}
