package com.messaging.redis.service;

import com.messaging.redis.exception.RedisOperationException;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisRateLimitService {

  private static final DefaultRedisScript<String> CONSUME_SCRIPT =
      new DefaultRedisScript<>(
          "local current = redis.call('INCR', KEYS[1]) "
              + "if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
              + "local ttl = redis.call('PTTL', KEYS[1]) "
              + "return tostring(current) .. ':' .. tostring(ttl)",
          String.class);

  private final StringRedisTemplate stringRedisTemplate;

  public RateLimitResult consume(String key, int limit, Duration window) {
    if (limit <= 0) {
      throw new IllegalArgumentException("Rate limit must be positive");
    }
    if (window == null || window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("Rate limit window must be positive");
    }

    try {
      String result =
          stringRedisTemplate.execute(
              CONSUME_SCRIPT, List.of(key), String.valueOf(window.toMillis()));
      String[] parts = result == null ? new String[] {"0", "0"} : result.split(":", 2);
      long current = Long.parseLong(parts[0]);
      long ttlMillis = Long.parseLong(parts[1]);
      boolean allowed = current <= limit;
      long remaining = Math.max(0, limit - current);
      Duration retryAfter = allowed ? Duration.ZERO : Duration.ofMillis(Math.max(0, ttlMillis));
      return new RateLimitResult(allowed, current, limit, remaining, retryAfter);
    } catch (RuntimeException exception) {
      throw new RedisOperationException("Redis rate limit consume failed", exception);
    }
  }
}
