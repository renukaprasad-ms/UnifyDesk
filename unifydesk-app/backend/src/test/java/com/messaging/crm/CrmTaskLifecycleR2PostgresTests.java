package com.messaging.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.messaging.common.exception.ApiException;
import com.messaging.notification.platform.core.event.NotificationEventRecorder;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

class CrmTaskLifecycleR2PostgresTests {
  private static final String URL = "jdbc:postgresql://127.0.0.1:55432/messaging_test";
  private static final String USER = "test";
  private static final String PASSWORD = "test";
  private String schema;

  @AfterEach
  void cleanup() {
    if (schema != null) {
      new JdbcTemplate(new DriverManagerDataSource(URL, USER, PASSWORD))
          .execute("drop schema if exists " + schema + " cascade");
    }
  }

  @Test
  void deactivatedMemberReassignsOpenWorkButPreservesClosedTaskAudit() {
    JdbcTemplate jdbc = setup();
    jdbc.update("""
        insert into crm_accounts(id,company_id,name,normalized_name,owner_user_id)
        values (301,101,'Acme','acme',2)
        """);
    jdbc.update("""
        insert into crm_tasks(id,company_id,title,assignee_user_id,status)
        values (401,101,'Open follow-up',2,'OPEN')
        """);
    jdbc.update("""
        insert into crm_tasks(id,company_id,title,assignee_user_id,status,completed_at)
        values (402,101,'Closed follow-up',2,'DONE',now())
        """);
    jdbc.update(
        "update company_members set status='SUSPENDED' where company_id=101 and user_id=2");
    new CrmTaskMembershipLifecycle(jdbc).onMemberDeactivated(101, 2);

    assertThat(jdbc.queryForObject(
        "select assignee_user_id from crm_tasks where id=401", Long.class)).isEqualTo(1L);
    assertThat(jdbc.queryForObject(
        "select reminder_generation from crm_tasks where id=401", Long.class)).isEqualTo(1L);
    assertThat(jdbc.queryForObject(
        "select assignee_user_id from crm_tasks where id=402", Long.class)).isEqualTo(2L);
    assertThat(jdbc.queryForObject(
        "select owner_user_id from crm_accounts where id=301", Long.class)).isNull();
  }

  @Test
  void missingOwnerFailsClosedRatherThanAbandoningOpenWork() {
    JdbcTemplate jdbc = setup();
    jdbc.update("""
        insert into crm_tasks(id,company_id,title,assignee_user_id)
        values (401,101,'Unassigned follow-up',2)
        """);
    jdbc.update(
        "update company_members set status='SUSPENDED' where company_id=101");
    assertThatThrownBy(
        () -> new CrmTaskMembershipLifecycle(jdbc).onMemberDeactivated(101, 2))
        .isInstanceOf(ApiException.class);
    assertThat(jdbc.queryForObject(
        "select assignee_user_id from crm_tasks where id=401", Long.class)).isEqualTo(2L);
  }

  @Test
  void dueReminderIsClaimedOnceAndNotRepeatedOnNextScan() {
    JdbcTemplate jdbc = setup();
    jdbc.update("""
        insert into crm_tasks(id,company_id,title,assignee_user_id,due_at)
        values (401,101,'Call customer',1,now() + interval '5 minutes')
        """);
    NotificationEventRecorder events = mock(NotificationEventRecorder.class);
    CrmTaskReminderScheduler scheduler = new CrmTaskReminderScheduler(jdbc, events);
    scheduler.scan();
    scheduler.scan();

    verify(events, times(1)).recordCrmTaskDue(401, 101, 1, 0, "Call customer");
    verifyNoMoreInteractions(events);
    assertThat(jdbc.queryForObject(
        "select reminded_generation from crm_tasks where id=401", Long.class))
        .isZero();
  }

  private JdbcTemplate setup() {
    schema = "qa_crm_r2_" + UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(URL, USER, PASSWORD)
        .schemas(schema)
        .defaultSchema(schema)
        .createSchemas(true)
        .cleanDisabled(false)
        .target(MigrationVersion.fromVersion("110"))
        .load()
        .migrate();
    JdbcTemplate jdbc = new JdbcTemplate(new DelegatingDataSource(
        new DriverManagerDataSource(URL, USER, PASSWORD)) {
      @Override
      public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        try (var statement = connection.createStatement()) {
          statement.execute("set search_path to " + schema);
        }
        return connection;
      }
    });
    jdbc.update("""
        insert into users(id,email,name,is_verified,username,password,two_factor_enabled)
        values (1,'crm-owner@example.com','Owner',true,'crm-owner','x',false),
               (2,'crm-member@example.com','Member',true,'crm-member','x',false)
        """);
    jdbc.update("""
        insert into companies(id,name,slug,status,created_by_user_id,created_at,updated_at)
        values (101,'Tenant A','crm-a','ACTIVE',1,now(),now())
        """);
    jdbc.update("""
        insert into company_members(id,company_id,user_id,role,status,created_at,updated_at)
        values (701,101,1,'OWNER','ACTIVE',now(),now()),
               (702,101,2,'MEMBER','ACTIVE',now(),now())
        """);
    return jdbc;
  }
}
