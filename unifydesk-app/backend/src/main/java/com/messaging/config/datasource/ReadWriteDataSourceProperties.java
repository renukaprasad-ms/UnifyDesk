package com.messaging.config.datasource;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.datasource")
public class ReadWriteDataSourceProperties {

  private DataSourceConnectionProperties write = new DataSourceConnectionProperties();
  private DataSourceConnectionProperties read = new DataSourceConnectionProperties();
}
