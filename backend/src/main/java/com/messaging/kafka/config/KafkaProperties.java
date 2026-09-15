package com.messaging.kafka.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProperties {

  private Security security = new Security();
  private Retry retry = new Retry();
  private Idempotency idempotency = new Idempotency();
  private Topics topics = new Topics();

  @Getter
  @Setter
  public static class Security {
    private String protocol = "PLAINTEXT";
    private String saslMechanism;
    private String saslJaasConfig;
  }

  @Getter
  @Setter
  public static class Retry {
    private int maxAttempts = 4;
    private Duration initialDelay = Duration.ofSeconds(5);
    private double multiplier = 2;
    private Duration maxDelay = Duration.ofMinutes(2);
  }

  @Getter
  @Setter
  public static class Idempotency {
    private Duration ttl = Duration.ofDays(7);
  }

  @Getter
  @Setter
  public static class Topics {
    private short replicationFactor = 1;
    private short minInsyncReplicas = 1;
    private Duration dltRetention = Duration.ofDays(14);
    private PriorityTopic critical = new PriorityTopic();
    private PriorityTopic high = new PriorityTopic();
    private PriorityTopic medium = new PriorityTopic();
    private PriorityTopic low = new PriorityTopic();
  }

  @Getter
  @Setter
  public static class PriorityTopic {
    private String name;
    private String retryName;
    private String dltName;
    private int partitions = 1;
    private int concurrency = 1;
    private int maxPollRecords = 50;
    private Duration retention = Duration.ofDays(5);
    private String consumerGroup;
  }
}
