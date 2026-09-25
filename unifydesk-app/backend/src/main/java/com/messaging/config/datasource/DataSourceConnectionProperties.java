package com.messaging.config.datasource;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DataSourceConnectionProperties {

  private String url;
  private String username;
  private String password;
  private int maxPoolSize = 25;
  private int minIdle = 1;
}
