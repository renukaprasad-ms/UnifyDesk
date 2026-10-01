package com.messaging.config.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class DatabaseMigrationConfigTests {

  @Test
  void baselineOnMigrateDefaultsToFalse() {
    MockEnvironment environment = new MockEnvironment();

    assertThat(DatabaseMigrationConfig.baselineOnMigrate(environment)).isFalse();
  }

  @Test
  void baselineOnMigrateSupportsExplicitTrue() {
    MockEnvironment environment =
        new MockEnvironment().withProperty("spring.flyway.baseline-on-migrate", "true");

    assertThat(DatabaseMigrationConfig.baselineOnMigrate(environment)).isTrue();
  }

  @Test
  void applicationYamlAlsoDefaultsFlywayBaseliningToFalse() throws Exception {
    ClassPathResource resource = new ClassPathResource("application.yaml");
    String yaml = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertThat(yaml)
        .contains("baseline-on-migrate: ${FLYWAY_BASELINE_ON_MIGRATE:false}")
        .doesNotContain("baseline-on-migrate: ${FLYWAY_BASELINE_ON_MIGRATE:true}");
  }
}
