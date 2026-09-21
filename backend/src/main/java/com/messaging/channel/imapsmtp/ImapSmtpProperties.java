package com.messaging.channel.imapsmtp;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.channel.imap-smtp")
public class ImapSmtpProperties {

  private boolean enabled = true;
  private Duration connectTimeout = Duration.ofSeconds(10);
  private Duration readTimeout = Duration.ofSeconds(20);
  private Duration writeTimeout = Duration.ofSeconds(20);
  private Duration incrementalPollInterval = Duration.ofMinutes(1);
  private Duration incrementalInitialDelay = Duration.ofMinutes(1);
  private int incrementalMaxMessagesPerFolder = 250;
  private int incrementalMaxAttempts = 12;
  private int syncMaxMessages = 10000;
  private int syncMaxRangeDays = 366;
  private int workerConcurrency = 4;
  private int workerPollIntervalMs = 500;
  private Duration staleJobAfter = Duration.ofMinutes(10);
}
