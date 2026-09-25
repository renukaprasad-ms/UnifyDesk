package com.messaging.lifecycle.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.lifecycle.deletion")
public class DeletionLifecycleProperties {

  private Duration retention = Duration.ofDays(30);
  private Duration retryDelay = Duration.ofHours(1);
  private Duration staleAfter = Duration.ofHours(1);
  private int batchSize = 20;
  private int rowBatchSize = 5000;
  private int maxRowBatchesPerRun = 4;
  private int mediaBatchSize = 250;
}
