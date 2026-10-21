package com.messaging.notification.platform.core.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.notification.platform.core.entity.NotificationEvent;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

class NotificationEventJpaMappingTests {
  @Test
  void hibernateMappingAndSpringDataQueriesInitializeWithoutDatabaseAccess() {
    Configuration configuration = new Configuration().addAnnotatedClass(NotificationEvent.class);
    configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
    configuration.setProperty("hibernate.boot.allow_jdbc_metadata_access", "false");
    configuration.setProperty("hibernate.hbm2ddl.auto", "none");
    try (var factory = configuration.buildSessionFactory();
        var entityManager = factory.createEntityManager()) {
      var repository =
          new JpaRepositoryFactory(entityManager)
              .getRepository(NotificationEventJpaRepository.class);
      assertThat(repository).isNotNull();
      assertThat(factory.getMetamodel().entity(NotificationEvent.class).getName())
          .isEqualTo("NotificationEvent");
    }
  }
}
