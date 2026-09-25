package com.messaging.redis.service;

import com.messaging.redis.exception.RedisOperationException;
import com.messaging.redis.key.RedisKey;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisIdempotencyService {

  private final StringRedisTemplate stringRedisTemplate;

  public boolean tryAcquire(String scope, String requestId, Duration ttl) {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("Idempotency TTL must be positive");
    }
    String key = RedisKey.idempotency(scope, requestId);
    try {
      return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, "1", ttl));
    } catch (RuntimeException exception) {
      throw new RedisOperationException("Redis idempotency acquire failed", exception);
    }
  }
}
