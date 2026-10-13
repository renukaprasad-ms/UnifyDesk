package com.messaging.crm;

import static org.assertj.core.api.Assertions.*;

import com.messaging.common.exception.ApiException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class CrmSalesWorkspacePostgresTests {
  private static final String URL = "jdbc:postgresql://127.0.0.1:55432/messaging_test";
  private String schema;

  @AfterEach
  void cleanup() {
    if (schema != null)
      new JdbcTemplate(new DriverManagerDataSource(URL, "test", "test"))
          .execute("drop schema " + schema + " cascade");
  }

  private JdbcTemplate setup() {
    schema = "qa_sales_" + UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(URL, "test", "test")
        .schemas(schema)
        .defaultSchema(schema)
        .createSchemas(true)
        .load()
        .migrate();
    DataSource source =
        new DelegatingDataSource(new DriverManagerDataSource(URL, "test", "test")) {
          @Override
          public Connection getConnection() throws SQLException {
            Connection c = super.getConnection();
            try (var statement = c.createStatement()) {
              statement.execute("set search_path to " + schema);
            }
            return c;
          }
        };
    JdbcTemplate jdbc = new JdbcTemplate(source);
    jdbc.update(
        """
        insert into users(id,email,name,is_verified,username,password,two_factor_enabled)
        values(1,'sales-a@example.com','Alex',true,'alex','x',false),(2,'sales-b@example.com','Blair',true,'blair','x',false)
        """);
    jdbc.update(
        """
        insert into companies(id,name,slug,status,created_by_user_id,created_at,updated_at) values(101,'A','sales-a','ACTIVE',1,now(),now()),(102,'B','sales-b','ACTIVE',2,now(),now())
        """);
    jdbc.update(
        "insert into company_members(id,company_id,user_id,role,status,created_at,updated_at) values(701,101,1,'OWNER','ACTIVE',now(),now()),(702,102,2,'OWNER','ACTIVE',now(),now())");
    jdbc.update(
        "insert into contacts(id,company_id,display_name,status,created_by_user_id,created_at,updated_at,version) values(201,101,'Customer A','ACTIVE',1,now(),now(),0),(202,101,'Merge target','ACTIVE',1,now(),now(),0),(203,102,'Customer B','ACTIVE',2,now(),now(),0)");
    jdbc.update(
        "insert into crm_accounts(id,company_id,name,normalized_name) values(301,101,'Customer Ltd','customer ltd'),(302,102,'Other Ltd','other ltd')");
    return jdbc;
  }

  private CrmDealsService.Input input(String amount, String currency) {
    return new CrmDealsService.Input(
        "Renewal", "Annual renewal", amount, currency, null, 1L, 201L, 301L);
  }

  @Test
  void dealsKeepExactValuesAuditActorsAndRejectStaleOrCrossWorkspaceUpdates() {
    var jdbc = setup();
    var service = new CrmDealsService(jdbc);
    var deal = service.create(101, input("123456789012345.67", "INR"), 1);
    long id = Long.parseLong(deal.id());
    assertThat(deal.amount()).isEqualTo("123456789012345.67");
    var won = service.move(101, id, deal.version(), CrmDealsService.Stage.WON, 1);
    assertThat(
            jdbc.queryForObject("select closed_at from crm_deals where id=?", Timestamp.class, id))
        .isNotNull();
    assertThatThrownBy(() -> service.move(101, id, deal.version(), CrmDealsService.Stage.LOST, 1))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.get(102, id)).isInstanceOf(ApiException.class);
    var reopened = service.move(101, id, won.version(), CrmDealsService.Stage.NEGOTIATION, 1);
    assertThat(
            jdbc.queryForObject("select closed_at from crm_deals where id=?", Timestamp.class, id))
        .isNull();
    service.move(101, id, reopened.version(), CrmDealsService.Stage.NEGOTIATION, 1);
    assertThat(service.history(101, id)).hasSize(3);
    assertThat(service.history(101, id).getFirst().actorName()).isEqualTo("Alex");
    assertThat(service.history(101, id).getFirst().previousStage()).isEqualTo("WON");
    var changed = service.update(101, id, reopened.version(), input("500.05", "INR"), 1);
    assertThat(changed.amount()).isEqualTo("500.05");
    assertThat(service.history(101, id).getFirst().changedFields()).isEqualTo("Value");
  }

  @Test
  void invalidCurrencyPrecisionAndForeignAssociationsNeverCreateDeals() {
    var jdbc = setup();
    var service = new CrmDealsService(jdbc);
    assertThatThrownBy(() -> service.create(101, input("10.001", "INR"), 1))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.create(101, input("1.1", "JPY"), 1))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.create(101, input("-1", "USD"), 1))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.create(101, input("1", "XXX"), 1))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                service.create(
                    101,
                    new CrmDealsService.Input("Invalid", null, "10", "INR", null, 1L, 203L, 301L),
                    1))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                service.create(
                    101,
                    new CrmDealsService.Input("Invalid", null, "10", "INR", null, 2L, 201L, 301L),
                    1))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                service.create(
                    101,
                    new CrmDealsService.Input("Invalid", null, "10", "INR", null, 1L, 201L, 302L),
                    1))
        .isInstanceOf(ApiException.class);
    assertThat(jdbc.queryForObject("select count(*) from crm_deals", Long.class)).isZero();
  }

  @Test
  void reportSeparatesCurrenciesAndUsesWorkspaceScopedCounts() {
    var jdbc = setup();
    var deals = new CrmDealsService(jdbc);
    deals.create(101, input("0.10", "INR"), 1);
    deals.create(101, input("0.20", "INR"), 1);
    var usd = deals.create(101, input("25.99", "USD"), 1);
    deals.move(101, Long.parseLong(usd.id()), usd.version(), CrmDealsService.Stage.WON, 1);
    jdbc.update(
        "insert into crm_tasks(id,company_id,title,assignee_user_id,due_at) values(401,101,'Call',1,now()-interval '1 day')");
    var report = new CrmReportsService(jdbc).report(101, 30);
    assertThat(report.overdueTasks()).isEqualTo(1);
    assertThat(report.trend()).hasSize(30);
    assertThat(report.values()).hasSize(2);
    assertThat(report.values().getFirst().currency()).isEqualTo("INR");
    assertThat(report.values().getFirst().openValue()).isEqualTo("0.3");
    assertThat(report.values().getLast().wonValue()).isEqualTo("25.99");
    assertThat(new CrmReportsService(jdbc).report(102, 7).values()).isEmpty();
    assertThatThrownBy(() -> new CrmReportsService(jdbc).report(101, 365))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void activityPaginationHandlesSameIdsAcrossSourcesAndPermissionFiltering() {
    var jdbc = setup();
    var activity = new CrmActivityService(jdbc);
    jdbc.update(
        "insert into crm_tasks(id,company_id,title,assignee_user_id,contact_id) values(401,101,'Call',1,201)");
    jdbc.update(
        "insert into crm_deals(id,company_id,title,amount,currency,owner_user_id,contact_id) values(501,101,'Renewal',10,'INR',1,201)");
    jdbc.update(
        "insert into crm_task_events(id,company_id,task_id,event_type,new_status,actor_user_id,occurred_at) values(900,101,401,'CREATED','OPEN',1,now()-interval '1 hour')");
    jdbc.update(
        "insert into crm_deal_events(id,company_id,deal_id,event_type,new_stage,actor_user_id,occurred_at) select 900,101,501,'CREATED','NEW',1,occurred_at from crm_task_events where id=900");
    var access = new CrmActivityService.Access(false, true, true, false, false, false);
    var page = activity.page(101, access, null, null, null, null, null, null, 1);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().getFirst().source()).isEqualTo("TASK");
    var next = activity.page(101, access, null, null, null, null, null, page.nextCursor(), 1);
    assertThat(next.items().getFirst().source()).isEqualTo("DEAL");
    assertThat(next.hasMore()).isFalse();
    assertThat(
            activity
                .page(
                    101,
                    new CrmActivityService.Access(false, false, false, false, false, false),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    50)
                .items())
        .isEmpty();
    assertThat(activity.page(101, access, null, "Alex", 201L, null, null, null, 50).items())
        .hasSize(2);
    assertThat(activity.page(102, access, null, null, null, null, null, null, 50).items())
        .isEmpty();
    assertThatThrownBy(
            () -> activity.page(101, access, null, null, null, null, null, "bad cursor", 50))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void contactMergeKeepsDealAndNoteLinks() {
    var jdbc = setup();
    var service = new CrmDealsService(jdbc);
    var activity = new CrmActivityService(jdbc);
    var deal = service.create(101, input("50", "INR"), 1);
    activity.note(101, 201L, "Call next week", 1);
    new CrmContactMergeExtension(jdbc).reconcile(101, 201, 202, 1);
    assertThat(service.get(101, Long.parseLong(deal.id())).contactId()).isEqualTo("202");
    assertThat(jdbc.queryForObject("select contact_id from crm_notes", Long.class)).isEqualTo(202);
    assertThatThrownBy(() -> activity.note(101, 203L, "Wrong tenant", 1))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void failedAuditInsertRollsBackDealChange() {
    var jdbc = setup();
    var service = new CrmDealsService(jdbc);
    var deal = service.create(101, input("50", "INR"), 1);
    var transaction =
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    assertThatThrownBy(
            () ->
                transaction.execute(
                    status ->
                        service.move(
                            101,
                            Long.parseLong(deal.id()),
                            deal.version(),
                            CrmDealsService.Stage.WON,
                            999)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(service.get(101, Long.parseLong(deal.id())).stage())
        .isEqualTo(CrmDealsService.Stage.NEW);
    assertThat(service.history(101, Long.parseLong(deal.id()))).hasSize(1);
  }
}
