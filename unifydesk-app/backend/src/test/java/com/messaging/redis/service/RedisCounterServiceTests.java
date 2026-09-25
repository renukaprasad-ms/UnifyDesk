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
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisCounterServiceTests {

  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @InjectMocks private RedisCounterService service;

  @Test
  void incrementsAtomicallyWithRedisCommand() {
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment("messaging:counter")).thenReturn(1L);

    assertThat(service.increment("messaging:counter")).isEqualTo(1L);
  }

  @Test
  void incrementWithTtlUsesScript() {
    when(stringRedisTemplate.execute(
            any(RedisScript.class), eq(List.of("messaging:counter")), eq("60000")))
        .thenReturn(1L);

    assertThat(service.incrementWithTtl("messaging:counter", Duration.ofMinutes(1))).isEqualTo(1L);
  }
}
