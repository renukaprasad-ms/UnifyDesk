package com.messaging.delivery.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.delivery.email")
public class EmailDeliveryProperties {
  private int claimSize = 100;
  private int pollIntervalMs = 5000;
  private int maxAttempts = 12;
  private Duration retryInitialBackoff = Duration.ofSeconds(30);
  private Duration retryMaxBackoff = Duration.ofMinutes(10);
  private Duration staleAfter = Duration.ofMinutes(5);
  private Duration matchLookback = Duration.ofDays(14);
}
