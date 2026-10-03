package com.messaging.contacts.consent;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.contacts.consent.ConsentTypes.ActorType;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.PreferenceCommand;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSource;
import com.messaging.contacts.consent.ConsentTypes.PreferenceState;
import com.messaging.contacts.consent.ConsentTypes.Purpose;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class ContactPreferenceOrderingPostgresTests {

  private static final String URL = "jdbc:postgresql://127.0.0.1:55432/messaging_test";
  private static final String USER = "test";
  private static final String PASSWORD = "test";
  private static final Instant T1 = Instant.parse("2026-10-02T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-10-02T10:01:00Z");
  private static final Instant T3 = Instant.parse("2026-10-02T10:02:00Z");

  private final JdbcTemplate admin =
      new JdbcTemplate(new DriverManagerDataSource(URL, USER, PASSWORD));

  @AfterEach
  void cleanup() {
    List<String> schemas =
        admin.queryForList(
            "select schema_name from information_schema.schemata where schema_name like 'qa_contact_pref_%'",
            String.class);
    for (String schema : schemas) {
      admin.execute("drop schema if exists " + schema + " cascade");
    }
  }

  @Test
  void staleImportIsHistoricalOnlyAfterNewerRecipientOptOut() {
    TestDatabase db = migratedDatabase();
    seed(db.jdbc());
    ContactPreferenceRepository repository = new ContactPreferenceRepository(db.jdbc(), new NamedParameterJdbcTemplate(db.jdbc()));

    apply(db, repository, command(PreferenceState.OPTED_IN, T1, "initial"));
    apply(db, repository, command(PreferenceState.OPTED_OUT, T2, "unsubscribe"));
    apply(
        db,
        repository,
        new PreferenceCommand(
            101L,
            201L,
            301L,
            Channel.EMAIL,
            Purpose.MARKETING,
            PreferenceState.OPTED_IN,
            PreferenceSource.IMPORT,
            "import-older",
            ActorType.IMPORT,
            "900",
            "older import evidence",
            T1.plusSeconds(30),
            "stale-import"));

    PreferenceState current =
        repository.effectiveState(101L, 201L, 301L, Channel.EMAIL, Purpose.MARKETING);
    Instant currentOccurredAt =
        db.jdbc()
            .queryForObject(
                """
                select occurred_at from contact_channel_preferences
                where company_id=101 and contact_id=201 and identity_id=301
                  and channel='EMAIL' and purpose='MARKETING'
                """,
                (rs, rowNum) -> rs.getTimestamp(1).toInstant());
    Integer events =
        db.jdbc()
            .queryForObject(
                "select count(*) from contact_preference_events where company_id=101 and contact_id=201",
                Integer.class);

    assertThat(current).isEqualTo(PreferenceState.OPTED_OUT);
    assertThat(currentOccurredAt).isEqualTo(T2);
    assertThat(events).isEqualTo(3);
  }

  @Test
  void strictlyLaterReconsentBecomesEffective() {
    TestDatabase db = migratedDatabase();
    seed(db.jdbc());
    ContactPreferenceRepository repository = new ContactPreferenceRepository(db.jdbc(), new NamedParameterJdbcTemplate(db.jdbc()));

    apply(db, repository, command(PreferenceState.OPTED_OUT, T2, "unsubscribe"));
    apply(db, repository, command(PreferenceState.OPTED_IN, T3, "reconsent"));

    assertThat(
            repository.effectiveState(
                101L, 201L, 301L, Channel.EMAIL, Purpose.MARKETING))
        .isEqualTo(PreferenceState.OPTED_IN);
  }

  @Test
  void equalTimestampConflictFailsClosedToOptOut() {
    TestDatabase db = migratedDatabase();
    seed(db.jdbc());
    ContactPreferenceRepository repository = new ContactPreferenceRepository(db.jdbc(), new NamedParameterJdbcTemplate(db.jdbc()));

    apply(db, repository, command(PreferenceState.OPTED_IN, T2, "optin"));
    apply(db, repository, command(PreferenceState.OPTED_OUT, T2, "optout"));

    assertThat(
            repository.effectiveState(
                101L, 201L, 301L, Channel.EMAIL, Purpose.MARKETING))
        .isEqualTo(PreferenceState.OPTED_OUT);
  }

  @Test
  void repeatedActionIdempotencyDoesNotAppendAnotherEvent() {
    TestDatabase db = migratedDatabase();
    seed(db.jdbc());
    ContactPreferenceRepository repository = new ContactPreferenceRepository(db.jdbc(), new NamedParameterJdbcTemplate(db.jdbc()));
    PreferenceCommand command = command(PreferenceState.OPTED_OUT, T2, "same-key");

    apply(db, repository, command);
    apply(db, repository, command);

    Integer events =
        db.jdbc()
            .queryForObject(
                "select count(*) from contact_preference_events where company_id=101 and idempotency_key='same-key'",
                Integer.class);
    assertThat(events).isEqualTo(1);
  }

  @Test
  void concurrentOlderOptInAndNewerOptOutConvergeToNewerEvent() throws Exception {
    TestDatabase db = migratedDatabase();
    seed(db.jdbc());
    ContactPreferenceRepository repository = new ContactPreferenceRepository(db.jdbc(), new NamedParameterJdbcTemplate(db.jdbc()));
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(2);

    Thread older =
        new Thread(
            () -> {
              await(start);
              apply(db, repository, command(PreferenceState.OPTED_IN, T1, "race-old"));
              done.countDown();
            });
    Thread newer =
        new Thread(
            () -> {
              await(start);
              apply(db, repository, command(PreferenceState.OPTED_OUT, T2, "race-new"));
              done.countDown();
            });
    older.start();
    newer.start();
    start.countDown();

    assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(
            repository.effectiveState(
                101L, 201L, 301L, Channel.EMAIL, Purpose.MARKETING))
        .isEqualTo(PreferenceState.OPTED_OUT);
  }

  private PreferenceCommand command(
      PreferenceState state, Instant occurredAt, String idempotencyKey) {
    return new PreferenceCommand(
        101L,
        201L,
        301L,
        Channel.EMAIL,
        Purpose.MARKETING,
        state,
        state == PreferenceState.OPTED_OUT
            ? PreferenceSource.CONTACT_ACTION
            : PreferenceSource.USER_UI,
        idempotencyKey,
        state == PreferenceState.OPTED_OUT ? ActorType.CONTACT : ActorType.USER,
        "actor",
        "test",
        occurredAt,
        idempotencyKey);
  }

  private void apply(
      TestDatabase db, ContactPreferenceRepository repository, PreferenceCommand command) {
    db.tx().executeWithoutResult(ignored -> repository.apply(command));
  }

  private void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private void seed(JdbcTemplate jdbc) {
    jdbc.update(
        """
        insert into users(id,email,name,is_verified,username,password,two_factor_enabled)
        values (1,'owner@example.com','Owner',true,'owner','x',false)
        """);
    jdbc.update(
        """
        insert into companies
          (id,name,slug,status,created_by_user_id,created_at,updated_at)
        values (101,'A','a','ACTIVE',1,now(),now())
        """);
    jdbc.update(
        """
        insert into contacts
          (id,company_id,display_name,status,created_by_user_id,created_at,updated_at,version)
        values (201,101,'Recipient','ACTIVE',1,now(),now(),0)
        """);
    jdbc.update(
        """
        insert into contact_identities
          (id,company_id,contact_id,identity_type,value,normalized_value,is_primary,status,
           created_at,updated_at)
        values
          (301,101,201,'EMAIL','recipient@example.test','recipient@example.test',true,'ACTIVE',
           now(),now())
        """);
  }

  private TestDatabase migratedDatabase() {
    String schema = "qa_contact_pref_" + UUID.randomUUID().toString().replace("-", "");
    Flyway flyway =
        Flyway.configure()
            .dataSource(URL, USER, PASSWORD)
            .schemas(schema)
            .defaultSchema(schema)
            .createSchemas(true)
            .cleanDisabled(false)
            .target(MigrationVersion.fromVersion("85"))
            .load();
    flyway.clean();
    flyway.migrate();

    DriverManagerDataSource root = new DriverManagerDataSource(URL, USER, PASSWORD);
    DataSource scoped = schemaScoped(root, schema);
    return new TestDatabase(
        new JdbcTemplate(scoped),
        new TransactionTemplate(new DataSourceTransactionManager(scoped)));
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
      JdbcTemplate jdbc, TransactionTemplate tx) {}
}
