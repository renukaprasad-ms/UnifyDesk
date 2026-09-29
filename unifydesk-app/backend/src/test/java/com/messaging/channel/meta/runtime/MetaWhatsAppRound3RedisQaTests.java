package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.common.exception.TooManyRequestsException;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class MetaWhatsAppRound3RedisQaTests {

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
  void differentPhonesHaveIndependentRateBudgets() {
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    MetaWhatsAppRuntimeProperties properties = properties(2, Duration.ofSeconds(5));
    MetaWhatsAppOutboundRateLimiter limiter =
        new MetaWhatsAppOutboundRateLimiter(redis, properties);

    limiter.requirePermit("phone-a");
    limiter.requirePermit("phone-a");
    limiter.requirePermit("phone-b");
    limiter.requirePermit("phone-b");

    assertThatThrownBy(() -> limiter.requirePermit("phone-a"))
        .isInstanceOf(TooManyRequestsException.class)
        .hasMessage("WHATSAPP_RATE_LIMITED");
    assertThatThrownBy(() -> limiter.requirePermit("phone-b"))
        .isInstanceOf(TooManyRequestsException.class)
        .hasMessage("WHATSAPP_RATE_LIMITED");
  }

  @Test
  void cooldownIsPhoneScopedAndSharedWithoutBlockingAnotherPhone() {
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    MetaWhatsAppRuntimeProperties properties = properties(10, Duration.ofSeconds(1));
    MetaWhatsAppOutboundRateLimiter first =
        new MetaWhatsAppOutboundRateLimiter(redis, properties);
    MetaWhatsAppOutboundRateLimiter second =
        new MetaWhatsAppOutboundRateLimiter(redis, properties);

    first.imposeProviderCooldown("phone-a", Duration.ofSeconds(2));

    assertThatThrownBy(() -> second.requirePermit("phone-a"))
        .isInstanceOf(TooManyRequestsException.class)
        .hasMessage("WHATSAPP_RATE_LIMITED");

    second.requirePermit("phone-b");
  }

  @Test
  void realRedisConnectionFailureFailsClosed() {
    RedisStandaloneConfiguration redisConfiguration =
        new RedisStandaloneConfiguration("127.0.0.1", 56380);
    LettuceClientConfiguration clientConfiguration =
        LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(500))
            .shutdownTimeout(Duration.ofMillis(100))
            .build();
    LettuceConnectionFactory unavailableFactory =
        new LettuceConnectionFactory(redisConfiguration, clientConfiguration);
    unavailableFactory.afterPropertiesSet();
    StringRedisTemplate unavailableRedis = new StringRedisTemplate(unavailableFactory);
    unavailableRedis.afterPropertiesSet();

    try {
      MetaWhatsAppOutboundRateLimiter limiter =
          new MetaWhatsAppOutboundRateLimiter(
              unavailableRedis, properties(10, Duration.ofSeconds(1)));

      assertThatThrownBy(() -> limiter.requirePermit("phone-outage"))
          .isInstanceOf(ServiceUnavailableException.class)
          .hasMessage("WHATSAPP_RATE_LIMIT_UNAVAILABLE");
    } finally {
      unavailableFactory.destroy();
    }
  }

  private MetaWhatsAppRuntimeProperties properties(int maxPerSecond, Duration window) {
    MetaWhatsAppRuntimeProperties properties = new MetaWhatsAppRuntimeProperties();
    properties.setOutboundMaxPerSecond(maxPerSecond);
    properties.setOutboundRateWindow(window);
    properties.setProviderRetryAfterFallback(Duration.ofSeconds(5));
    return properties;
  }
}
