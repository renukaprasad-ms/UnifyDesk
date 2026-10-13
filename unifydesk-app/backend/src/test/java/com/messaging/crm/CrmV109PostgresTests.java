package com.messaging.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CrmV109PostgresTests {
  private static final String URL = "jdbc:postgresql://127.0.0.1:55432/messaging_test";
  private static final String USER = "test";
  private static final String PASSWORD = "test";
  private final JdbcTemplate admin =
      new JdbcTemplate(new DriverManagerDataSource(URL, USER, PASSWORD));

  @AfterEach
  void cleanup() {
    List<String> schemas =
        admin.queryForList(
            "select schema_name from information_schema.schemata where schema_name like 'qa_crm_%'",
            String.class);
    for (String schema : schemas) {
      admin.execute("drop schema if exists " + schema + " cascade");
    }
  }

  @Test
  void upgradeFromV107ToV109CreatesCrmTables() {
    String schema = schema();
    flyway(schema, "107").migrate();
    Flyway upgrade = flyway(schema, "109");
    upgrade.migrate();
    assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("109");
    assertThat(tableExists(schema, "crm_leads")).isTrue();
    assertThat(tableExists(schema, "crm_lead_stage_events")).isTrue();
    assertThat(tableExists(schema, "crm_accounts")).isTrue();
    assertThat(tableExists(schema, "crm_account_contacts")).isTrue();
    assertThat(tableExists(schema, "crm_tasks")).isTrue();
  }

  @Test
  void compositeForeignKeysPreventCrossTenantContactLinkage() {
    String schema = schema();
    flyway(schema, "114").migrate();
    JdbcTemplate jdbc = new JdbcTemplate(scoped(schema));
    seed(jdbc);
    jdbc.update(
        """
        insert into crm_accounts(id,company_id,name,normalized_name)
        values (301,101,'Customer A','customer a')
        """);
    jdbc.update(
        """
        insert into crm_account_contacts(company_id,account_id,contact_id)
        values (101,301,201)
        """);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
        insert into crm_account_contacts(company_id,account_id,contact_id)
        values (101,301,203)
        """))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from crm_account_contacts where company_id=101", Long.class))
        .isEqualTo(1L);
  }

  @Test
  void staleTaskVersionCannotOverwriteLatestStatus() {
    String schema = schema();
    flyway(schema, "114").migrate();
    JdbcTemplate jdbc = new JdbcTemplate(scoped(schema));
    seed(jdbc);
    jdbc.update(
        """
        insert into crm_tasks(id,company_id,title,assignee_user_id)
        values (401,101,'Follow up',1)
        """);
    assertThat(
            jdbc.update(
                """
        update crm_tasks set status='IN_PROGRESS',version=version+1
        where id=401 and company_id=101 and version=0
        """))
        .isEqualTo(1);
    assertThat(
            jdbc.update(
                """
        update crm_tasks set status='DONE',completed_at=now(),version=version+1
        where id=401 and company_id=101 and version=0
        """))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select status from crm_tasks where id=401 and company_id=101", String.class))
        .isEqualTo("IN_PROGRESS");
  }

  @Test
  void contactMergePreservesLeadHistoryDeduplicatesAccountsAndMovesTasks() {
    String schema = schema();
    flyway(schema, "114").migrate();
    JdbcTemplate jdbc = new JdbcTemplate(scoped(schema));
    seed(jdbc);
    jdbc.update(
        """
        insert into contacts(id,company_id,display_name,status,created_by_user_id,created_at,updated_at,version)
        values (202,101,'Source','ACTIVE',1,now(),now(),0)
        """);
    jdbc.update(
        """
        insert into crm_leads(company_id,contact_id,stage,created_by_user_id)
        values (101,201,'CONTACTED',1),(101,202,'QUALIFIED',1)
        """);
    jdbc.update(
        """
        insert into crm_lead_stage_events(id,company_id,contact_id,new_stage,actor_user_id)
        values (501,101,202,'QUALIFIED',1)
        """);
    jdbc.update(
        """
        insert into crm_accounts(id,company_id,name,normalized_name)
        values (301,101,'Account','account')
        """);
    jdbc.update(
        """
        insert into crm_account_contacts(company_id,account_id,contact_id)
        values (101,301,201),(101,301,202)
        """);
    jdbc.update(
        """
        insert into crm_tasks(id,company_id,title,assignee_user_id,contact_id)
        values (401,101,'Call customer',1,202)
        """);

    new CrmContactMergeExtension(jdbc).reconcile(101, 202, 201, 1);

    assertThat(
            jdbc.queryForObject(
                "select stage from crm_leads where company_id=101 and contact_id=201",
                String.class))
        .isEqualTo("CONTACTED");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from crm_leads where company_id=101 and contact_id=202",
                Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select contact_id from crm_lead_stage_events where id=501", Long.class))
        .isEqualTo(201L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from crm_account_contacts where company_id=101 and account_id=301",
                Long.class))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select contact_id from crm_tasks where company_id=101 and id=401", Long.class))
        .isEqualTo(201L);
  }

  @Test
  void contactMergeTransfersLeadWhenTargetHasNoLead() {
    String schema = schema();
    flyway(schema, "114").migrate();
    JdbcTemplate jdbc = new JdbcTemplate(scoped(schema));
    seed(jdbc);
    jdbc.update(
        """
        insert into contacts(id,company_id,display_name,status,created_by_user_id,created_at,updated_at,version)
        values (202,101,'Source','ACTIVE',1,now(),now(),0)
        """);
    jdbc.update(
        """
        insert into crm_leads(company_id,contact_id,stage,created_by_user_id)
        values (101,202,'QUALIFIED',1)
        """);
    new CrmContactMergeExtension(jdbc).reconcile(101, 202, 201, 1);
    assertThat(
            jdbc.queryForObject(
                "select stage from crm_leads where company_id=101 and contact_id=201",
                String.class))
        .isEqualTo("QUALIFIED");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from crm_leads where company_id=101 and contact_id=202",
                Long.class))
        .isZero();
  }

  @Test
  void optionalLeadAndTaskPagingFiltersAcceptNullOnPostgres() {
    String schema = schema();
    flyway(schema, "114").migrate();
    JdbcTemplate jdbc = new JdbcTemplate(scoped(schema));
    seed(jdbc);

    var leads = new LeadService(jdbc, null);
    var work = new CrmWorkService(jdbc, null);
    assertThat(leads.list(101, null, null, 50).items()).isEmpty();
    assertThat(work.accounts(101, null, 50).items()).isEmpty();
    assertThat(work.tasks(101, null, 50, null, null).items()).isEmpty();
  }

  @Test
  void leadHistoryIncludesActorAndTimestampWithoutCrossTenantAccess() {
    String schema = schema();
    flyway(schema, "114").migrate();
    JdbcTemplate jdbc = new JdbcTemplate(scoped(schema));
    seed(jdbc);
    jdbc.update(
        """
        insert into crm_leads(company_id,contact_id,stage,created_by_user_id)
        values (101,201,'QUALIFIED',1)
        """);
    jdbc.update(
        """
        insert into crm_lead_stage_events
          (id,company_id,contact_id,previous_stage,new_stage,actor_user_id,occurred_at)
        values (501,101,201,null,'NEW',null,'2026-10-13T08:00:00Z'),
               (502,101,201,'NEW','QUALIFIED',1,'2026-10-13T09:00:00Z')
        """);
    var service = new LeadService(jdbc, null);
    var history = service.history(101, 201);
    assertThat(history).hasSize(2);
    assertThat(history.getFirst().actorUserId()).isEqualTo("1");
    assertThat(history.getFirst().actorName()).isEqualTo("Owner A");
    assertThat(history.getFirst().actorUsername()).isEqualTo("crm-a");
    assertThat(history.getFirst().previousStage()).isEqualTo(LeadService.Stage.NEW);
    assertThat(history.getFirst().newStage()).isEqualTo(LeadService.Stage.QUALIFIED);
    assertThat(history.getFirst().occurredAt())
        .isEqualTo(java.time.Instant.parse("2026-10-13T09:00:00Z"));
    assertThat(history.getLast().actorName()).isNull();
    assertThat(history.getLast().actorUserId()).isNull();
    assertThatThrownBy(() -> service.history(102, 201))
        .isInstanceOf(com.messaging.common.exception.ApiException.class);
  }

  @Test
  void editingCustomerAccountChecksVersionAndDoesNotCrossTenants() {
    String schema = schema();
    flyway(schema, "114").migrate();
    JdbcTemplate jdbc = new JdbcTemplate(scoped(schema));
    seed(jdbc);
    jdbc.update(
        """
        insert into crm_accounts(id,company_id,name,normalized_name)
        values (301,101,'Example','example')
        """);

    var work = new CrmWorkService(jdbc, null);
    var updated = work.updateAccount(101, 301, 0, "Renamed", null, "New notes", null);
    assertThat(updated.name()).isEqualTo("Renamed");
    assertThat(updated.version()).isEqualTo(1);
    assertThatThrownBy(() -> work.updateAccount(101, 301, 0, "Stale", null, null, null))
        .isInstanceOf(com.messaging.common.exception.ApiException.class);
    assertThatThrownBy(() -> work.updateAccount(102, 301, 1, "Wrong tenant", null, null, null))
        .isInstanceOf(com.messaging.common.exception.ApiException.class);
    assertThat(work.getAccount(101, 301).name()).isEqualTo("Renamed");
  }

  @Test
  void taskBoardUpgradeBackfillsExistingTasksWithoutChangingTheirLifecycle() {
    String schema = schema();
    flyway(schema, "113").migrate();
    var jdbc = new JdbcTemplate(scoped(schema));
    seed(jdbc);
    jdbc.update(
        "insert into crm_tasks(id,company_id,title,assignee_user_id,status,version,reminder_generation,completed_at) values(401,101,'Completed',1,'DONE',5,2,now()),(402,102,'Working',2,'IN_PROGRESS',3,1,null)");
    flyway(schema, "114").migrate();
    assertThat(jdbc.queryForObject("select column_key from crm_tasks where id=401", String.class))
        .isEqualTo("DONE");
    assertThat(jdbc.queryForObject("select column_key from crm_tasks where id=402", String.class))
        .isEqualTo("IN_PROGRESS");
    assertThat(jdbc.queryForObject("select count(*) from crm_task_columns", Long.class))
        .isEqualTo(8L);
    assertThat(jdbc.queryForObject("select version from crm_tasks where id=401", Long.class))
        .isEqualTo(5L);
    assertThat(
            jdbc.queryForObject(
                "select reminder_generation from crm_tasks where id=401", Long.class))
        .isEqualTo(2L);
    assertThat(
            jdbc.queryForObject(
                "select completed_at is not null from crm_tasks where id=401", Boolean.class))
        .isTrue();
  }

  private void seed(JdbcTemplate jdbc) {
    jdbc.update(
        """
        insert into users(id,email,name,is_verified,username,password,two_factor_enabled)
        values (1,'crm-a@example.com','Owner A',true,'crm-a','x',false),
               (2,'crm-b@example.com','Owner B',true,'crm-b','x',false)
        """);
    jdbc.update(
        """
        insert into companies(id,name,slug,status,created_by_user_id,created_at,updated_at)
        values (101,'Tenant A','crm-a','ACTIVE',1,now(),now()),
               (102,'Tenant B','crm-b','ACTIVE',2,now(),now())
        """);
    jdbc.update(
        """
        insert into contacts(id,company_id,display_name,status,created_by_user_id,created_at,updated_at,version)
        values (201,101,'Alice','ACTIVE',1,now(),now(),0),
               (203,102,'Bob','ACTIVE',2,now(),now(),0)
        """);
  }

  private boolean tableExists(String schema, String table) {
    return Boolean.TRUE.equals(
        admin.queryForObject(
            "select exists (select 1 from information_schema.tables where table_schema=? and table_name=?)",
            Boolean.class,
            schema,
            table));
  }

  private String schema() {
    return "qa_crm_" + UUID.randomUUID().toString().replace("-", "");
  }

  private Flyway flyway(String schema, String target) {
    return Flyway.configure()
        .dataSource(URL, USER, PASSWORD)
        .schemas(schema)
        .defaultSchema(schema)
        .createSchemas(true)
        .cleanDisabled(false)
        .target(MigrationVersion.fromVersion(target))
        .load();
  }

  private DataSource scoped(String schema) {
    return new DelegatingDataSource(new DriverManagerDataSource(URL, USER, PASSWORD)) {
      @Override
      public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        try (var statement = connection.createStatement()) {
          statement.execute("set search_path to " + schema);
        }
        return connection;
      }
    };
  }
}
