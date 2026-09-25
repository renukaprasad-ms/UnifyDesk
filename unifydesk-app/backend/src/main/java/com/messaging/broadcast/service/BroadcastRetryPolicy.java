package com.messaging.broadcast.service;

import com.messaging.broadcast.config.BroadcastProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BroadcastRetryPolicy {

  private final BroadcastProperties properties;

  public boolean canRetry(int attempt) {
    return attempt < Math.max(1, properties.getMaxDeliveryAttempts());
  }

  public Instant nextAttempt(int attempt) {
    long initial = Math.max(1000L, properties.getRetryInitialBackoff().toMillis());
    long max = Math.max(initial, properties.getRetryMaxBackoff().toMillis());
    int exponent = Math.min(Math.max(0, attempt - 1), 16);
    long exponential;
    try {
      exponential = Math.multiplyExact(initial, 1L << exponent);
    } catch (ArithmeticException exception) {
      exponential = max;
    }
    long jitter = ThreadLocalRandom.current().nextLong(0, 1001);
    return Instant.now().plus(Duration.ofMillis(Math.min(max, exponential + jitter)));
  }
}
