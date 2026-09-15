package com.messaging.redis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitServiceTests {

  @Mock private StringRedisTemplate stringRedisTemplate;
  @InjectMocks private RedisRateLimitService service;

  @Test
  void allowsBelowLimit() {
    when(stringRedisTemplate.execute(
            any(RedisScript.class), eq(List.of("messaging:rate-limit:login:127.0.0.1")), eq("60000")))
        .thenReturn("2:60000");

    RateLimitResult result =
        service.consume("messaging:rate-limit:login:127.0.0.1", 3, Duration.ofMinutes(1));

    assertThat(result.allowed()).isTrue();
    assertThat(result.current()).isEqualTo(2);
    assertThat(result.remaining()).isEqualTo(1);
    assertThat(result.retryAfter()).isEqualTo(Duration.ZERO);
  }

  @Test
  void blocksAboveLimitWithRetryAfter() {
    when(stringRedisTemplate.execute(
            any(RedisScript.class), eq(List.of("messaging:rate-limit:login:127.0.0.1")), eq("60000")))
        .thenReturn("4:42000");

    RateLimitResult result =
        service.consume("messaging:rate-limit:login:127.0.0.1", 3, Duration.ofMinutes(1));

    assertThat(result.allowed()).isFalse();
    assertThat(result.current()).isEqualTo(4);
    assertThat(result.remaining()).isZero();
    assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(42));
  }
}
