package com.messaging.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.user.enums.UserAppearanceMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class UserAppearanceMigrationPostgresTests {
  private static final String URL = "jdbc:postgresql://127.0.0.1:55432/messaging_test";
  private String schema;

  private Flyway migrations(String version) {
    return Flyway.configure()
        .dataSource(URL, "test", "test")
        .schemas(schema)
        .defaultSchema(schema)
        .createSchemas(true)
        .target(version)
        .load();
  }

  @AfterEach
  void cleanup() {
    if (schema != null)
      new JdbcTemplate(new DriverManagerDataSource(URL, "test", "test"))
          .execute("drop schema if exists " + schema + " cascade");
  }

  @Test
  void deviceDefaultPersistsWithoutResettingExplicitLightAndDarkChoices() {
    schema = "qa_appearance_" + UUID.randomUUID().toString().replace("-", "");
    migrations("114").migrate();
    var jdbc =
        new JdbcTemplate(
            new DelegatingDataSource(new DriverManagerDataSource(URL, "test", "test")) {
              @Override
              public Connection getConnection() throws SQLException {
                var connection = super.getConnection();
                try (var statement = connection.createStatement()) {
                  statement.execute("set search_path to " + schema);
                }
                return connection;
              }
            });
    jdbc.update(
        "insert into users(id,email,name,is_verified,username,password,two_factor_enabled) values(1,'one@example.com','One',true,'one','x',false),(2,'two@example.com','Two',true,'two','x',false),(3,'three@example.com','Three',true,'three','x',false)");
    jdbc.update(
        "insert into user_preferences(user_id,appearance_mode,created_at,updated_at) values(1,'DARK',now(),now()),(2,'LIGHT',now(),now())");
    migrations("115").migrate();
    assertThat(
            jdbc.queryForObject(
                "select appearance_mode from user_preferences where user_id=1", String.class))
        .isEqualTo("DARK");
    assertThat(
            jdbc.queryForObject(
                "select appearance_mode from user_preferences where user_id=2", String.class))
        .isEqualTo("LIGHT");
    jdbc.update(
        "insert into user_preferences(user_id,created_at,updated_at) values(3,now(),now())");
    assertThat(
            UserAppearanceMode.valueOf(
                jdbc.queryForObject(
                    "select appearance_mode from user_preferences where user_id=3", String.class)))
        .isEqualTo(UserAppearanceMode.SYSTEM);
    jdbc.update("update user_preferences set appearance_mode='SYSTEM' where user_id=1");
    assertThat(
            jdbc.queryForObject(
                "select appearance_mode from user_preferences where user_id=1", String.class))
        .isEqualTo("SYSTEM");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update user_preferences set appearance_mode='INVALID' where user_id=3"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
