package com.messaging.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

@Configuration
@EnableConfigurationProperties(ReadWriteDataSourceProperties.class)
public class DataSourceConfig {

  @Bean
  @Primary
  public DataSource dataSource(ReadWriteDataSourceProperties properties) {
    HikariDataSource writeDataSource = hikariDataSource("write", properties.getWrite());
    HikariDataSource readDataSource = hikariDataSource("read", properties.getRead());
    ReadWriteRoutingDataSource routingDataSource = new ReadWriteRoutingDataSource();
    routingDataSource.setTargetDataSources(
        Map.of(DataSourceType.WRITE, writeDataSource, DataSourceType.READ, readDataSource));
    routingDataSource.setDefaultTargetDataSource(writeDataSource);
    routingDataSource.afterPropertiesSet();
    return new LazyConnectionDataSourceProxy(routingDataSource);
  }

  private HikariDataSource hikariDataSource(
      String poolName, DataSourceConnectionProperties properties) {
    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setPoolName("messaging-" + poolName);
    dataSource.setJdbcUrl(properties.getUrl());
    dataSource.setUsername(properties.getUsername());
    dataSource.setPassword(properties.getPassword());
    dataSource.setMaximumPoolSize(properties.getMaxPoolSize());
    dataSource.setMinimumIdle(properties.getMinIdle());
    return dataSource;
  }
}
