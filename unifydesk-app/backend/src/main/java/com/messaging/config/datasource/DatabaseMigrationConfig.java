package com.messaging.config.datasource;

import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DatabaseMigrationConfig {

  private static final String DATABASE_MIGRATION_BEAN = "databaseMigration";
  private static final String ENTITY_MANAGER_FACTORY_BEAN = "entityManagerFactory";

  @Bean(name = DATABASE_MIGRATION_BEAN)
  public InitializingBean databaseMigration(
      ReadWriteDataSourceProperties properties, Environment environment) {
    DataSourceConnectionProperties write = properties.getWrite();
    return () ->
        Flyway.configure()
            .dataSource(write.getUrl(), write.getUsername(), write.getPassword())
            .locations(environment.getProperty("spring.flyway.locations", "classpath:db/migration"))
            .baselineOnMigrate(baselineOnMigrate(environment))
            .baselineVersion(environment.getProperty("spring.flyway.baseline-version", "0"))
            .load()
            .migrate();
  }

  static boolean baselineOnMigrate(Environment environment) {
    return environment.getProperty("spring.flyway.baseline-on-migrate", Boolean.class, false);
  }

  @Bean
  public static BeanFactoryPostProcessor entityManagerFactoryDependsOnMigration() {
    return beanFactory -> {
      if (!beanFactory.containsBeanDefinition(ENTITY_MANAGER_FACTORY_BEAN)) {
        return;
      }
      BeanDefinition beanDefinition = beanFactory.getBeanDefinition(ENTITY_MANAGER_FACTORY_BEAN);
      String[] dependsOn = beanDefinition.getDependsOn();
      if (dependsOn == null) {
        beanDefinition.setDependsOn(DATABASE_MIGRATION_BEAN);
        return;
      }
      if (Arrays.asList(dependsOn).contains(DATABASE_MIGRATION_BEAN)) {
        return;
      }
      String[] updatedDependsOn = Arrays.copyOf(dependsOn, dependsOn.length + 1);
      updatedDependsOn[dependsOn.length] = DATABASE_MIGRATION_BEAN;
      beanDefinition.setDependsOn(updatedDependsOn);
    };
  }
}
