package com.messaging.contacts.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.contacts.core.domain.ContactTypes.MembershipSource;
import com.messaging.contacts.core.repository.ContactListRepository;
import com.messaging.contacts.core.repository.ContactRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class ContactsV80PostgresTests {

  private static final String URL = "jdbc:postgresql://127.0.0.1:55432/messaging_test";
  private static final String USER = "test";
  private static final String PASSWORD = "test";

  private final JdbcTemplate admin =
      new JdbcTemplate(new DriverManagerDataSource(URL, USER, PASSWORD));

  @AfterEach
  void cleanup() {
    List<String> schemas =
        admin.queryForList(
            "select schema_name from information_schema.schemata where schema_name like 'qa_contacts_%'",
            String.class);
    for (String schema : schemas) {
      admin.execute("drop schema if exists " + schema + " cascade");
    }
  }

  @Test
  void representativeUpgradeFromV79ReachesV80WithExpectedIndexes() {
    String schema = schema();
    Flyway through79 = flyway(schema, MigrationVersion.fromVersion("79"));
    through79.clean();
    through79.migrate();

    Flyway through80 = flyway(schema, MigrationVersion.fromVersion("80"));
    through80.migrate();

    assertThat(through80.info().current().getVersion().getVersion()).isEqualTo("80");
    assertThat(indexExists(schema, "uk_contact_identity_active")).isTrue();
    assertThat(indexExists(schema, "idx_contacts_company_updated")).isTrue();
    assertThat(indexExists(schema, "idx_contact_list_memberships_list")).isTrue();
    assertThat(indexExists(schema, "idx_contact_custom_field_number")).isTrue();
  }

  @Test
  void exactIdentityIsUniqueInsideTenantButReusableAcrossTenants() {
    TestDatabase db = migratedDatabase();
    seedTenants(db.jdbc());

    db.jdbc()
        .update(
            """
            insert into contact_identities
              (id,company_id,contact_id,identity_type,namespace,value,normalized_value,
               is_primary,status,created_at,updated_at)
            values (1001,101,201,'EMAIL',null,'A@example.com','a@example.com',
                    true,'ACTIVE',now(),now())
            """);

    assertThatThrownBy(
            () ->
                db.jdbc()
                    .update(
                        """
                        insert into contact_identities
                          (id,company_id,contact_id,identity_type,namespace,value,normalized_value,
                           is_primary,status,created_at,updated_at)
                        values (1002,101,202,'EMAIL',null,'a@example.com','a@example.com',
                                true,'ACTIVE',now(),now())
                        """))
        .isInstanceOf(DataIntegrityViolationException.class);

    db.jdbc()
        .update(
            """
            insert into contact_identities
              (id,company_id,contact_id,identity_type,namespace,value,normalized_value,
               is_primary,status,created_at,updated_at)
            values (1003,102,203,'EMAIL',null,'a@example.com','a@example.com',
                    true,'ACTIVE',now(),now())
            """);

    assertThat(
            db.jdbc()
                .queryForObject(
                    "select count(*) from contact_identities where normalized_value='a@example.com'",
                    Long.class))
        .isEqualTo(2L);
  }

  @Test
  void concurrentDuplicateIdentityRaceAllowsExactlyOneWinner() throws Exception {
    TestDatabase db = migratedDatabase();
    seedTenants(db.jdbc());

    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<Object> first =
          pool.submit(
              () ->
                  insertIdentity(
                      db.tx(), db.jdbc(), 1101L, 201L, "race@example.com", start));
      Future<Object> second =
          pool.submit(
              () ->
                  insertIdentity(
                      db.tx(), db.jdbc(), 1102L, 202L, "race@example.com", start));
      start.countDown();

      Object a = first.get();
      Object b = second.get();
      long successes = java.util.stream.Stream.of(a, b).filter(Boolean.TRUE::equals).count();
      long conflicts =
          java.util.stream.Stream.of(a, b)
              .filter(DataIntegrityViolationException.class::isInstance)
              .count();

      assertThat(successes).isEqualTo(1);
      assertThat(conflicts).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void repositoryScopingAndOptimisticVersionFailClosedAcrossTenantAndStaleWrites() {
    TestDatabase db = migratedDatabase();
    seedTenants(db.jdbc());
    ContactRepository repository =
        new ContactRepository(db.jdbc(), new NamedParameterJdbcTemplate(db.dataSource()));

    assertThat(repository.find(102L, 201L)).isEmpty();

    int stale =
        repository.patchContact(
            101L, 201L, java.util.Map.of("display_name", "stale"), 99L);
    assertThat(stale).isZero();

    int current =
        repository.patchContact(
            101L, 201L, java.util.Map.of("display_name", "current"), 0L);
    assertThat(current).isEqualTo(1);

    int customFieldOnlyGuard = repository.patchContact(101L, 201L, java.util.Map.of(), 1L);
    assertThat(customFieldOnlyGuard).isEqualTo(1);

    int staleAfterCustomFieldOnlyGuard =
        repository.patchContact(101L, 201L, java.util.Map.of(), 1L);
    assertThat(staleAfterCustomFieldOnlyGuard).isZero();
    assertThat(
            db.jdbc()
                .queryForObject(
                    "select version from contacts where company_id=101 and id=201", Long.class))
        .isEqualTo(2L);
  }

  @Test
  void concurrentListMembershipAdditionIsIdempotentAndCountRemainsExact() throws Exception {
    TestDatabase db = migratedDatabase();
    seedTenants(db.jdbc());
    db.jdbc()
        .update(
            """
            insert into contact_lists
              (id,company_id,name,normalized_name,status,member_count,created_by_user_id,
               created_at,updated_at,version)
            values (301,101,'Customers','customers','ACTIVE',0,1,now(),now(),0)
            """);

    ContactListRepository repository =
        new ContactListRepository(db.jdbc(), new NamedParameterJdbcTemplate(db.dataSource()));

    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<Integer> first =
          pool.submit(() -> addMember(db.tx(), repository, start));
      Future<Integer> second =
          pool.submit(() -> addMember(db.tx(), repository, start));
      start.countDown();

      assertThat(first.get() + second.get()).isEqualTo(1);
      assertThat(
              db.jdbc()
                  .queryForObject(
                      "select member_count from contact_lists where id=301", Long.class))
          .isEqualTo(1L);
      assertThat(
              db.jdbc()
                  .queryForObject(
                      "select count(*) from contact_list_memberships where list_id=301",
                      Long.class))
          .isEqualTo(1L);
      assertThat(repository.listsForContact(101L, 201L))
          .extracting("id")
          .containsExactly(301L);
      assertThat(repository.listsForContact(102L, 201L)).isEmpty();
    } finally {
      pool.shutdownNow();
    }
  }

  private int addMember(
      TransactionTemplate tx, ContactListRepository repository, CountDownLatch start)
      throws InterruptedException {
    start.await();
    Integer inserted =
        tx.execute(
            status -> {
              repository.lockActive(101L, 301L);
              return repository.addMembers(
                  101L, 301L, List.of(201L), MembershipSource.MANUAL, null, 1L);
            });
    return inserted == null ? 0 : inserted;
  }

  private Object insertIdentity(
      TransactionTemplate tx,
      JdbcTemplate jdbc,
      long identityId,
      long contactId,
      String normalized,
      CountDownLatch start)
      throws InterruptedException {
    start.await();
    try {
      tx.executeWithoutResult(
          status ->
              jdbc.update(
                  """
                  insert into contact_identities
                    (id,company_id,contact_id,identity_type,namespace,value,normalized_value,
                     is_primary,status,created_at,updated_at)
                  values (?,101,?,'EMAIL',null,?,?,false,'ACTIVE',now(),now())
                  """,
                  identityId,
                  contactId,
                  normalized,
                  normalized));
      return Boolean.TRUE;
    } catch (RuntimeException exception) {
      return exception;
    }
  }

  private TestDatabase migratedDatabase() {
    String schema = schema();
    Flyway flyway = flyway(schema, MigrationVersion.fromVersion("80"));
    flyway.clean();
    flyway.migrate();

    DriverManagerDataSource root = new DriverManagerDataSource(URL, USER, PASSWORD);
    DataSource scoped = schemaScoped(root, schema);
    return new TestDatabase(
        new JdbcTemplate(scoped),
        scoped,
        new TransactionTemplate(new DataSourceTransactionManager(scoped)));
  }

  private void seedTenants(JdbcTemplate jdbc) {
    jdbc.update(
        """
        insert into users(id,email,name,is_verified,username,password,two_factor_enabled)
        values
          (1,'owner@example.com','Owner',true,'owner','x',false),
          (2,'other@example.com','Other',true,'other','x',false)
        """);
    jdbc.update(
        """
        insert into companies
          (id,name,slug,status,created_by_user_id,created_at,updated_at)
        values
          (101,'A','a','ACTIVE',1,now(),now()),
          (102,'B','b','ACTIVE',2,now(),now())
        """);
    jdbc.update(
        """
        insert into contacts
          (id,company_id,display_name,status,created_by_user_id,created_at,updated_at,version)
        values
          (201,101,'A1','ACTIVE',1,now(),now(),0),
          (202,101,'A2','ACTIVE',1,now(),now(),0),
          (203,102,'B1','ACTIVE',2,now(),now(),0)
        """);
  }

  private Flyway flyway(String schema, MigrationVersion target) {
    return Flyway.configure()
        .dataSource(URL, USER, PASSWORD)
        .schemas(schema)
        .defaultSchema(schema)
        .createSchemas(true)
        .cleanDisabled(false)
        .target(target)
        .load();
  }

  private boolean indexExists(String schema, String index) {
    Integer count =
        admin.queryForObject(
            """
            select count(*)
            from pg_indexes
            where schemaname = ? and indexname = ?
            """,
            Integer.class,
            schema,
            index);
    return count != null && count == 1;
  }

  private String schema() {
    return "qa_contacts_" + UUID.randomUUID().toString().replace("-", "");
  }

  private DataSource schemaScoped(DataSource delegate, String schema) {
    return new DelegatingDataSource(delegate) {
      @Override
      public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        try (var statement = connection.createStatement()) {
          statement.execute("set search_path to " + schema);
        }
        return connection;
      }

      @Override
      public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = super.getConnection(username, password);
        try (var statement = connection.createStatement()) {
          statement.execute("set search_path to " + schema);
        }
        return connection;
      }
    };
  }

  private record TestDatabase(
      JdbcTemplate jdbc, DataSource dataSource, TransactionTemplate tx) {}
}
