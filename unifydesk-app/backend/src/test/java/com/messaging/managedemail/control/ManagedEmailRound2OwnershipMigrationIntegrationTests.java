package com.messaging.managedemail.control;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ManagedEmailRound2OwnershipMigrationIntegrationTests {

  private static Connection connection;
  private static String schema;

  @BeforeAll
  static void setUp() throws Exception {
    String url =
        System.getenv()
            .getOrDefault(
                "MANAGED_EMAIL_TEST_DB_URL",
                "jdbc:postgresql://localhost:55432/messaging_test");
    String user = System.getenv().getOrDefault("MANAGED_EMAIL_TEST_DB_USER", "test");
    String password = System.getenv().getOrDefault("MANAGED_EMAIL_TEST_DB_PASSWORD", "test");
    try {
      connection = DriverManager.getConnection(url, user, password);
    } catch (SQLException exception) {
      Assumptions.assumeTrue(false, "PostgreSQL integration service unavailable: " + exception);
      return;
    }

    schema = "me_r2_" + Long.toUnsignedString(System.nanoTime());
    execute("create schema " + schema);
    execute("set search_path to " + schema);
    createModeledV58Schema();
    applyV59();
    seedConsistentOwnershipGraph();
  }

  @AfterAll
  static void tearDown() throws Exception {
    if (connection == null) return;
    try (Statement statement = connection.createStatement()) {
      statement.execute("drop schema if exists " + schema + " cascade");
    } finally {
      connection.close();
    }
  }

  @Test
  void domainCannotReferenceTenantFromAnotherCompany() {
    assertSqlRejected(
        """
        insert into managed_email_domains (id, company_id, tenant_id)
        values (203, 2, 101)
        """);
  }

  @Test
  void dnsRecordCannotReferenceDomainFromAnotherCompany() {
    assertSqlRejected(
        """
        insert into managed_email_dns_records (id, domain_id, company_id)
        values (601, 201, 2)
        """);
  }

  @Test
  void senderBindingCannotMixSenderDomainOrTenantOwnership() {
    assertSqlRejected(
        """
        insert into managed_email_sender_bindings (sender_id, company_id, domain_id, tenant_id)
        values (301, 2, 202, 102)
        """);
    assertSqlRejected(
        """
        insert into managed_email_sender_bindings (sender_id, company_id, domain_id, tenant_id)
        values (302, 2, 201, 102)
        """);
    assertSqlRejected(
        """
        insert into managed_email_sender_bindings (sender_id, company_id, domain_id, tenant_id)
        values (302, 2, 202, 101)
        """);
  }

  @Test
  void usageEventCannotMixCompanyRunAndRecipientOwnership() {
    assertSqlRejected(
        """
        insert into managed_email_usage_events (id, company_id, recipient_id, run_id)
        values (701, 2, 501, 401)
        """);
    assertSqlRejected(
        """
        insert into managed_email_usage_events (id, company_id, recipient_id, run_id)
        values (702, 1, 502, 401)
        """);
  }

  private static void createModeledV58Schema() throws SQLException {
    execute("create table companies (id bigint primary key)");
    execute("create table users (id bigint primary key)");
    execute(
        """
        create table managed_email_tenants (
          id bigint primary key,
          company_id bigint not null,
          constraint uk_managed_email_tenants_id_company unique (id, company_id)
        )
        """);
    execute(
        """
        create table managed_email_domains (
          id bigint primary key,
          company_id bigint not null,
          tenant_id bigint not null,
          constraint uk_managed_email_domains_id_company unique (id, company_id)
        )
        """);
    execute(
        """
        create table managed_email_dns_records (
          id bigint primary key,
          domain_id bigint not null,
          company_id bigint not null
        )
        """);
    execute(
        """
        create table broadcast_senders (
          id bigint primary key,
          company_id bigint not null
        )
        """);
    execute(
        """
        create table managed_email_sender_bindings (
          sender_id bigint primary key,
          company_id bigint not null,
          domain_id bigint not null,
          tenant_id bigint not null,
          constraint uk_managed_email_sender_bindings_sender_company
            unique (sender_id, company_id)
        )
        """);
    execute(
        """
        create table broadcast_runs (
          id bigint primary key,
          company_id bigint not null
        )
        """);
    execute(
        """
        create table broadcast_recipients (
          id bigint primary key,
          run_id bigint not null,
          company_id bigint not null
        )
        """);
    execute(
        """
        create table managed_email_usage_events (
          id bigint primary key,
          company_id bigint not null,
          recipient_id bigint not null,
          run_id bigint not null
        )
        """);
  }

  private static void applyV59() throws Exception {
    try (var stream =
        ManagedEmailRound2OwnershipMigrationIntegrationTests.class
            .getClassLoader()
            .getResourceAsStream(
                "db/migration/V59__managed_email_round2_control_security.sql")) {
      if (stream == null) throw new IllegalStateException("V59 migration not found");
      String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      for (String statement : sql.split(";")) {
        if (!statement.isBlank()) execute(statement);
      }
    }
  }

  private static void seedConsistentOwnershipGraph() throws SQLException {
    execute("insert into companies (id) values (1), (2)");
    execute("insert into users (id) values (7)");
    execute("insert into managed_email_tenants (id, company_id) values (101, 1), (102, 2)");
    execute(
        "insert into managed_email_domains (id, company_id, tenant_id) values (201, 1, 101), (202, 2, 102)");
    execute("insert into broadcast_senders (id, company_id) values (301, 1), (302, 2)");
    execute("insert into broadcast_runs (id, company_id) values (401, 1), (402, 2)");
    execute(
        "insert into broadcast_recipients (id, run_id, company_id) values (501, 401, 1), (502, 402, 2)");
  }

  private static void assertSqlRejected(String sql) {
    assertThatThrownBy(() -> execute(sql)).isInstanceOf(SQLException.class);
  }

  private static void execute(String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
