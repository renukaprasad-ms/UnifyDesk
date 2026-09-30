package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.common.exception.TooManyRequestsException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class MetaWhatsAppOutboundRateLimiterRedisIntegrationTests {

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redis;

  @BeforeAll
  static void connect() {
    connectionFactory = new LettuceConnectionFactory("127.0.0.1", 56379);
    connectionFactory.afterPropertiesSet();
    redis = new StringRedisTemplate(connectionFactory);
    redis.afterPropertiesSet();
  }

  @AfterAll
  static void close() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @Test
  void twoInstancesShareOnePhoneRateBudget() throws Exception {
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    MetaWhatsAppRuntimeProperties properties = properties(10, Duration.ofSeconds(5));
    MetaWhatsAppOutboundRateLimiter first =
        new MetaWhatsAppOutboundRateLimiter(redis, properties);
    MetaWhatsAppOutboundRateLimiter second =
        new MetaWhatsAppOutboundRateLimiter(redis, properties);

    int attempts = 20;
    AtomicInteger allowed = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(attempts);
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < attempts; i++) {
        MetaWhatsAppOutboundRateLimiter limiter = i % 2 == 0 ? first : second;
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  try {
                    limiter.requirePermit("phone-shared");
                    allowed.incrementAndGet();
                  } catch (TooManyRequestsException ignored) {
                    // Expected for attempts above the distributed phone budget.
                  }
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> future : futures) {
        future.get();
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(allowed.get()).isEqualTo(10);
  }

  @Test
  void providerCooldownIsVisibleAcrossInstances() {
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    MetaWhatsAppRuntimeProperties properties = properties(100, Duration.ofSeconds(1));
    MetaWhatsAppOutboundRateLimiter first =
        new MetaWhatsAppOutboundRateLimiter(redis, properties);
    MetaWhatsAppOutboundRateLimiter second =
        new MetaWhatsAppOutboundRateLimiter(redis, properties);

    first.imposeProviderCooldown("phone-cooldown", Duration.ofSeconds(2));

    assertThatThrownBy(() -> second.requirePermit("phone-cooldown"))
        .isInstanceOf(TooManyRequestsException.class)
        .hasMessage("WHATSAPP_RATE_LIMITED");
  }

  private MetaWhatsAppRuntimeProperties properties(int maxPerSecond, Duration window) {
    MetaWhatsAppRuntimeProperties properties = new MetaWhatsAppRuntimeProperties();
    properties.setOutboundMaxPerSecond(maxPerSecond);
    properties.setOutboundRateWindow(window);
    properties.setProviderRetryAfterFallback(Duration.ofSeconds(5));
    return properties;
  }
}
