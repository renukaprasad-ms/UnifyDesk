package com.messaging.broadcast.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.broadcast")
public class BroadcastProperties {
  private int memberWriteBatchSize = 500;
  private int snapshotBatchSize = 100;
  private int recipientClaimSize = 10;
  private int workerConcurrency = 8;
  private int workerPollIntervalMs = 250;
  private int maxDeliveryAttempts = 5;
  private Duration retryInitialBackoff = Duration.ofSeconds(2);
  private Duration retryMaxBackoff = Duration.ofMinutes(5);
  private Duration staleClaimAfter = Duration.ofMinutes(5);
  private Duration scheduleClaimStaleAfter = Duration.ofMinutes(2);
  private int outboxBatchSize = 50;
  private int outboxPollIntervalMs = 250;
  private Duration outboxStaleAfter = Duration.ofMinutes(2);
  private int outboxMaxAttempts = 20;
  private String messageIdDomain = "messaging.renukaprasad.in";
  private int gmailPerSecond = 5;
  private int microsoftPerSecond = 5;
  private int imapSmtpPerSecond = 5;
}
