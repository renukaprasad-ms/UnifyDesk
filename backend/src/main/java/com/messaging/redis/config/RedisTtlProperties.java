package com.messaging.redis.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.redis.ttl")
public class RedisTtlProperties {

  private Duration otp = Duration.ofMinutes(5);
  private Duration otpAttempts = Duration.ofMinutes(10);
  private Duration loginAttempts = Duration.ofMinutes(15);
  private Duration idempotency = Duration.ofHours(24);
  private Duration shortCache = Duration.ofMinutes(5);
  private Duration configurationCache = Duration.ofMinutes(30);
  private Duration broadcastProgress = Duration.ofDays(7);
}
