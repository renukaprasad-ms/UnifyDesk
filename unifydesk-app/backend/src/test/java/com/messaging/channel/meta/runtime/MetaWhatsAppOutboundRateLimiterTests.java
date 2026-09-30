package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.common.exception.TooManyRequestsException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class MetaWhatsAppOutboundRateLimiterTests {

  private StringRedisTemplate redisTemplate;
  private MetaWhatsAppRuntimeProperties properties;
  private MetaWhatsAppOutboundRateLimiter limiter;

  @BeforeEach
  void setUp() {
    redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
    properties = new MetaWhatsAppRuntimeProperties();
    limiter = new MetaWhatsAppOutboundRateLimiter(redisTemplate, properties);
  }

  @Test
  void rejectsWhenSharedPhoneWindowIsExhausted() {
    when(redisTemplate.execute(
            any(RedisScript.class),
            eq(
                List.of(
                    "unifydesk:whatsapp:rate:phone:phone-1",
                    "unifydesk:whatsapp:rate:phone:phone-1:cooldown")),
            eq("1000"),
            eq("10")))
        .thenReturn("0:800:rate");

    assertThatThrownBy(() -> limiter.requirePermit("phone-1"))
        .isInstanceOf(TooManyRequestsException.class)
        .hasMessage("WHATSAPP_RATE_LIMITED");
  }

  @Test
  void redisFailureFailsClosed() {
    when(redisTemplate.execute(
            any(RedisScript.class),
            eq(
                List.of(
                    "unifydesk:whatsapp:rate:phone:phone-1",
                    "unifydesk:whatsapp:rate:phone:phone-1:cooldown")),
            eq("1000"),
            eq("10")))
        .thenThrow(new IllegalStateException("redis unavailable"));

    assertThatThrownBy(() -> limiter.requirePermit("phone-1"))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessage("WHATSAPP_RATE_LIMIT_UNAVAILABLE");
  }

  @Test
  void missingCooldownResultFailsClosed() {
    when(redisTemplate.execute(
            any(RedisScript.class),
            eq(List.of("unifydesk:whatsapp:rate:phone:phone-1:cooldown")),
            eq("5000")))
        .thenReturn(null);

    assertThatThrownBy(() -> limiter.imposeProviderCooldown("phone-1", Duration.ofSeconds(5)))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessage("WHATSAPP_RATE_LIMIT_UNAVAILABLE");
  }

  @Test
  void providerCooldownUsesConfiguredFallback() {
    properties.setProviderRetryAfterFallback(Duration.ofSeconds(7));
    when(redisTemplate.execute(
            any(RedisScript.class),
            eq(List.of("unifydesk:whatsapp:rate:phone:phone-1:cooldown")),
            eq("7000")))
        .thenReturn(7000L);

    limiter.imposeProviderCooldown("phone-1", null);
  }
}
