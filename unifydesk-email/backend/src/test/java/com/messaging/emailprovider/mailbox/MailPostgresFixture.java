package com.messaging.emailprovider.mailbox;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Owns one ephemeral schema, never modifies pre-existing tables. */
public final class MailPostgresFixture implements AutoCloseable {
  private final String url;
  private final String user;
  private final String password;
  private final String schema = "mail_qa_" + UUID.randomUUID().toString().replace("-", "");
  public final JdbcTemplate jdbc;
  public final TransactionTemplate tx;

  public MailPostgresFixture() throws Exception {
    url = System.getenv("MAIL_QA_PG_URL");
    assumeTrue(url != null && url.startsWith("jdbc:postgresql:"), "Requires disposable PostgreSQL");
    user = System.getenv().getOrDefault("MAIL_QA_PG_USER", "postgres");
    password = System.getenv().getOrDefault("MAIL_QA_PG_PASSWORD", "postgres");
    try (var connection = DriverManager.getConnection(url, user, password);
        var statement = connection.createStatement()) {
      statement.execute("CREATE SCHEMA " + schema);
    }
    try {
      Flyway.configure()
          .dataSource(url, user, password)
          .schemas(schema)
          .defaultSchema(schema)
          .load()
          .migrate();
      var source =
          new DriverManagerDataSource(
              url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, user, password);
      jdbc = new JdbcTemplate(source);
      tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    } catch (Exception failure) {
      close();
      throw failure;
    }
  }

  public void seed() {
    jdbc.update(
        """
        INSERT INTO email_product_organizations(id,name,contact_email,status,created_at,updated_at)
        VALUES (1,'QA','qa@example.test','ACTIVE',now(),now()),(2,'Other','other@example.test','ACTIVE',now(),now())
        """);
    jdbc.update(
        """
        INSERT INTO email_console_users(id,organization_id,email,display_name,role,status,created_at,updated_at)
        VALUES (11,1,'owner@example.test','Owner','OWNER','ACTIVE',now(),now()),
          (12,1,'member@example.test','Member','VIEWER','ACTIVE',now(),now()),
          (13,1,'admin@example.test','Admin','ADMIN','ACTIVE',now(),now()),
          (21,2,'other@example.test','Other','OWNER','ACTIVE',now(),now())
        """);
    jdbc.update(
        """
        INSERT INTO email_console_sessions(id,user_id,token_hash,expires_at,created_at,updated_at)
        VALUES (101,11,repeat('a',64),now()+interval '1 hour',now(),now()),
          (102,12,repeat('b',64),now()+interval '1 hour',now(),now()),
          (103,13,repeat('c',64),now()+interval '1 hour',now(),now()),
          (201,21,repeat('d',64),now()+interval '1 hour',now(),now())
        """);
    jdbc.update(
        """
        INSERT INTO email_mail_domains(id,organization_id,domain_name,status,inbound_enabled)
        VALUES (31,1,'example.test','VERIFIED',TRUE),(32,2,'other.test','VERIFIED',TRUE)
        """);
    jdbc.update(
        """
        INSERT INTO email_mailboxes(id,organization_id,domain_id,local_part,status)
        VALUES(41,1,31,'owner','ACTIVE'),(42,2,32,'owner','ACTIVE')
        """);
    jdbc.update(
        """
        INSERT INTO email_mailbox_access(organization_id,mailbox_id,user_id,can_delegate)
        VALUES(1,41,11,TRUE),(2,42,21,TRUE)
        """);
  }

  @Override
  public void close() throws Exception {
    try (var connection = DriverManager.getConnection(url, user, password);
        var statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
  }
}
