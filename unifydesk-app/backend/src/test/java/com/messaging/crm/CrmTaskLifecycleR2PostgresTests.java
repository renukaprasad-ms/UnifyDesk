package com.messaging.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.messaging.common.exception.ApiException;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.notification.platform.core.event.NotificationEventService;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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
    jdbc.update(
        """
        insert into crm_accounts(id,company_id,name,normalized_name,owner_user_id)
        values (301,101,'Acme','acme',2)
        """);
    jdbc.update(
        """
        insert into crm_tasks(id,company_id,title,assignee_user_id,status)
        values (401,101,'Open follow-up',2,'OPEN')
        """);
    jdbc.update(
        """
        insert into crm_tasks(id,company_id,title,assignee_user_id,status,completed_at)
        values (402,101,'Closed follow-up',2,'DONE',now())
        """);
    jdbc.update("update company_members set status='SUSPENDED' where company_id=101 and user_id=2");
    new CrmTaskMembershipLifecycle(jdbc).onMemberDeactivated(101, 2);

    assertThat(
            jdbc.queryForObject("select assignee_user_id from crm_tasks where id=401", Long.class))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select reminder_generation from crm_tasks where id=401", Long.class))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject("select assignee_user_id from crm_tasks where id=402", Long.class))
        .isEqualTo(2L);
    assertThat(
            jdbc.queryForObject("select owner_user_id from crm_accounts where id=301", Long.class))
        .isNull();
  }

  @Test
  void missingOwnerFailsClosedRatherThanAbandoningOpenWork() {
    JdbcTemplate jdbc = setup();
    jdbc.update(
        """
        insert into crm_tasks(id,company_id,title,assignee_user_id)
        values (401,101,'Unassigned follow-up',2)
        """);
    jdbc.update("update company_members set status='SUSPENDED' where company_id=101");
    assertThatThrownBy(() -> new CrmTaskMembershipLifecycle(jdbc).onMemberDeactivated(101, 2))
        .isInstanceOf(ApiException.class);
    assertThat(
            jdbc.queryForObject("select assignee_user_id from crm_tasks where id=401", Long.class))
        .isEqualTo(2L);
  }

  @Test
  void dueReminderIsClaimedOnceAndNotRepeatedOnNextScan() {
    JdbcTemplate jdbc = setup();
    jdbc.update(
        """
        insert into crm_tasks(id,company_id,title,assignee_user_id,due_at)
        values (401,101,'Call customer',1,now() + interval '5 minutes')
        """);
    NotificationEventService events = mock(NotificationEventService.class);
    CrmTaskReminderScheduler scheduler = new CrmTaskReminderScheduler(jdbc, events);
    scheduler.scan();
    scheduler.scan();

    verify(events, times(1)).recordCrmTaskDue(401, 101, 1, 0, "Call customer");
    verifyNoMoreInteractions(events);
    assertThat(
            jdbc.queryForObject(
                "select reminded_generation from crm_tasks where id=401", Long.class))
        .isZero();
  }

  @Test
  void editingDueTimeAndReopeningTaskRearmsReminderExactlyWhenNeeded() {
    JdbcTemplate jdbc = setup();
    jdbc.update(
        """
        insert into crm_tasks(id,company_id,title,assignee_user_id,due_at)
        values (401,101,'Follow up',1,now() + interval '5 minutes')
        """);
    CompanyMemberRepository members = mock(CompanyMemberRepository.class);
    when(members.existsByCompanyIdAndUserIdAndStatus(101L, 1L, CompanyMemberStatus.ACTIVE))
        .thenReturn(true);
    CrmWorkService work = new CrmWorkService(jdbc, members);
    var task = work.getTask(101, 401);
    var rescheduled =
        work.updateTask(
            101,
            401,
            task.version(),
            "Follow up",
            null,
            "NORMAL",
            task.dueAt().plusSeconds(3600),
            1L,
            null,
            null,
            1L);
    assertThat(
            jdbc.queryForObject(
                "select reminder_generation from crm_tasks where id=401", Long.class))
        .isEqualTo(1L);

    var unchanged =
        work.updateTask(
            101,
            401,
            rescheduled.version(),
            "Follow up revised",
            null,
            "NORMAL",
            rescheduled.dueAt(),
            1L,
            null,
            null,
            1L);
    assertThat(
            jdbc.queryForObject(
                "select reminder_generation from crm_tasks where id=401", Long.class))
        .isEqualTo(1L);

    var done = work.changeTaskStatus(101, 401, unchanged.version(), "DONE", 1L);
    work.changeTaskStatus(101, 401, done.version(), "OPEN", 1L);
    assertThat(
            jdbc.queryForObject(
                "select reminder_generation from crm_tasks where id=401", Long.class))
        .isEqualTo(2L);
  }

  @Test
  void userPurgeReassignsActiveCompanyTasksBeforeRemovingMembership() {
    JdbcTemplate jdbc = setup();
    jdbc.update(
        """
        insert into crm_tasks(id,company_id,title,assignee_user_id)
        values (401,101,'Follow up',2)
        """);
    new CrmTaskMembershipLifecycle(jdbc).onUserPurging(2);
    assertThat(
            jdbc.queryForObject("select assignee_user_id from crm_tasks where id=401", Long.class))
        .isEqualTo(1L);
  }

  @Test
  void taskActivityTracksActorsEditsAndStatusWithoutRecordingFailedOrNoopChanges() {
    JdbcTemplate jdbc = setup();
    CompanyMemberRepository members = mock(CompanyMemberRepository.class);
    when(members.existsByCompanyIdAndUserIdAndStatus(101L, 1L, CompanyMemberStatus.ACTIVE))
        .thenReturn(true);
    var work = new CrmWorkService(jdbc, members);
    var task = work.createTask(101, "Call customer", null, "NORMAL", null, 1, null, null, 1);
    long taskId = Long.parseLong(task.id());
    var changed = work.changeTaskStatus(101, taskId, task.version(), "IN_PROGRESS", 2);
    var edited =
        work.updateTask(
            101,
            taskId,
            changed.version(),
            "Call customer today",
            "Discuss renewal",
            "HIGH",
            null,
            1,
            null,
            null,
            1);
    var history = work.taskHistory(101, taskId);
    assertThat(history).hasSize(3);
    assertThat(history.getFirst().eventType()).isEqualTo("UPDATED");
    assertThat(history.getFirst().changedFields()).isEqualTo("Title, Details, Priority");
    assertThat(history.getFirst().actorName()).isEqualTo("Owner");
    assertThat(history.getFirst().occurredAt()).isNotNull();
    assertThat(history.get(1).previousStatus()).isEqualTo("OPEN");
    assertThat(history.get(1).newStatus()).isEqualTo("IN_PROGRESS");
    assertThat(history.get(1).actorUsername()).isEqualTo("crm-member");
    assertThat(history.getLast().eventType()).isEqualTo("CREATED");
    assertThatThrownBy(() -> work.changeTaskStatus(101, taskId, task.version(), "DONE", 1))
        .isInstanceOf(ApiException.class);
    work.changeTaskStatus(101, taskId, edited.version(), "IN_PROGRESS", 1);
    assertThat(work.taskHistory(101, taskId)).hasSize(3);
    assertThatThrownBy(() -> work.taskHistory(102, taskId)).isInstanceOf(ApiException.class);
  }

  @Test
  void customStepsPreserveActorHistoryCompletionAndReminderLifecycle() {
    JdbcTemplate jdbc = setup();
    var members = mock(CompanyMemberRepository.class);
    when(members.existsByCompanyIdAndUserIdAndStatus(101L, 1L, CompanyMemberStatus.ACTIVE))
        .thenReturn(true);
    var work = new CrmWorkService(jdbc, members);
    var board = new CrmTaskBoardService(jdbc);
    var review =
        board.create(101, "Review", "IN_PROGRESS").stream()
            .filter(c -> c.name().equals("Review"))
            .findFirst()
            .orElseThrow();
    var task = work.createTask(101, "Renewal", null, "HIGH", null, 1, null, null, 1, "IN_PROGRESS");
    long id = Long.parseLong(task.id());
    var moved = work.changeTaskColumn(101, id, task.version(), review.key(), 2);
    assertThat(moved.status()).isEqualTo("IN_PROGRESS");
    var event = work.taskHistory(101, id).getFirst();
    assertThat(event.eventType()).isEqualTo("STEP_CHANGED");
    assertThat(event.previousColumn()).isEqualTo("In progress");
    assertThat(event.newColumn()).isEqualTo("Review");
    assertThat(event.actorName()).isEqualTo("Member");
    board.rename(101, review.key(), review.version(), "Customer review");
    assertThat(work.taskHistory(101, id).getFirst().newColumn()).isEqualTo("Review");
    assertThatThrownBy(() -> board.remove(101, review.key(), review.version() + 1))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> work.changeTaskColumn(101, id, task.version(), "DONE", 1))
        .isInstanceOf(ApiException.class);
    var done = work.changeTaskColumn(101, id, moved.version(), "DONE", 1);
    assertThat(
            jdbc.queryForObject(
                "select completed_at is not null from crm_tasks where id=?", Boolean.class, id))
        .isTrue();
    var reopened = work.changeTaskColumn(101, id, done.version(), review.key(), 1);
    assertThat(reopened.status()).isEqualTo("IN_PROGRESS");
    assertThat(
            jdbc.queryForObject(
                "select completed_at is null from crm_tasks where id=?", Boolean.class, id))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select reminder_generation from crm_tasks where id=?", Long.class, id))
        .isEqualTo(1L);
    int count = work.taskHistory(101, id).size();
    work.changeTaskColumn(101, id, reopened.version(), review.key(), 1);
    assertThat(work.taskHistory(101, id)).hasSize(count);
  }

  @Test
  void stepConfigurationIsVersionedAndScopedAndDoesNotRestoreRenamedDefaults() {
    var jdbc = setup();
    var board = new CrmTaskBoardService(jdbc);
    var columns = board.columns(101);
    assertThat(columns).hasSize(4);
    var open = columns.getFirst();
    board.rename(101, open.key(), open.version(), "Backlog");
    var next = board.create(101, "To do", "OPEN");
    assertThat(board.columns(101)).hasSize(5);
    assertThat(board.columns(101).getFirst().name()).isEqualTo("Backlog");
    assertThatThrownBy(() -> board.create(101, "to DO", "OPEN")).isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                board.reorder(
                    101,
                    columns.stream()
                        .map(c -> new CrmTaskBoardService.Order(c.key(), c.version()))
                        .toList()))
        .isInstanceOf(ApiException.class);
    var reversed = new java.util.ArrayList<>(next);
    java.util.Collections.reverse(reversed);
    var ordered =
        board.reorder(
            101,
            reversed.stream()
                .map(c -> new CrmTaskBoardService.Order(c.key(), c.version()))
                .toList());
    assertThat(ordered.getFirst().name()).isEqualTo("To do");
    assertThatThrownBy(() -> board.remove(101, "OPEN", 1)).isInstanceOf(ApiException.class);
    jdbc.update(
        "insert into companies(id,name,slug,status,created_by_user_id,created_at,updated_at) values(102,'Other','other-board','ACTIVE',1,now(),now())");
    assertThatThrownBy(() -> CrmTaskBoardService.target(jdbc, 102, ordered.getFirst().key()))
        .isInstanceOf(ApiException.class);
    assertThat(board.remove(101, ordered.getFirst().key(), ordered.getFirst().version()))
        .hasSize(4);
  }

  private JdbcTemplate setup() {
    schema = "qa_crm_r2_" + UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(URL, USER, PASSWORD)
        .schemas(schema)
        .defaultSchema(schema)
        .createSchemas(true)
        .cleanDisabled(false)
        .target(MigrationVersion.fromVersion("114"))
        .load()
        .migrate();
    JdbcTemplate jdbc =
        new JdbcTemplate(
            new DelegatingDataSource(new DriverManagerDataSource(URL, USER, PASSWORD)) {
              @Override
              public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                try (var statement = connection.createStatement()) {
                  statement.execute("set search_path to " + schema);
                }
                return connection;
              }
            });
    jdbc.update(
        """
        insert into users(id,email,name,is_verified,username,password,two_factor_enabled)
        values (1,'crm-owner@example.com','Owner',true,'crm-owner','x',false),
               (2,'crm-member@example.com','Member',true,'crm-member','x',false)
        """);
    jdbc.update(
        """
        insert into companies(id,name,slug,status,created_by_user_id,created_at,updated_at)
        values (101,'Tenant A','crm-a','ACTIVE',1,now(),now())
        """);
    jdbc.update(
        """
        insert into company_members(id,company_id,user_id,role,status,created_at,updated_at)
        values (701,101,1,'OWNER','ACTIVE',now(),now()),
               (702,101,2,'MEMBER','ACTIVE',now(),now())
        """);
    return jdbc;
  }
}
