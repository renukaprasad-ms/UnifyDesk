package com.messaging.broadcast.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.dto.BroadcastExpectedSourceDescriptor;
import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.broadcast.enums.BroadcastAudienceSourceType;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.exception.ConflictException;
import com.messaging.contacts.audience.*;
import com.messaging.contacts.core.domain.ContactTypes.MembershipSource;
import com.messaging.contacts.core.repository.ContactListRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

class BroadcastAudiencePreviewBindingPostgresTests {

  private static final String URL = "jdbc:postgresql://127.0.0.1:55432/messaging_test";
  private static final String USER = "test";
  private static final String PASSWORD = "test";

  private final JdbcTemplate admin =
      new JdbcTemplate(new DriverManagerDataSource(URL, USER, PASSWORD));

  @AfterEach
  void cleanup() {
    List<String> schemas =
        admin.queryForList(
            "select schema_name from information_schema.schemata where schema_name like 'qa_broadcast_preview_%'",
            String.class);
    for (String schema : schemas) {
      admin.execute("drop schema if exists " + schema + " cascade");
    }
  }

  @Test
  void listMembershipMutationAfterPreviewProducesDeterministicConflict() {
    TestDatabase db = migratedDatabase();
    seedBase(db.jdbc());
    db.jdbc()
        .update(
            """
            insert into contact_lists
              (id,company_id,name,normalized_name,status,member_count,created_by_user_id,
               created_at,updated_at,version)
            values (301,101,'Customers','customers','ACTIVE',1,1,now(),now(),0)
            """);
    db.jdbc()
        .update(
            """
            insert into contact_list_memberships
              (company_id,list_id,contact_id,source,added_by_user_id,added_at)
            values (101,301,201,'MANUAL',1,now())
            """);

    AudienceSourceResolver resolver = resolver(db);
    String previewVersion =
        resolver.describeForBroadcast(101L, AudienceSourceResolver.SourceType.LIST, 301L)
            .source()
            .sourceVersion();

    ContactListRepository lists =
        new ContactListRepository(db.jdbc(), new NamedParameterJdbcTemplate(db.dataSource()));
    db.tx()
        .executeWithoutResult(
            ignored -> {
              lists.lockActive(101L, 301L);
              lists.addMembers(
                  101L, 301L, List.of(202L), MembershipSource.MANUAL, null, 1L);
            });

    BroadcastAudienceFreezeService freeze =
        new BroadcastAudienceFreezeService(mock(AudienceSnapshotService.class), resolver);

    assertThatThrownBy(
            () ->
                freeze.freeze(
                    message(BroadcastAudienceSourceType.LIST, 301L),
                    new BroadcastExpectedSourceDescriptor(
                        BroadcastAudienceSourceType.LIST,
                        "301",
                        previewVersion,
                        null,
                        null,
                        null,
                        Instant.now())))
        .isInstanceOf(ConflictException.class)
        .hasMessage(BroadcastAudienceFreezeService.STALE_PREVIEW);
  }

  @Test
  void segmentMutationAfterPreviewProducesDeterministicConflict() {
    TestDatabase db = migratedDatabase();
    seedBase(db.jdbc());
    db.jdbc()
        .update(
            """
            insert into contact_segments
              (id,company_id,name,normalized_name,filter_version,filter_json,filter_hash,status,
               created_by_user_id,created_at,updated_at,version)
            values (401,101,'US','us',1,'{}'::jsonb,'old-hash','ACTIVE',1,now(),now(),3)
            """);

    AudienceSourceResolver resolver = resolver(db);
    AudienceSourceResolver.BroadcastSourceState preview =
        resolver.describeForBroadcast(101L, AudienceSourceResolver.SourceType.SEGMENT, 401L);

    SegmentRepository segments =
        new SegmentRepository(db.jdbc(), new NamedParameterJdbcTemplate(db.dataSource()));
    segments.update(101L, 401L, 3L, "US", null, 2, "{}", "new-hash");

    BroadcastAudienceFreezeService freeze =
        new BroadcastAudienceFreezeService(mock(AudienceSnapshotService.class), resolver);

    assertThatThrownBy(
            () ->
                freeze.freeze(
                    message(BroadcastAudienceSourceType.SEGMENT, 401L),
                    new BroadcastExpectedSourceDescriptor(
                        BroadcastAudienceSourceType.SEGMENT,
                        "401",
                        preview.source().sourceVersion(),
                        preview.filterVersion(),
                        preview.source().filterHash(),
                        null,
                        Instant.now())))
        .isInstanceOf(ConflictException.class)
        .hasMessage(BroadcastAudienceFreezeService.STALE_PREVIEW);
  }

  private AudienceSourceResolver resolver(TestDatabase db) {
    ObjectMapper mapper = new ObjectMapper();
    NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(db.dataSource());
    SegmentRepository segments = new SegmentRepository(db.jdbc(), named);
    SegmentCompiler compiler =
        new SegmentCompiler(
            mapper,
            new ContactAudienceProperties(),
            new SegmentFieldMetadataRepository(db.jdbc()));
    return new AudienceSourceResolver(db.jdbc(), named, segments, compiler, mapper);
  }

  private BroadcastMessage message(BroadcastAudienceSourceType type, long sourceId) {
    BroadcastMessage message = new BroadcastMessage();
    message.setCompanyId(101L);
    message.setAudienceSourceType(type);
    message.setAudienceSourceId(sourceId);
    message.setChannelType(ChannelType.EMAIL);
    return message;
  }

  private void seedBase(JdbcTemplate jdbc) {
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
        values
          (201,101,'A1','ACTIVE',1,now(),now(),0),
          (202,101,'A2','ACTIVE',1,now(),now(),0)
        """);
  }

  private TestDatabase migratedDatabase() {
    String schema = "qa_broadcast_preview_" + UUID.randomUUID().toString().replace("-", "");
    Flyway flyway =
        Flyway.configure()
            .dataSource(URL, USER, PASSWORD)
            .schemas(schema)
            .defaultSchema(schema)
            .createSchemas(true)
            .cleanDisabled(false)
            .target(MigrationVersion.fromVersion("84"))
            .load();
    flyway.clean();
    flyway.migrate();

    DriverManagerDataSource root = new DriverManagerDataSource(URL, USER, PASSWORD);
    DataSource scoped = schemaScoped(root, schema);
    return new TestDatabase(
        new JdbcTemplate(scoped),
        scoped,
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
      JdbcTemplate jdbc, DataSource dataSource, TransactionTemplate tx) {}
}
