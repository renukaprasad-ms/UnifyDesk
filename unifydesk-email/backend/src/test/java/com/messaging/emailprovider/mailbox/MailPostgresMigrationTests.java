package com.messaging.emailprovider.mailbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

/**
 * Real PostgreSQL Flyway tests, executed by mail-qa.yml with a disposable DB. The normal unit suite
 * skips these checks when MAIL_QA_PG_URL is absent.
 */
class MailPostgresMigrationTests {
  @Test
  void newDatabaseAndUpgradePathsApplyV1ThroughV22() throws Exception {
    String url = System.getenv("MAIL_QA_PG_URL");
    assumeTrue(
        url != null && url.startsWith("jdbc:postgresql:"),
        "Requires a disposable local PostgreSQL instance");
    String user = System.getenv().getOrDefault("MAIL_QA_PG_USER", "postgres");
    String password = System.getenv().getOrDefault("MAIL_QA_PG_PASSWORD", "postgres");
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    for (String starting : new String[] {"0", "13", "16", "19", "21"}) {
      String schema = "mail_qa_" + suffix + "_" + starting;
      try (Connection connection = DriverManager.getConnection(url, user, password);
          Statement statement = connection.createStatement()) {
        statement.execute("CREATE SCHEMA " + schema);
      }
      try {
        if (!"0".equals(starting)) {
          Flyway.configure()
              .dataSource(url, user, password)
              .schemas(schema)
              .defaultSchema(schema)
              .target(MigrationVersion.fromVersion(starting))
              .load()
              .migrate();
        }
        Flyway flyway =
            Flyway.configure()
                .dataSource(url, user, password)
                .schemas(schema)
                .defaultSchema(schema)
                .load();
        flyway.migrate();
        flyway.validate();
        try (Connection connection = DriverManager.getConnection(url, user, password);
            Statement statement = connection.createStatement()) {
          statement.execute("SET search_path TO " + schema);
          try (ResultSet result =
              statement.executeQuery(
                  "SELECT count(*) FROM flyway_schema_history WHERE success=true")) {
            assertTrue(result.next());
            assertEquals(22, result.getInt(1));
          }
          try (ResultSet result =
              statement.executeQuery(
                  """
              SELECT is_nullable,column_default
              FROM information_schema.columns
              WHERE table_schema=current_schema()
                AND table_name='email_mail_domains'
                AND column_name='inbound_enabled'
              """)) {
            assertTrue(result.next());
            assertEquals("NO", result.getString(1));
            assertTrue(result.getString(2).contains("false"));
          }
          try (ResultSet result =
              statement.executeQuery(
                  """
              SELECT count(*) FROM information_schema.tables
              WHERE table_schema=current_schema()
                AND table_name IN (
                  'email_mailboxes','email_mail_inbound_jobs',
                  'email_mail_outbound_intents','email_mail_outbound_recipients',
                  'email_mail_outbound_feedback','email_mail_outbound_suppressions')
              """)) {
            assertTrue(result.next());
            assertEquals(6, result.getInt(1));
          }
        }
      } finally {
        try (Connection connection = DriverManager.getConnection(url, user, password);
            Statement statement = connection.createStatement()) {
          statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
      }
    }
  }
}
