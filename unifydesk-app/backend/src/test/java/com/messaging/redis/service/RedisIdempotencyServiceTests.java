package com.messaging.redis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisIdempotencyServiceTests {

  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @InjectMocks private RedisIdempotencyService service;

  @Test
  void firstAcquireSucceedsAndDuplicateFails() {
    Duration ttl = Duration.ofHours(24);
    String key = "messaging:idempotency:webhook:req-1";

    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(key, "1", ttl)).thenReturn(true, false);

    assertThat(service.tryAcquire("webhook", "req-1", ttl)).isTrue();
    assertThat(service.tryAcquire("webhook", "req-1", ttl)).isFalse();
  }
}
